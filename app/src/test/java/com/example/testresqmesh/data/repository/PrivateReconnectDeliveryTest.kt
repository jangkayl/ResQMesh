package com.example.testresqmesh.data.repository

import android.content.SharedPreferences
import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.network.MeshNetworkGateway
import com.example.testresqmesh.core.network.BroadcastDispatchResult
import com.example.testresqmesh.core.network.MeshPayload
import com.example.testresqmesh.core.network.TransportDispatchResult
import com.example.testresqmesh.core.network.OutboundFrameEvent
import java.lang.reflect.Proxy
import java.security.KeyPairGenerator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.serialization.ExperimentalSerializationApi::class)
class PrivateReconnectDeliveryTest {
    private companion object {
        // Real JVM recipient keys exercise PayloadFactory without production test hooks.
        val firstKey: String by lazy { recipientKey() }
        val replacementKey: String by lazy { recipientKey() }
        fun recipientKey(): String = java.util.Base64.getEncoder().encodeToString(
            KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().public.encoded
        )
    }

    private class Store : MessageStore {
        val rows = linkedMapOf<String, ChatMessage>()
        val targets = mutableMapOf<String, String?>()
        var acceptanceGate: CompletableDeferred<Unit>? = null
        override val publicMessages = flowOf(emptyList<ChatMessage>())
        override val privateMessages = flowOf(emptyMap<String, List<ChatMessage>>())
        override suspend fun save(message: ChatMessage, targetName: String?) {
            rows[message.id] = message; targets[message.id] = targetName
        }
        override suspend fun contains(id: String) = id in rows
        override suspend fun incomingPrivateWasSeen(messageId: String) = rows[messageId]?.let {
            !it.isMine && it.isPrivate && it.seenBy.isNotEmpty()
        } == true
        override suspend fun saveIncomingPrivateIfAbsent(message: ChatMessage): PrivateMessageInsert {
            val existing = rows[message.id]
            if (existing != null) return if (!existing.isMine && NodeIdentity.matches(existing.senderName, message.senderName)) {
                PrivateMessageInsert.DUPLICATE
            } else PrivateMessageInsert.REJECTED
            save(message, message.senderName)
            return PrivateMessageInsert.INSERTED
        }
        override suspend fun recoverUnacknowledgedPrivateSends() {
            rows.values.filter { it.isMine && targets[it.id] != null && it.deliveredTo.isEmpty() && it.seenBy.isEmpty() }
                .forEach { markPending(it.id) }
        }
        override suspend fun isUnacknowledgedPrivateSend(messageId: String) = rows[messageId]?.let {
            it.isMine && targets[it.id] != null && it.deliveredTo.isEmpty() && it.seenBy.isEmpty()
        } == true
        private fun isPendingSend(messageId: String) = rows[messageId]?.let {
            it.isMine && it.deliveredTo == listOf("PENDING") && it.seenBy.isEmpty()
        } == true
        override suspend fun markDelivered(messageId: String, readerName: String) {
            if (!receiptMatchesTarget(messageId, readerName)) return
            rows[messageId]?.let { rows[messageId] = it.copy(deliveredTo = listOf(readerName)) }
        }
        override suspend fun markSeen(messageId: String, readerName: String) {
            if (!receiptMatchesTarget(messageId, readerName)) return
            rows[messageId]?.let { rows[messageId] = it.copy(seenBy = listOf(readerName)) }
        }
        private fun receiptMatchesTarget(messageId: String, readerName: String): Boolean {
            val row = rows[messageId] ?: return false
            val target = targets[messageId]
            return !row.isMine || target == null || NodeIdentity.matches(target, readerName)
        }
        override suspend fun markFailed(messageId: String) {
            if (isUnacknowledgedPrivateSend(messageId)) rows[messageId]?.let { rows[messageId] = it.copy(deliveredTo = listOf("FAILED")) }
        }
        override suspend fun expirePending(messageId: String) {
            if (isPendingSend(messageId)) rows[messageId]?.let { rows[messageId] = it.copy(deliveredTo = listOf("FAILED")) }
        }
        override suspend fun markPending(messageId: String) {
            if (isUnacknowledgedPrivateSend(messageId)) rows[messageId]?.let { rows[messageId] = it.copy(deliveredTo = listOf("PENDING")) }
        }
        override suspend fun markSent(messageId: String) {
            acceptanceGate?.await()
            if (isPendingSend(messageId)) rows[messageId]?.let { rows[messageId] = it.copy(deliveredTo = emptyList()) }
        }
        override suspend fun getPendingOutbox() = rows.values.filter { isPendingSend(it.id) }.map { it to targets[it.id] }
        override suspend fun deleteConversation(peerName: String) {}
    }

    private class Fixture(
        scope: CoroutineScope,
        val store: Store = Store(),
        trusted: Boolean = true
    ) {
        val properties = mutableMapOf<String, Any?>()
        val preferences = mutableMapOf<String, String>()
        var dispatchResult = TransportDispatchResult.ACCEPTED
        val transmissions = mutableListOf<MeshPayload>()
        val endpoints = mutableListOf<String>()
        val receipts = mutableListOf<Pair<String, String>>()
        val forwards = mutableListOf<Pair<String, MeshPayload>>()
        var notifications = 0
        var broadcasts = 0
        var publicResult = BroadcastDispatchResult(mapOf("ep-bob" to TransportDispatchResult.REJECTED_QUEUE_FULL))
        var trackedTransport = false
        var connected = true
        var pendingRelayCustody = false
        var onDispatch: ((MeshPayload) -> Unit)? = null

        private val prefs: SharedPreferences
        init {
            lateinit var editor: SharedPreferences.Editor
            editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { _, method, args ->
                when (method.name) {
                    "putString" -> { preferences[args!![0] as String] = args[1] as String; editor }
                    "commit" -> true
                    "apply" -> null
                    else -> editor
                }
            } as SharedPreferences.Editor
            prefs = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
                when (method.name) {
                    "getString" -> preferences[args!![0]] ?: args[1]
                    "getStringSet" -> emptySet<String>()
                    "edit" -> editor
                    else -> null
                }
            } as SharedPreferences
        }
        val keys = PeerPublicKeyDirectory(prefs).also {
            if (trusted) it.observe("Bob#B001", "B001", firstKey)
        }
        val network = Proxy.newProxyInstance(MeshNetworkGateway::class.java.classLoader, arrayOf(MeshNetworkGateway::class.java)) { _, method, args ->
            when {
                method.name.startsWith("set") -> { properties[method.name.substring(3)] = args!![0]; null }
                method.name == "getReportsOutboundProgress" -> trackedTransport
                method.name.startsWith("get") -> properties[method.name.substring(3)]
                method.name == "sendDirectPayload" -> {
                    val payload = ProtoBuf.decodeFromByteArray(MeshPayload.serializer(), args!![1] as ByteArray)
                    assertEquals("Persist PENDING before transport acceptance", listOf("PENDING"), store.rows[payload.targetMessageId]?.deliveredTo)
                    transmissions += payload; endpoints += args[0] as String
                    onDispatch?.invoke(payload)
                    dispatchResult
                }
                method.name in setOf("broadcastSeenReceipt", "broadcastDeliveredReceipt") -> {
                    assertTrue("Receipt must follow durable insertion", store.rows.containsKey(args!![0]))
                    receipts += (args[0] as String) to (args[2] as? String).orEmpty()
                    null // The gateway owns receipt transport/retry and exposes a Unit result.
                }
                method.name in setOf("broadcastPayload", "broadcastPriorityPayload") -> {
                    val payload = ProtoBuf.decodeFromByteArray(MeshPayload.serializer(), args!![0] as ByteArray)
                    val ingress = args[1] as? String
                    if (ingress != null) {
                        assertTrue("Forward after storage", store.rows.containsKey(payload.id))
                        forwards += ingress to payload
                    } else broadcasts++
                    publicResult
                }
                method.name == "showPrivateMessageNotification" -> { notifications++; null }
                method.name == "currentMeshTtl" -> 10
                method.name == "linkEstablishedAt" -> 0L
                method.name == "hasPendingCustody" -> pendingRelayCustody
                method.name == "hasPendingTransfer" -> false
                method.name.startsWith("has") -> connected
                method.returnType == Boolean::class.javaPrimitiveType -> false
                else -> null
            }
        } as MeshNetworkGateway
        val repo = MeshRepository(network, store, BlockRelationshipStore(prefs), keys, scope, MeshReadyPeerEvents(), object : PeerNameStore {
            override val names = flowOf(emptyMap<String, String>())
            override suspend fun observeFullName(fullName: String) {}
        })
        init {
            repo.startNode("Alice", "", "test", "A001")
            ready()
        }
        fun ready(endpoint: String = "ep-bob") {
            connected = true
            network.onDeviceConnected!!.invoke(ConnectedDevice(endpoint, "Bob#B001", nodeId = "B001", isPayloadReady = true))
        }
        fun disconnect() {
            connected = false
            network.onDeviceDisconnected!!.invoke("ep-bob")
        }
        fun incoming(id: String = "incoming") = network.onMessageReceived!!.invoke(
            "ep-bob", id, "Bob#B001", "test", true, false, null, null, null, null, "BLE", listOf("Bob#B001"), "1"
        )
        fun delivered(id: String) = network.onMessageDelivered!!.invoke(id, "Bob#B001", emptyList())
        fun send(target: String = "Bob#B001") {
            assertTrue(repo.sendPrivateMessage(target, "test", null, null))
        }
    }

    private fun TestScope.fixture(store: Store = Store(), trusted: Boolean = true) =
        Fixture(backgroundScope, store, trusted)
    private fun TestScope.tick(ms: Long = 3_000L) { advanceTimeBy(ms); runCurrent() }

    private fun Fixture.frame(stage: com.example.testresqmesh.core.network.OutboundFrameEvent.Stage, wire: MeshPayload = transmissions.last()) {
        val at = System.nanoTime() / 1_000_000
        network.onOutboundFrame!!.invoke(com.example.testresqmesh.core.network.OutboundFrameEvent(
            wire.id, wire.targetMessageId, stage, "L2CAP", 1, at, 100, isPrivate = true
        ))
    }

    @Test fun publicRelayHandsRejectedNeighborsToTransportOnceDespiteDuplicateReception() = runTest {
        val f = fixture(); runCurrent()
        for (kind in listOf("COMMUNITY", "RADIO")) {
            val packet = MeshPayload(id = "relay-$kind", type = "CONVERSATION", senderName = "Charlie#C001",
                senderNodeId = "C001", text = "test", conversationKind = kind, channelId = "1", ttl = 3)
            repeat(2) { f.network.onConversationMessage!!.invoke("ingress", packet); runCurrent() }
        }
        assertEquals(2, f.forwards.size)
        assertEquals(0, f.broadcasts)
        assertEquals(4, f.receipts.size)
        assertTrue(f.forwards.all { (endpoint, packet) -> endpoint == "ingress" && packet.ttl == 2 && packet.relayHopCount == 1 })
        tick(20_000)
        assertEquals(2, f.forwards.size)
    }

    @Test fun duplicatePublicReceptionRecoversPendingCustodyWithoutDuplicatingStorage() = runTest {
        val f = fixture(); runCurrent()
        val packet = MeshPayload(id = "recover-relay", type = "CONVERSATION", senderName = "Charlie#C001",
            senderNodeId = "C001", text = "test", conversationKind = "COMMUNITY", channelId = "1", ttl = 3)
        f.network.onConversationMessage!!.invoke("ingress", packet); runCurrent()
        f.pendingRelayCustody = true
        f.network.onConversationMessage!!.invoke("ingress", packet); runCurrent()
        assertEquals(2, f.forwards.size)
        assertEquals(1, f.store.rows.values.count { it.id == packet.id })
        f.pendingRelayCustody = false
        f.network.onConversationMessage!!.invoke("ingress", packet); runCurrent()
        assertEquals(2, f.forwards.size)
        assertEquals(3, f.receipts.size)
    }

    @Test fun trackedReceiptDeadlineStartsOnlyAfterFullOriginTransmission() = runTest {
        val started = PrivateTransferProgress("wire", 1).observe(
            OutboundFrameEvent("wire", "message", OutboundFrameEvent.Stage.STARTED, "L2CAP", 1, 1_000, 100), 1_000)
        assertFalse(started.shouldRetry(25_000, 0, retained = true))
        val completed = started.observe(
            OutboundFrameEvent("wire", "message", OutboundFrameEvent.Stage.COMPLETED, "L2CAP", 1, 25_000, 100), 25_000)
        assertFalse(completed.shouldRetry(39_999, 0, retained = false))
        assertTrue(completed.shouldRetry(40_000, 0, retained = false))
    }

    @Test fun trackedQueueAndWriteDeadlinesRemainBoundedWithoutCallbacks() = runTest {
        val queued = PrivateTransferProgress("wire", 1)
        assertFalse(queued.shouldRetry(60_999, 1_000, retained = false))
        assertTrue(queued.shouldRetry(61_000, 1_000, retained = false))
        assertFalse(queued.shouldRetry(300_000, 1_000, retained = true))
    }

    @Test fun staleWireAndLateTransportFailureCannotUndoConfirmation() = runTest {
        val f = fixture(); f.send(); runCurrent()
        val old = f.transmissions.single()
        tick(15_000); assertEquals(2, f.transmissions.size)
        f.frame(com.example.testresqmesh.core.network.OutboundFrameEvent.Stage.COMPLETED, old); runCurrent()
        assertEquals(2, f.transmissions.size)
        f.delivered(old.targetMessageId); runCurrent()
        f.trackedTransport = true
        f.frame(com.example.testresqmesh.core.network.OutboundFrameEvent.Stage.FAILED); runCurrent()
        tick(100_000); assertEquals(2, f.transmissions.size)
        assertEquals(listOf("Bob#B001"), f.store.rows[old.targetMessageId]!!.deliveredTo)
    }

    @Test fun trackedFailureRetriesWithoutWaitingForReceiptTimeout() = runTest {
        val f = fixture(); f.trackedTransport = true; f.send(); runCurrent()
        f.frame(com.example.testresqmesh.core.network.OutboundFrameEvent.Stage.FAILED); runCurrent()
        tick(999); assertEquals(1, f.transmissions.size)
        tick(1); assertEquals(2, f.transmissions.size)
    }

    @Test fun repeatedStartCannotExtendAbsoluteWriteDeadline() = runTest {
        val event = OutboundFrameEvent("wire", "message", OutboundFrameEvent.Stage.STARTED, "L2CAP", 1, 1_000, 100)
        val started = PrivateTransferProgress("wire", 1).observe(event, 1_000).observe(event, 41_000)
        assertFalse(started.shouldRetry(60_999, 1_000, retained = false))
        assertTrue(started.shouldRetry(61_000, 1_000, retained = false))
    }

    @Test fun readyDirectFirstSendDispatchesWithoutAdvancingTime() = runTest {
        val f = fixture()
        f.send(); runCurrent()
        assertEquals(0L, testScheduler.currentTime)
        assertEquals(1, f.transmissions.size)
        assertTrue(f.store.rows.values.single().deliveredTo.isEmpty())
    }

    @Test fun readyMeshHopFirstSendDispatchesWithoutAdvancingTime() = runTest {
        val f = fixture()
        f.network.onPublicKeyReceived!!.invoke("Charlie#C001", "C001", firstKey)
        f.network.onRoutingTableReceived!!.invoke("Bob#B001", "B001", listOf("Alice#A001", "Charlie#C001"), listOf("A001", "C001"), 1L)
        f.send("Charlie#C001"); runCurrent()
        assertEquals(0L, testScheduler.currentTime)
        assertEquals(listOf("ep-bob"), f.endpoints)
        assertEquals(listOf("A001", "B001", "C001"), f.transmissions.single().directedRouteNodeIds)
    }

    @Test fun newSendDoesNotWaitForAnotherMessagesScheduledRetry() = runTest {
        val f = fixture(); f.dispatchResult = TransportDispatchResult.REJECTED_QUEUE_FULL
        f.send(); runCurrent()
        val oldId = f.transmissions.single().targetMessageId
        tick(1_000)
        f.dispatchResult = TransportDispatchResult.ACCEPTED
        f.send(); runCurrent()
        val newId = f.transmissions.last().targetMessageId
        assertNotEquals(oldId, newId)
        assertEquals(2, f.transmissions.size)
        tick(3_999)
        assertEquals(2, f.transmissions.size)
        tick(1)
        assertEquals(3, f.transmissions.size)
        assertEquals(oldId, f.transmissions.last().targetMessageId)
        assertEquals(1, f.transmissions.count { it.targetMessageId == newId })
    }

    @Test fun missingReceiptRetriesAtDeadlineWithoutAnotherPeerEvent() = runTest {
        val f = fixture(); f.send(); runCurrent()
        tick(14_999)
        assertEquals(1, f.transmissions.size)
        tick(1)
        assertEquals(2, f.transmissions.size)
        f.delivered(f.transmissions.last().targetMessageId); runCurrent(); tick(40_000)
        assertEquals(2, f.transmissions.size)
    }

    @Test fun publicRetryBackoffDoesNotDelayPrivateFirstSend() = runTest {
        val f = fixture(); runCurrent()
        f.repo.sendPublicMessage("test", null, null); runCurrent()
        assertEquals(1, f.broadcasts)
        f.publicResult = BroadcastDispatchResult(mapOf("ep-bob" to TransportDispatchResult.ACCEPTED))
        tick(1_000)
        f.send(); runCurrent()
        assertEquals(1, f.transmissions.size)
        assertEquals(1, f.broadcasts)
        tick(3_999)
        assertEquals(1, f.broadcasts)
        tick(1)
        assertEquals(2, f.broadcasts)
    }

    @Test fun sharedRetryBackoffDoesNotPostponePrivateDispatch() = runTest {
        val f = fixture(); runCurrent()
        f.repo.sendPublicMessage("test", null, null); runCurrent()
        f.dispatchResult = TransportDispatchResult.REJECTED_QUEUE_FULL
        f.send(); runCurrent()
        tick(4_999)
        assertEquals(1, f.broadcasts)
        assertEquals(1, f.transmissions.size)
        f.dispatchResult = TransportDispatchResult.ACCEPTED
        tick(1)
        assertEquals(2, f.transmissions.size)
        assertEquals(2, f.broadcasts)
    }

    @Test fun rejectedInitialSendRetriesWithoutAnotherPeerEvent() = runTest {
        val f = fixture(); f.dispatchResult = TransportDispatchResult.REJECTED_QUEUE_FULL
        f.send(); tick()
        assertEquals(1, f.transmissions.size)
        assertEquals(listOf("PENDING"), f.store.rows.values.single().deliveredTo)
        f.dispatchResult = TransportDispatchResult.ACCEPTED
        tick(8_000)
        assertEquals(2, f.transmissions.size)
        assertTrue(f.store.rows.values.single().deliveredTo.isEmpty())
        assertEquals(1, f.transmissions.map { it.targetMessageId }.distinct().size)
        assertEquals(2, f.transmissions.map { it.id }.distinct().size)
    }

    @Test fun lostReceiptRetriesThenReceiptStopsAllAttempts() = runTest {
        val f = fixture(); f.send(); tick()
        tick(23_000)
        assertEquals(2, f.transmissions.size)
        val id = f.transmissions.first().targetMessageId
        f.delivered(id); runCurrent(); tick(80_000)
        assertEquals(2, f.transmissions.size)
        assertEquals(listOf("Bob#B001"), f.store.rows[id]!!.deliveredTo)
    }

    @Test fun noReceiptStopsAtThreeAcceptedAttemptsAndFailedIsTerminal() = runTest {
        val f = fixture(); f.send(); tick(80_000)
        assertEquals(3, f.transmissions.size)
        assertEquals(listOf("FAILED"), f.store.rows.values.single().deliveredTo)
        f.ready(); tick(30_000)
        assertEquals(3, f.transmissions.size)
    }

    @Test fun concurrentTriggersCannotCancelActiveFlushOrDuplicateSend() = runTest {
        val f = fixture(); f.store.acceptanceGate = CompletableDeferred()
        f.send(); tick()
        assertEquals(1, f.transmissions.size)
        repeat(20) { f.ready() }
        tick(4_000)
        f.store.acceptanceGate!!.complete(Unit); runCurrent(); tick()
        assertEquals(1, f.transmissions.size)
        f.delivered(f.transmissions.single().targetMessageId); runCurrent(); tick(30_000)
        assertEquals(1, f.transmissions.size)
    }

    @Test fun fastReceiptAndReadWinOverAcceptanceAndTimeout() = runTest {
        val f = fixture()
        f.onDispatch = { p ->
            f.delivered(p.targetMessageId)
            f.network.onMessageSeen!!.invoke(p.targetMessageId, "Bob#B001")
        }
        f.send(); tick(80_000)
        assertEquals(1, f.transmissions.size)
        val message = f.store.rows.values.single()
        assertEquals(listOf("Bob#B001"), message.deliveredTo)
        assertEquals(listOf("Bob#B001"), message.seenBy)
    }

    @Test fun wrongRecipientCannotConfirmPrivateSendOrCancelRetry() = runTest {
        val f = fixture(); f.send(); tick()
        val id = f.transmissions.single().targetMessageId
        f.network.onMessageDelivered!!.invoke(id, "Other#C001", emptyList())
        f.network.onMessageSeen!!.invoke(id, "Other#C001")
        runCurrent()
        assertTrue(f.store.rows[id]!!.deliveredTo.isEmpty())
        assertTrue(f.store.rows[id]!!.seenBy.isEmpty())
        tick(12_000)
        assertEquals(2, f.transmissions.size)
        f.delivered(id); runCurrent(); tick(60_000)
        assertEquals(2, f.transmissions.size)
        assertEquals(listOf("Bob#B001"), f.store.rows[id]!!.deliveredTo)
    }

    @Test fun missingKeyRemainsPendingThenKeyReceiptFlushesIt() = runTest {
        val f = fixture(trusted = false); f.send(); tick()
        assertTrue(f.transmissions.isEmpty())
        assertEquals(listOf("PENDING"), f.store.rows.values.single().deliveredTo)
        f.network.onPublicKeyReceived!!.invoke("Bob#B001", "B001", firstKey); runCurrent()
        assertEquals(1, f.transmissions.size)
    }

    @Test fun changedKeyBlocksSavedAndNewSendsUntilApprovalAndReadyWake() = runTest {
        val f = fixture(); f.disconnect(); f.send(); tick()
        f.network.onPublicKeyReceived!!.invoke("Bob#B001", "B001", replacementKey)
        f.ready(); tick()
        assertTrue(f.transmissions.isEmpty())
        assertFalse(f.repo.sendPrivateMessage("Bob#B001", "test", null, null))
        assertTrue(f.repo.acceptPendingPublicKeyChange("Bob#B001")); runCurrent()
        assertFalse(f.repo.hasPendingPublicKeyChange("Bob#B001"))
        assertTrue(f.transmissions.isEmpty())
        f.ready(); runCurrent()
        assertEquals(1, f.transmissions.size)
    }

    @Test fun pendingKeyChangeIsRecheckedBeforeOutboxDispatch() = runTest {
        val f = fixture(trusted = false)
        f.send(); runCurrent()
        f.keys.observe("Bob#B001", "B001", firstKey)
        f.network.onPublicKeyReceived!!.invoke("Bob#B001", "B001", replacementKey)
        runCurrent()
        assertTrue(f.transmissions.isEmpty())
        assertTrue(f.repo.acceptPendingPublicKeyChange("Bob#B001")); tick()
        assertTrue(f.transmissions.isEmpty())
        f.ready(); runCurrent()
        assertEquals(1, f.transmissions.size)
    }

    @Test fun offlineQueuedMessageUsesReplacementEndpointAfterReconnect() = runTest {
        val f = fixture(); f.disconnect(); f.send(); tick()
        assertTrue(f.transmissions.isEmpty())
        f.ready("ep-replacement"); runCurrent()
        assertEquals(listOf("ep-replacement"), f.endpoints)
    }

    @Test fun startupRecoversOnlyUnacknowledgedPrivateSentRows() = runTest {
        val store = Store()
        fun row(id: String, state: List<String>, mine: Boolean = true, target: String? = "Bob#B001", seen: List<String> = emptyList()) {
            store.rows[id] = ChatMessage(id, "Alice#A001", "test", null, null, isMine = mine, isPrivate = target != null,
                timestamp = System.currentTimeMillis(), deliveredTo = state, seenBy = seen)
            store.targets[id] = target
        }
        row("sent", emptyList()); row("delivered", listOf("Bob#B001")); row("failed", listOf("FAILED"))
        row("read", emptyList(), seen = listOf("Bob#B001")); row("public", emptyList(), target = null)
        row("incoming", emptyList(), mine = false)
        val f = fixture(store); runCurrent()
        assertEquals(listOf("sent"), f.transmissions.map { it.targetMessageId })
    }

    @Test fun expiredOfflineMessageIsRejectedWhenReadyReturns() = runTest {
        val f = fixture(); f.disconnect(); f.send(); runCurrent()
        val pending = f.store.rows.values.single()
        f.store.rows[pending.id] = pending.copy(timestamp = System.currentTimeMillis() - 24 * 60 * 60 * 1000L - 1_000)
        f.ready(); runCurrent()
        assertEquals(listOf("FAILED"), f.store.rows.values.single().deliveredTo)
        assertTrue(f.transmissions.isEmpty())
    }

    @Test fun duplicateIncomingKeepsReadStateAndNotifiesOnceButReplaysBothReceipts() = runTest {
        val f = fixture(); f.incoming(); runCurrent()
        f.store.markSeen("incoming", "Me")
        f.incoming(); runCurrent()
        assertEquals(1, f.store.rows.size)
        assertEquals(listOf("Me"), f.store.rows["incoming"]!!.seenBy)
        assertEquals(1, f.notifications)
        assertEquals(3, f.receipts.size) // Duplicate replays both DELIVERED and SEEN.
        assertEquals(0, f.broadcasts)
    }

    @Test fun receiptRequestsUseTheIncomingEndpointAfterReconnect() = runTest {
        val f = fixture()
        f.incoming(); runCurrent(); f.disconnect(); f.ready("ep-replacement")
        f.network.onMessageReceived!!.invoke("ep-replacement", "incoming", "Bob#B001", "test", true,
            false, null, null, null, null, "BLE", listOf("Bob#B001"), "1")
        runCurrent()
        assertEquals(listOf("ep-bob", "ep-replacement"), f.receipts.map { it.second })
        assertEquals(1, f.notifications)
    }

    @Test fun duplicateReceiptRequestsNeverBroadcastPrivatePayload() = runTest {
        val f = fixture()
        f.incoming(); runCurrent(); f.incoming(); tick(30_000)
        assertEquals(2, f.receipts.size)
        assertEquals(0, f.broadcasts)
    }

    @Test fun incomingRelayPathReturnsReceiptBeforeRemoteTopologyArrives() = runTest {
        val f = fixture()
        f.network.onMessageReceived!!.invoke("ep-bob", "relay-incoming", "Charlie#C001", "test", true,
            false, null, null, null, null, "BLE", listOf("Charlie#C001", "Bob#B001"), "1")
        runCurrent()
        assertEquals(listOf("ep-bob"), f.receipts.map { it.second })
        assertEquals(0, f.broadcasts)
    }
}
