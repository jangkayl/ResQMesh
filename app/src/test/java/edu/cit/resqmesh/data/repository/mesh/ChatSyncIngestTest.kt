package edu.cit.resqmesh.data.repository.mesh

import android.content.SharedPreferences
import edu.cit.resqmesh.core.model.ChatMessage
import edu.cit.resqmesh.core.network.MeshNetworkGateway
import edu.cit.resqmesh.data.local.entity.ChatSyncIngestion
import edu.cit.resqmesh.data.local.entity.ChatSyncRow
import edu.cit.resqmesh.data.repository.MeshRouter
import edu.cit.resqmesh.data.repository.MessageStore
import edu.cit.resqmesh.data.repository.PeerPublicKeyDirectory
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSyncIngestTest {
    private class Store : MessageStore {
        val rows = linkedMapOf<String, ChatMessage>()
        override val publicMessages = flowOf(emptyList<ChatMessage>())
        override val privateMessages = flowOf(emptyMap<String, List<ChatMessage>>())
        override suspend fun save(message: ChatMessage, targetName: String?) { rows[message.id] = message }
        override suspend fun contains(id: String) = id in rows
        override suspend fun markDelivered(messageId: String, readerName: String) = Unit
        override suspend fun markSeen(messageId: String, readerName: String) = Unit
        override suspend fun markFailed(messageId: String) = Unit
        override suspend fun expirePending(messageId: String) = Unit
        override suspend fun markPending(messageId: String) = Unit
        override suspend fun markSent(messageId: String) = Unit
        override suspend fun getPendingOutbox() = emptyList<Pair<ChatMessage, String?>>()
        override suspend fun deleteConversation(peerName: String) = Unit
    }

    private class Fixture(scope: CoroutineScope, knownAlerts: Set<String> = emptySet()) {
        val store = Store()
        val networkCalls = mutableListOf<String>()
        val voice = MutableSharedFlow<ChatMessage>(replay = 1)
        private val network = Proxy.newProxyInstance(
            MeshNetworkGateway::class.java.classLoader, arrayOf(MeshNetworkGateway::class.java)
        ) { _, method, _ ->
            when {
                method.name == "getMyNodeId" -> "ME"
                method.name.startsWith("get") -> null
                else -> { networkCalls += method.name; null }
            }
        } as MeshNetworkGateway
        private val prefs = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)
        ) { _, _, _ -> null } as SharedPreferences
        val inbound = MeshInboundMessages(network, store, PeerPublicKeyDirectory(prefs), scope,
            SosReplyCapability({ false }, { it in knownAlerts }), MeshRouter(), MutableStateFlow("1"),
            MutableStateFlow(emptyList()), MutableStateFlow(null), voice, { "Me#ME" }, { emptyList() },
            { _, _ -> }, Mutex(), clock = { NOW })
    }

    private fun row(id: String = "m", kind: String = "COMMUNITY", sosId: String = "", sender: String = "A1",
        sentAt: Long = NOW - 60_000L) = ChatSyncRow(id, sentAt, kind, "Ann#A1", sender, "hello", sosId)

    @Test fun storedRowKeepsItsSenderTimeAndStaysSilent() = runTest {
        val f = Fixture(backgroundScope)
        assertEquals(ChatSyncIngestion.STORED, f.inbound.ingestSynced(row()))
        val stored = f.store.rows.getValue("m")
        assertEquals(NOW - 60_000L, stored.timestamp)
        assertEquals("COMMUNITY", stored.conversationKind)
        assertFalse(stored.isMine)
        assertTrue("Sync must not touch the network: ${f.networkCalls}", f.networkCalls.isEmpty())
        assertTrue(f.voice.replayCache.isEmpty())
    }

    @Test fun repliesToAnEndedButKnownSosAreKept() = runTest {
        val f = Fixture(backgroundScope, knownAlerts = setOf("s1"))
        assertEquals(ChatSyncIngestion.STORED, f.inbound.ingestSynced(row(kind = "SOS", sosId = "s1")))
    }

    @Test fun repliesToAnUnknownSosAreDeferredNotDropped() = runTest {
        val f = Fixture(backgroundScope)
        assertEquals(ChatSyncIngestion.DEFERRED, f.inbound.ingestSynced(row(kind = "SOS", sosId = "s1")))
        assertTrue(f.store.rows.isEmpty())
    }

    @Test fun radioOwnAndUndatedRowsAreRejected() = runTest {
        val f = Fixture(backgroundScope)
        assertEquals(ChatSyncIngestion.REJECTED, f.inbound.ingestSynced(row(kind = "RADIO")))
        assertEquals(ChatSyncIngestion.REJECTED, f.inbound.ingestSynced(row(sender = "me")))
        assertEquals(ChatSyncIngestion.REJECTED, f.inbound.ingestSynced(row(sentAt = 0L)))
        assertTrue(f.store.rows.isEmpty())
    }

    @Test fun aRowAlreadyHeldIsNotRewritten() = runTest {
        val f = Fixture(backgroundScope)
        f.inbound.ingestSynced(row())
        val first = f.store.rows.getValue("m")
        assertEquals(ChatSyncIngestion.STORED, f.inbound.ingestSynced(row(sentAt = NOW - 5L)))
        assertEquals(first.timestamp, f.store.rows.getValue("m").timestamp)
    }

    private companion object {
        const val NOW = 1_000_000_000L
    }
}
