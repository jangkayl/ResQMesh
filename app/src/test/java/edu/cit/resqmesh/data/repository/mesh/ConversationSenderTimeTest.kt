package edu.cit.resqmesh.data.repository.mesh

import android.content.SharedPreferences
import edu.cit.resqmesh.core.model.ChatMessage
import edu.cit.resqmesh.core.model.ConversationPolicy
import edu.cit.resqmesh.core.network.MeshNetworkGateway
import edu.cit.resqmesh.core.network.MeshPayload
import edu.cit.resqmesh.data.repository.BlockRelationshipStore
import edu.cit.resqmesh.data.repository.MeshReadyPeerEvents
import edu.cit.resqmesh.data.repository.MeshRepository
import edu.cit.resqmesh.data.repository.MessageStore
import edu.cit.resqmesh.data.repository.PeerNameStore
import edu.cit.resqmesh.data.repository.PeerPublicKeyDirectory
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A late Radio copy must reach the autoplay check with the age the sender gave it. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ConversationSenderTimeTest {
    private class Store : MessageStore {
        val rows = linkedMapOf<String, ChatMessage>()
        override val publicMessages = flowOf(emptyList<ChatMessage>())
        override val privateMessages = flowOf(emptyMap<String, List<ChatMessage>>())
        override suspend fun save(message: ChatMessage, targetName: String?) { rows[message.id] = message }
        override suspend fun markDelivered(messageId: String, readerName: String) = Unit
        override suspend fun markSeen(messageId: String, readerName: String) = Unit
        override suspend fun markFailed(messageId: String) = Unit
        override suspend fun expirePending(messageId: String) = Unit
        override suspend fun markPending(messageId: String) = Unit
        override suspend fun markSent(messageId: String) = Unit
        override suspend fun getPendingOutbox() = emptyList<Pair<ChatMessage, String?>>()
        override suspend fun deleteConversation(peerName: String) = Unit
    }

    private class Fixture(scope: CoroutineScope) {
        val store = Store()
        private val callbacks = mutableMapOf<String, Any?>()
        private val network = Proxy.newProxyInstance(
            MeshNetworkGateway::class.java.classLoader, arrayOf(MeshNetworkGateway::class.java)
        ) { _, method, args ->
            when {
                method.name == "getMyNodeId" -> "A1"
                method.name.startsWith("set") -> { callbacks[method.name.removePrefix("set")] = args?.get(0); null }
                method.returnType == Boolean::class.javaPrimitiveType -> false
                else -> null
            }
        } as MeshNetworkGateway
        private val prefs = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)
        ) { _, _, _ -> null } as SharedPreferences
        @Suppress("unused")
        private val repo = MeshRepository(network, store, BlockRelationshipStore(prefs), PeerPublicKeyDirectory(prefs),
            scope, MeshReadyPeerEvents(), object : PeerNameStore {
                override val names = flowOf(emptyMap<String, String>())
                override suspend fun observeFullName(fullName: String) = Unit
            })

        @Suppress("UNCHECKED_CAST")
        fun receive(payload: MeshPayload) =
            (callbacks.getValue("OnConversationMessage") as (String, MeshPayload) -> Unit)("B", payload)
    }

    private fun radio(id: String, sentAt: Long) = MeshPayload(id = id, type = "CONVERSATION", senderName = "Bob#B1",
        senderNodeId = "B1", text = "note", conversationKind = "RADIO", channelId = "1", ttl = 1, createdAt = sentAt)

    @Test fun oldRadioCopyKeepsItsAgeAndDoesNotAutoplay() = runTest {
        val f = Fixture(backgroundScope)
        val sentAt = System.currentTimeMillis() - 5 * 60_000L
        f.receive(radio("old", sentAt)); runCurrent()
        val stored = f.store.rows.getValue("old")
        assertEquals(sentAt, stored.timestamp)
        assertFalse(ConversationPolicy.autoplay(stored.copy(audioBase64 = "audio"), "1", true))
    }

    @Test fun recentRadioCopyStillAutoplays() = runTest {
        val f = Fixture(backgroundScope)
        f.receive(radio("new", System.currentTimeMillis() - 5_000L)); runCurrent()
        assertTrue(ConversationPolicy.autoplay(f.store.rows.getValue("new").copy(audioBase64 = "audio"), "1", true))
    }

    @Test fun missingOrFutureSenderTimeFallsBackToArrival() {
        assertEquals(500L, senderTime(0L, 500L))
        assertEquals(500L, senderTime(-1L, 500L))
        assertEquals(500L, senderTime(900L, 500L))
        assertEquals(300L, senderTime(300L, 500L))
    }
}
