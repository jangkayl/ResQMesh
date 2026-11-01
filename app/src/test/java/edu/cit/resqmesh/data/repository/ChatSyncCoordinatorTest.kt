package edu.cit.resqmesh.data.repository

import edu.cit.resqmesh.core.model.ChatMessage
import edu.cit.resqmesh.core.model.ConnectedDevice
import edu.cit.resqmesh.core.network.MeshNetworkGateway
import edu.cit.resqmesh.core.network.MeshPayload
import edu.cit.resqmesh.core.network.TransportDispatchResult
import edu.cit.resqmesh.data.local.entity.ChatSyncEntry
import edu.cit.resqmesh.data.local.entity.ChatSyncIngestion
import edu.cit.resqmesh.data.local.entity.ChatSyncRow
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.serialization.ExperimentalSerializationApi::class)
class ChatSyncCoordinatorTest {
    private class Store : MessageStore {
        val offered = mutableListOf<ChatSyncEntry>()
        val rows = linkedMapOf<String, ChatSyncRow>()
        val extraRows = mutableListOf<ChatSyncRow>()
        val saved = linkedSetOf<String>()
        val pending = linkedMapOf<String, Long>()
        val filled = mutableListOf<String>()
        override val publicMessages = flowOf(emptyList<ChatMessage>())
        override val privateMessages = flowOf(emptyMap<String, List<ChatMessage>>())
        override suspend fun save(message: ChatMessage, targetName: String?) = Unit
        override suspend fun contains(id: String) = id in saved
        override suspend fun markDelivered(messageId: String, readerName: String) = Unit
        override suspend fun markSeen(messageId: String, readerName: String) = Unit
        override suspend fun markFailed(messageId: String) = Unit
        override suspend fun expirePending(messageId: String) = Unit
        override suspend fun markPending(messageId: String) = Unit
        override suspend fun markSent(messageId: String) = Unit
        override suspend fun getPendingOutbox() = emptyList<Pair<ChatMessage, String?>>()
        override suspend fun deleteConversation(peerName: String) = Unit
        override suspend fun chatSyncIndex(limit: Int, maxSosThreads: Int) =
            offered.sortedByDescending { it.sentAt }.take(limit)
        override suspend fun chatSyncRows(ids: List<String>) = rows.values.filter { it.id in ids } + extraRows
        override suspend fun chatSyncMessage(id: String) = rows[id]?.takeIf { it.mediaKind.isNotEmpty() }?.let {
            ChatMessage(it.id, it.senderName, it.text, null, "audio", isMine = false, timestamp = it.sentAt)
        }
        override suspend fun pendingSyncMedia(limit: Int) =
            pending.entries.sortedByDescending { it.value }.take(limit).map { it.key }

        fun offer(id: String, sentAt: Long, kind: String = "COMMUNITY", sosId: String = "", media: String = "") =
            offer(ChatSyncEntry(id, sentAt, kind, "Ann#A1", sosId, media))

        fun offer(entry: ChatSyncEntry, text: String = "hello") {
            offered += entry
            rows[entry.id] = ChatSyncRow(entry.id, entry.sentAt, entry.kind, entry.senderName, "A1", text, entry.sosId,
                mediaKind = entry.mediaKind)
        }
    }

    private class Network(val scope: CoroutineScope) {
        val nodes = mutableMapOf<String, Node>()
        val sent = mutableListOf<MeshPayload>()
        val copies = mutableListOf<String>()
        var dropCopies = false
        var copyBytes = 100
        fun send(from: Node, to: String, bytes: ByteArray): TransportDispatchResult {
            val payload = ProtoBuf.decodeFromByteArray<MeshPayload>(bytes)
            sent += payload
            scope.launch { nodes.getValue(to).deliver(from.id, payload) }
            return TransportDispatchResult.ACCEPTED
        }
        fun sendCopy(from: Node, to: String, bytes: ByteArray): TransportDispatchResult {
            val payload = ProtoBuf.decodeFromByteArray<MeshPayload>(bytes)
            copies += payload.id
            if (dropCopies) return TransportDispatchResult.ACCEPTED
            scope.launch { nodes.getValue(to).deliver(from.id, payload) }
            return TransportDispatchResult.ACCEPTED
        }
        fun connect(a: Node, b: Node) {
            a.peers.update(listOf(ConnectedDevice(b.id, b.id, nodeId = b.id, isPayloadReady = true)))
            b.peers.update(listOf(ConnectedDevice(a.id, a.id, nodeId = a.id, isPayloadReady = true)))
        }
        fun rowRequests() = sent.count { it.type == ChatSyncCoordinator.REQUEST && it.text.contains("\"ROWS\"") }
    }

    private class Node(val id: String, val network: Network) {
        val store = Store()
        val peers = MeshReadyPeerEvents()
        val blocked = mutableSetOf<String>()
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        var knownSos = true
        val released = mutableListOf<String>()
        private var listener: ((String, MeshPayload) -> Unit)? = null
        private val gateway = Proxy.newProxyInstance(
            MeshNetworkGateway::class.java.classLoader, arrayOf(MeshNetworkGateway::class.java)
        ) { _, method, args ->
            when (method.name) {
                "setOnChatSyncPacket" -> {
                    @Suppress("UNCHECKED_CAST")
                    listener = args!![0] as ((String, MeshPayload) -> Unit)?
                    null
                }
                "getMyNodeId" -> id
                "markPayloadStored" -> { released += args!![0] as String; null }
                "sendSyncCopy" -> network.sendCopy(this, args!![0] as String, args[1] as ByteArray)
                "hasReadyEndpoint" -> true
                "isDeviceBlocked" -> args!![0] in blocked
                "linkEstablishedAt" -> 1L
                "sendDirectPayload", "sendPriorityPayload" ->
                    network.send(this, args!![0] as String, args[1] as ByteArray)
                else -> null
            }
        } as MeshNetworkGateway

        private val sink = object : ChatSyncSink {
            override suspend fun ingest(row: ChatSyncRow) = this@Node.ingest(row)
            override suspend fun ingestMedia(payload: MeshPayload): Boolean {
                val waiting = store.pending.remove(payload.id) != null
                if (waiting) store.filled += payload.id
                return waiting
            }
        }

        @Suppress("unused")
        private val coordinator = ChatSyncCoordinator(gateway, peers, store, network.scope, sink, changes) { message ->
            ProtoBuf.encodeToByteArray(MeshPayload.serializer(), MeshPayload(id = message.id, type = "CONVERSATION",
                chatSync = true, audioBytes = ByteArray(network.copyBytes)))
        }

        init { network.nodes[id] = this }

        fun deliver(from: String, payload: MeshPayload) = listener?.invoke(from, payload)

        private fun ingest(row: ChatSyncRow): ChatSyncIngestion {
            if (row.kind == "SOS" && !knownSos) return ChatSyncIngestion.DEFERRED
            store.saved += row.id
            if (row.mediaKind.isNotEmpty()) store.pending[row.id] = row.sentAt
            return ChatSyncIngestion.STORED
        }
    }

    private fun scene(scope: CoroutineScope): Pair<Node, Node> {
        val network = Network(scope)
        return Node("A", network) to Node("B", network)
    }

    private fun Node.link(other: Node) = network.connect(this, other)

    @Test fun newPhoneGetsTheNewestFiftyMessages() = runTest {
        val (a, b) = scene(backgroundScope)
        repeat(60) { a.store.offer("m$it", 1_000L + it) }
        a.link(b); advanceTimeBy(5_000); runCurrent()
        assertEquals(ChatSyncCoordinator.MAX_MESSAGES_PER_CONVERSATION, b.store.saved.size)
        assertEquals((10 until 60).map { "m$it" }.toSet(), b.store.saved)
    }

    @Test fun placeholdersArriveWithTextAndMediaWaitsForTheSettle() = runTest {
        val (a, b) = scene(backgroundScope)
        a.store.offer("text", 1_000L)
        a.store.offer("voice", 2_000L, media = "VOICE")
        a.link(b); advanceTimeBy(5_000); runCurrent()
        assertEquals(setOf("text", "voice"), b.store.saved)
        assertTrue(a.network.copies.isEmpty())
        advanceTimeBy(ChatMediaPuller.SETTLE_MS); runCurrent()
        assertEquals(listOf("voice"), b.store.filled)
    }

    @Test fun aNeighborSendsImagesItSentItselfNewestFirst() = runTest {
        val (a, b) = scene(backgroundScope)
        a.store.offer("older", 1_000L, media = "IMAGE")
        a.store.offer("newer", 2_000L, media = "IMAGE")
        a.link(b); advanceTimeBy(20_000); runCurrent()
        assertEquals(listOf("newer", "older"), b.store.filled)
    }

    @Test fun onlyTheNewestTenPerConversationAreFetched() = runTest {
        val (a, b) = scene(backgroundScope)
        repeat(12) { a.store.offer("m$it", 1_000L + it, media = "IMAGE") }
        a.link(b); advanceTimeBy(40_000); runCurrent()
        // The fake keeps pending rows, so each pass reaches the next ten; the first pass proves the order and cap.
        assertEquals((11 downTo 2).map { "m$it" }, b.store.filled.take(ChatMediaPuller.MAX_PER_CONVERSATION))
    }

    @Test fun aNeighborStopsAtItsByteBudget() = runTest {
        val (a, b) = scene(backgroundScope)
        a.network.copyBytes = 600 * 1024
        a.store.offer("first", 2_000L, media = "IMAGE")
        a.store.offer("second", 1_000L, media = "IMAGE")
        a.link(b); advanceTimeBy(20_000); runCurrent()
        assertEquals(listOf("first"), b.store.filled)
        assertEquals(listOf("first"), a.network.copies)
    }

    @Test fun anUnrequestedCopyIsIgnoredButItsCustodyIsReleased() = runTest {
        val (_, b) = scene(backgroundScope)
        b.store.pending["target"] = 1_000L
        b.deliver("A", MeshPayload(id = "target", type = "CONVERSATION", chatSync = true))
        runCurrent()
        assertTrue(b.store.filled.isEmpty())
        assertEquals(listOf("target"), b.released)
    }

    @Test fun aLostCopyStaysPendingAndIsAskedForAgain() = runTest {
        val (a, b) = scene(backgroundScope)
        a.store.offer("voice", 1_000L, media = "VOICE")
        a.network.dropCopies = true
        a.link(b); advanceTimeBy(20_000); runCurrent()
        assertTrue(b.store.filled.isEmpty())
        assertEquals(listOf("voice"), b.store.pending.keys.toList())
        a.network.dropCopies = false
        advanceTimeBy(160_000); runCurrent()
        assertEquals(listOf("voice"), b.store.filled)
    }

    @Test fun rowsNobodyAskedForAreIgnored() = runTest {
        val (a, b) = scene(backgroundScope)
        a.store.offer("asked", 1_000L)
        a.store.extraRows += ChatSyncRow("forged", 1_000L, "COMMUNITY", "Ann#A1", "A1", "fake")
        a.link(b); advanceTimeBy(5_000); runCurrent()
        assertEquals(setOf("asked"), b.store.saved)
    }

    @Test fun aResponseToNoRequestIsIgnored() = runTest {
        val (_, b) = scene(backgroundScope)
        val packet = ChatSyncPacket("unknown", ChatSyncCoordinator.ROWS,
            rows = listOf(ChatSyncRow("forged", 1L, "COMMUNITY", "Ann#A1", "A1", "fake")))
        b.deliver("A", MeshPayload(id = "x", type = ChatSyncCoordinator.RESPONSE,
            text = Json.encodeToString(ChatSyncPacket.serializer(), packet)))
        runCurrent()
        assertTrue(b.store.saved.isEmpty())
    }

    @Test fun aRowThatDiffersFromItsOfferIsIgnored() = runTest {
        val (a, b) = scene(backgroundScope)
        a.store.offer("m", 1_000L)
        a.store.rows["m"] = a.store.rows.getValue("m").copy(kind = "SOS", sosId = "other")
        a.link(b); advanceTimeBy(5_000); runCurrent()
        assertTrue(b.store.saved.isEmpty())
    }

    @Test fun repliesToAnUnknownSosWaitUntilTheAlertArrives() = runTest {
        val (a, b) = scene(backgroundScope)
        b.knownSos = false
        a.store.offer("reply", 1_000L, kind = "SOS", sosId = "s1")
        a.link(b); advanceTimeBy(5_000); runCurrent()
        assertTrue(b.store.saved.isEmpty())
        b.knownSos = true
        b.changes.tryEmit(Unit); advanceTimeBy(5_000); runCurrent()
        assertEquals(setOf("reply"), b.store.saved)
    }

    @Test fun blockedSendersAreNeitherOfferedNorTaken() = runTest {
        val (a, b) = scene(backgroundScope)
        a.store.offer("keep", 1_000L)
        a.store.offer(ChatSyncEntry("blockedByOwner", 2_000L, "COMMUNITY", "Eve#E1"))
        a.blocked += "Eve#E1"
        a.store.offer(ChatSyncEntry("blockedByReader", 3_000L, "COMMUNITY", "Mal#M1"))
        b.blocked += "Mal#M1"
        a.link(b); advanceTimeBy(5_000); runCurrent()
        assertEquals(setOf("keep"), b.store.saved)
    }

    @Test fun anUnchangedNeighborIsNotPulledAgain() = runTest {
        val (a, b) = scene(backgroundScope)
        a.store.offer("m", 1_000L)
        a.link(b); advanceTimeBy(5_000); runCurrent()
        val requests = a.network.rowRequests()
        assertEquals(1, requests)
        advanceTimeBy(70_000); runCurrent()
        assertEquals(requests, a.network.rowRequests())
    }

    @Test fun aNewMessageIsPulledOnTheNextCycle() = runTest {
        val (a, b) = scene(backgroundScope)
        a.store.offer("first", 1_000L)
        a.link(b); advanceTimeBy(5_000); runCurrent()
        a.store.offer("second", 2_000L)
        advanceTimeBy(40_000); runCurrent()
        assertEquals(setOf("first", "second"), b.store.saved)
    }

    @Test fun pagesStayInTheSmallFrameLane() = runTest {
        val (a, b) = scene(backgroundScope)
        repeat(50) { a.store.offer(ChatSyncEntry("message-$it", 1_000L + it, "COMMUNITY", "Ann#A1"), "x".repeat(600)) }
        a.link(b); advanceTimeBy(30_000); runCurrent()
        assertEquals(50, b.store.saved.size)
        assertFalse(a.network.sent.filter { it.type == ChatSyncCoordinator.RESPONSE }.any { it.text.length > 4_096 })
    }
}
