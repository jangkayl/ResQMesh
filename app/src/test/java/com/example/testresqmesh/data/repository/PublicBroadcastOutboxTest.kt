package com.example.testresqmesh.data.repository

import android.content.SharedPreferences
import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.network.BroadcastDispatchResult
import com.example.testresqmesh.core.network.MeshNetworkGateway
import com.example.testresqmesh.core.network.TransportDispatchResult
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PublicBroadcastOutboxTest {
    private class Store : MessageStore {
        val rows = linkedMapOf<String, ChatMessage>()
        override val publicMessages = flowOf(emptyList<ChatMessage>())
        override val privateMessages = flowOf(emptyMap<String, List<ChatMessage>>())
        override suspend fun save(message: ChatMessage, targetName: String?) { rows[message.id] = message }
        override suspend fun markDelivered(messageId: String, readerName: String) {}
        override suspend fun markSeen(messageId: String, readerName: String) {}
        override suspend fun markFailed(messageId: String) { update(messageId, "FAILED") }
        override suspend fun expirePending(messageId: String) { update(messageId, "FAILED") }
        override suspend fun markPending(messageId: String) { update(messageId, "PENDING") }
        override suspend fun markSent(messageId: String) { rows[messageId]?.let { rows[messageId] = it.copy(deliveredTo = emptyList()) } }
        override suspend fun getPendingOutbox() = rows.values.filter { "PENDING" in it.deliveredTo }.map { it to null }
        override suspend fun deleteConversation(peerName: String) {}
        private fun update(id: String, marker: String) { rows[id]?.let { rows[id] = it.copy(deliveredTo = listOf(marker)) } }
    }

    private class Fixture(scope: CoroutineScope) {
        val store = Store()
        var result = BroadcastDispatchResult(mapOf("B" to TransportDispatchResult.REJECTED_QUEUE_FULL))
        var broadcasts = 0
        val payloads = mutableListOf<com.example.testresqmesh.core.network.MeshPayload>()
        val network = Proxy.newProxyInstance(MeshNetworkGateway::class.java.classLoader, arrayOf(MeshNetworkGateway::class.java)) { _, method, args ->
            when (method.name) {
                "currentMeshTtl" -> 10
                "getMyNodeId" -> "A1"
                "broadcastPayload", "broadcastPriorityPayload" -> {
                    payloads += kotlinx.serialization.protobuf.ProtoBuf.decodeFromByteArray(com.example.testresqmesh.core.network.MeshPayload.serializer(), args!![0] as ByteArray)
                    assertEquals("Persistence must precede dispatch", 1, store.rows.size)
                    broadcasts++
                    result
                }
                else -> null
            }
        } as MeshNetworkGateway
        private val prefs = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)) { _, _, _ -> null } as SharedPreferences
        val repo = MeshRepository(network, store, BlockRelationshipStore(prefs), PeerPublicKeyDirectory(prefs), scope, MeshReadyPeerEvents(), object : PeerNameStore {
            override val names = flowOf(emptyMap<String, String>())
            override suspend fun observeFullName(fullName: String) {}
        })

        init {
            // Isolate repository send/retry behavior from Android callback/radio setup.
            val field = MeshRepository::class.java.getDeclaredField("_connectedDevices").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val peers = field.get(repo) as MutableStateFlow<List<ConnectedDevice>>
            peers.value = listOf(ConnectedDevice("B", "Bob#B1", isPayloadReady = true, nodeId = "B1"))
        }
    }

    @Test fun queuedRadioDestinationAndIdentitySurviveTuningChange() = runTest {
        val f = Fixture(backgroundScope)
        val id = f.repo.sendPublicMessage("Radio reply", null, null, conversationKind = "RADIO", channelId = "1")
        runCurrent()
        f.repo.setChannel("2")
        f.result = BroadcastDispatchResult(mapOf("B" to TransportDispatchResult.ACCEPTED))
        advanceTimeBy(5000); runCurrent()
        assertEquals(2, f.payloads.size)
        assertTrue(f.payloads.all { it.channelId == "1" && it.conversationKind == "RADIO" && it.senderNodeId == "A1" })
        assertEquals("1", f.store.rows[id]!!.channelId)
    }

    @Test fun fullRejectionPersistsPendingThenRetriesOnlyUntilAccepted() = runTest {
        val f = Fixture(backgroundScope)
        val id = f.repo.sendPublicMessage("test", null, null)
        runCurrent()
        assertEquals(listOf("PENDING"), f.store.rows[id]!!.deliveredTo)
        assertEquals(1, f.broadcasts)
        f.result = BroadcastDispatchResult(mapOf("B" to TransportDispatchResult.ACCEPTED))
        advanceTimeBy(4_999L); runCurrent()
        assertEquals(1, f.broadcasts)
        advanceTimeBy(1L); runCurrent()
        assertEquals(emptyList<String>(), f.store.rows[id]!!.deliveredTo)
        assertEquals(2, f.broadcasts)
        advanceTimeBy(4_000L); runCurrent()
        assertEquals(2, f.broadcasts)
    }

    @Test fun partialAcceptanceDoesNotRebroadcastToAlreadyAcceptedNeighbors() = runTest {
        val f = Fixture(backgroundScope)
        f.result = BroadcastDispatchResult(mapOf("B" to TransportDispatchResult.ACCEPTED, "C" to TransportDispatchResult.REJECTED_QUEUE_FULL))
        val id = f.repo.sendPublicMessage("test", null, null)
        runCurrent()
        assertEquals(emptyList<String>(), f.store.rows[id]!!.deliveredTo)
        advanceTimeBy(7_000L); runCurrent()
        assertEquals(1, f.broadcasts)
    }

    @Test fun invalidFrameStopsRetryAndIsMarkedFailed() = runTest {
        val f = Fixture(backgroundScope)
        f.result = BroadcastDispatchResult(mapOf("B" to TransportDispatchResult.REJECTED_INVALID_FRAME))
        val id = f.repo.sendPublicMessage("test", null, null)
        runCurrent()
        assertEquals(listOf("FAILED"), f.store.rows[id]!!.deliveredTo)
        advanceTimeBy(7_000L); runCurrent()
        assertEquals(1, f.broadcasts)
    }
}
