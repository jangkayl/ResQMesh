package edu.cit.resqmesh.data.repository

import android.content.SharedPreferences
import edu.cit.resqmesh.core.model.ChatMessage
import edu.cit.resqmesh.core.model.ConnectedDevice
import edu.cit.resqmesh.core.network.BroadcastDispatchResult
import edu.cit.resqmesh.core.network.MeshNetworkGateway
import edu.cit.resqmesh.core.network.TransportDispatchResult
import edu.cit.resqmesh.core.network.ratelimit.OutgoingChatBudget
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

    private class Fixture(scope: CoroutineScope, budget: OutgoingChatBudget = OutgoingChatBudget()) {
        val store = Store()
        var result = BroadcastDispatchResult(mapOf("B" to TransportDispatchResult.REJECTED_QUEUE_FULL))
        var broadcasts = 0
        val payloads = mutableListOf<edu.cit.resqmesh.core.network.MeshPayload>()
        val network = Proxy.newProxyInstance(MeshNetworkGateway::class.java.classLoader, arrayOf(MeshNetworkGateway::class.java)) { _, method, args ->
            when (method.name) {
                "currentMeshTtl" -> 10
                "getMyNodeId" -> "A1"
                "broadcastPayload", "broadcastPriorityPayload" -> {
                    payloads += kotlinx.serialization.protobuf.ProtoBuf.decodeFromByteArray(edu.cit.resqmesh.core.network.MeshPayload.serializer(), args!![0] as ByteArray)
                    assertTrue("Persistence must precede dispatch", store.rows.isNotEmpty())
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
        }, chatBudget = budget)

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

    @Test fun retryWithoutTokenStaysPendingUntilRefillWhileFirstSendIsNotChargedTwice() = runTest {
        var now = 1_000_000L
        val budget = OutgoingChatBudget(clock = { now })
        repeat(20) { assertTrue(budget.tryConsume()) }
        val f = Fixture(backgroundScope, budget)
        val id = f.repo.sendPublicMessage("test", null, null)
        runCurrent()
        // A recovered row was never paid by the send gate, so its dispatch needs a token.
        val orphan = ChatMessage("orphan", "Me#A1", "queued", null, null, null, null, true, false,
            System.currentTimeMillis(), deliveredTo = listOf("PENDING"))
        f.store.rows[orphan.id] = orphan
        f.result = BroadcastDispatchResult(mapOf("B" to TransportDispatchResult.ACCEPTED))
        advanceTimeBy(5_000L); runCurrent()
        // The paid first send goes out without a token; the unpaid retry stays PENDING.
        assertEquals(emptyList<String>(), f.store.rows[id]!!.deliveredTo)
        assertEquals(listOf("PENDING"), f.store.rows["orphan"]!!.deliveredTo)
        assertEquals(2, f.broadcasts)
        assertEquals(0, budget.state.value.remaining)
        now += 3_000L
        advanceTimeBy(3_000L); runCurrent()
        assertEquals(emptyList<String>(), f.store.rows["orphan"]!!.deliveredTo)
        assertEquals(3, f.broadcasts)
    }

    @Test fun expiredPendingMessageReleasesItsPrepaidMark() = runTest {
        val f = Fixture(backgroundScope)
        val id = f.repo.sendPublicMessage("test", null, null)
        runCurrent()
        fun prepaid(): Set<*> {
            val field = MeshRepository::class.java.getDeclaredField("outbound").apply { isAccessible = true }
            val outbound = field.get(f.repo)
            val prepaid = outbound.javaClass.getDeclaredField("prepaid").apply { isAccessible = true }
            return prepaid.get(outbound) as Set<*>
        }
        assertTrue(id in prepaid())
        f.store.rows[id] = f.store.rows[id]!!.copy(timestamp = 0L)
        advanceTimeBy(5_000L); runCurrent()
        assertEquals(listOf("FAILED"), f.store.rows[id]!!.deliveredTo)
        assertFalse(id in prepaid())
    }
}
