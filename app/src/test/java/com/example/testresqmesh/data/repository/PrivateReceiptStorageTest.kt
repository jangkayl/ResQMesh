package com.example.testresqmesh.data.repository

import android.content.SharedPreferences
import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.network.MeshNetworkGateway
import java.lang.reflect.Proxy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PrivateReceiptStorageTest {
    private class Store : MessageStore {
        val rows = mutableMapOf<String, ChatMessage>()
        var gate: CompletableDeferred<Unit>? = null
        override val publicMessages = flowOf(emptyList<ChatMessage>())
        override val privateMessages = flowOf(emptyMap<String, List<ChatMessage>>())
        override suspend fun contains(id: String) = id in rows
        override suspend fun incomingPrivateWasSeen(messageId: String) = rows[messageId]?.seenBy?.isNotEmpty() == true
        override suspend fun save(message: ChatMessage, targetName: String?) { gate?.await(); rows[message.id] = message }
        override suspend fun markDelivered(messageId: String, readerName: String) {}
        override suspend fun markSeen(messageId: String, readerName: String) { rows[messageId]?.let { rows[messageId] = it.copy(seenBy = listOf(readerName)) } }
        override suspend fun isUnacknowledgedPrivateSend(messageId: String) = rows[messageId]?.let {
            it.isMine && it.isPrivate && it.deliveredTo.isEmpty() && it.seenBy.isEmpty()
        } == true
        override suspend fun markFailed(messageId: String) {
            if (isUnacknowledgedPrivateSend(messageId)) rows[messageId]?.let { rows[messageId] = it.copy(deliveredTo = listOf("FAILED")) }
        }
        override suspend fun expirePending(messageId: String) {}
        override suspend fun markPending(messageId: String) {
            if (isUnacknowledgedPrivateSend(messageId)) rows[messageId]?.let { rows[messageId] = it.copy(deliveredTo = listOf("PENDING")) }
        }
        override suspend fun markSent(messageId: String) {}
        override suspend fun getPendingOutbox() = emptyList<Pair<ChatMessage, String?>>()
        override suspend fun deleteConversation(peerName: String) {}
    }
    @Test fun missingReceiptReturnsAcceptedSendToPendingWithoutWaitingOnHealthySends() = runTest {
        val store = Store()
        val callbacks = mutableMapOf<String, Any?>()
        val network = Proxy.newProxyInstance(MeshNetworkGateway::class.java.classLoader, arrayOf(MeshNetworkGateway::class.java)) { _, method, args ->
            when {
                method.name.startsWith("set") -> { callbacks[method.name.substring(3)] = args!![0]; null }
                method.name.startsWith("get") -> callbacks[method.name.substring(3)]
                method.returnType == Boolean::class.javaPrimitiveType -> false
                else -> null
            }
        } as MeshNetworkGateway
        val prefs = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)) { _, _, _ -> null } as SharedPreferences
        val repo = MeshRepository(network, store, BlockRelationshipStore(prefs), PeerPublicKeyDirectory(prefs), backgroundScope,
            MeshReadyPeerEvents(), object : PeerNameStore {
                override val names = flowOf(emptyMap<String, String>())
                override suspend fun observeFullName(fullName: String) {}
            })
        runCurrent()
        val accepted = ChatMessage(id = "accepted", senderName = "Alice#A001", text = "test", isMine = true,
            imageBase64 = null, audioBase64 = null, isPrivate = true, timestamp = System.currentTimeMillis())
        store.rows[accepted.id] = accepted
        val awaitReceipt = MeshRepository::class.java.getDeclaredMethod("awaitPrivateReceipt", String::class.java).apply { isAccessible = true }
        awaitReceipt.invoke(repo, accepted.id); runCurrent()
        assertEquals(0L, testScheduler.currentTime)
        advanceTimeBy(15_000); runCurrent()
        assertEquals(listOf("PENDING"), store.rows[accepted.id]!!.deliveredTo)
        // Receipt confirmation wins over a later timeout.
        store.rows[accepted.id] = accepted.copy(seenBy = listOf("Bob#B001"))
        awaitReceipt.invoke(repo, accepted.id); runCurrent(); advanceTimeBy(15_000); runCurrent()
        assertEquals(listOf("Bob#B001"), store.rows[accepted.id]!!.seenBy)
        assertTrue(store.rows[accepted.id]!!.deliveredTo.isEmpty())
    }
    @Test fun receiptFollowsStorageAndDuplicateReplayPreservesSeenStateAndNotificationCount() = runTest {
        val store = Store(); store.gate = CompletableDeferred()
        val callbacks = mutableMapOf<String, Any?>(); var receipts = 0; var notifications = 0; var seenReceipts = 0
        val network = Proxy.newProxyInstance(MeshNetworkGateway::class.java.classLoader, arrayOf(MeshNetworkGateway::class.java)) { _, method, args ->
            when {
                method.name.startsWith("set") -> { callbacks[method.name.substring(3)] = args!![0]; null }
                method.name.startsWith("get") -> callbacks[method.name.substring(3)]
                method.name == "broadcastDeliveredReceipt" -> { assertTrue(store.rows.containsKey(args!![0])); receipts++; null }
                method.name == "broadcastSeenReceipt" -> { assertTrue(store.rows[args!![0]]!!.seenBy.isNotEmpty()); seenReceipts++; null }
                method.name == "showPrivateMessageNotification" -> { notifications++; null }
                method.returnType == Boolean::class.javaPrimitiveType -> false
                else -> null
            }
        } as MeshNetworkGateway
        val prefs = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)) { _, _, _ -> null } as SharedPreferences
        val repo = MeshRepository(network, store, BlockRelationshipStore(prefs), PeerPublicKeyDirectory(prefs), backgroundScope,
            MeshReadyPeerEvents(), object : PeerNameStore {
                override val names = flowOf(emptyMap<String, String>())
                override suspend fun observeFullName(fullName: String) {}
            })
        repo.startNode("Alice", "", "test", "A001")
        fun incoming() = network.onMessageReceived!!.invoke("ep-bob", "logical", "Bob#B001", "test", true, false,
            null, null, null, null, "BLE", listOf("Bob#B001"), "1")
        incoming(); runCurrent(); assertEquals(0, receipts); assertEquals(0, notifications)
        store.gate!!.complete(Unit); runCurrent()
        assertEquals(1, receipts); assertEquals(1, notifications)
        store.markSeen("logical", "Me")
        incoming(); runCurrent()
        assertEquals(2, receipts); assertEquals(1, notifications); assertEquals(1, store.rows.size)
        assertEquals(1, seenReceipts)
        assertEquals(listOf("Me"), store.rows["logical"]!!.seenBy)
    }
}
