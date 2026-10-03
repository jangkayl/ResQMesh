package com.example.testresqmesh.core.network

import java.io.File
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
class ReliableMeshTransfersTest {
    @get:Rule val temporary = TemporaryFolder()
    private data class Wire(val from: String, val to: String, val payload: MeshPayload, val priority: Boolean)
    private inner class World {
        var time = 1_000_000L
        val queue = ArrayDeque<Wire>()
        val scheduled = mutableListOf<Pair<Long, () -> Unit>>()
        val nodes = mutableMapOf<String, Node>()
        val sentPieces = mutableListOf<Pair<String, Int>>()
        val sent = mutableListOf<Wire>()
        var completeFrames: (Wire) -> Boolean = { true }
        var drop: (Wire) -> Boolean = { false }
        inner class Node(val name: String, val root: File = temporary.newFolder(), var online: Boolean = true) {
            val arrived = mutableListOf<MeshPayload>()
            val events = mutableListOf<OutboundFrameEvent.Stage>()
            val logs = mutableListOf<String>()
            var autoStore = true
            var onward: ((ByteArray) -> Unit)? = null
            lateinit var engine: ReliableMeshTransfers
            init {
                nodes[name] = this
                val boot = mutableListOf<() -> Unit>()
                var starting = true
                engine = ReliableMeshTransfers(root, { online }, { nodes[it]?.online == true },
                    execute = { if (starting) boot.add(it) else it() }, schedule = { delay, action -> scheduled.add(time + delay to action) },
                    send = { peer, bytes, priority, callback ->
                        val payload = ProtoBuf.decodeFromByteArray<MeshPayload>(bytes).copy(targetNodeId = peer)
                        if (payload.type == ReliableMeshTransfers.PIECE) {
                            val piece = ProtoBuf.decodeFromByteArray<TransferPiece>(payload.liveAudioChunk!!)
                            if (!piece.status) sentPieces.add(name to piece.index)
                        }
                        val wire = Wire(name, peer, payload, priority)
                        queue.add(wire); sent.add(wire)
                        if (completeFrames(wire)) {
                            engine.frame(payload, OutboundFrameEvent.Stage.STARTED)
                            engine.frame(payload, OutboundFrameEvent.Stage.COMPLETED)
                        }
                        callback(TransportDispatchResult.ACCEPTED)
                    }, received = { _, bytes ->
                        val payload = ProtoBuf.decodeFromByteArray<MeshPayload>(bytes)
                        arrived.add(payload)
                        onward?.invoke(bytes)
                        if (autoStore) engine.stored(payload.targetMessageId.ifBlank { payload.id })
                    }, response = { _, _ -> logs.add("RECEIPT_REPLAY") },
                    event = { _, stage, _, _ -> events.add(stage) }, now = { time }, log = { logs.add(it) })
                starting = false
                boot.forEach { it() }
            }
        }
        fun pump(limit: Int = 10_000) {
            var count = 0
            while (queue.isNotEmpty()) {
                check(++count <= limit) { "Unbounded transfer loop" }
                val wire = queue.removeFirst()
                if (!drop(wire)) nodes[wire.to]?.takeIf { it.online }?.engine?.receive(wire.from, wire.payload)
            }
        }
        fun advance(ms: Long) {
            time += ms
            val due = scheduled.filter { it.first <= time }
            scheduled.removeAll(due.toSet())
            due.forEach { it.second() }
        }
        fun payload(id: String = "note", size: Int = 8192) = ProtoBuf.encodeToByteArray(
            MeshPayload(id = id, type = "CONVERSATION", conversationKind = "RADIO",
                audioBytes = ByteArray(size) { (it % 251).toByte() }))
    }

    @Test fun piecesReassembleExactlyAndCustodyDoesNotInventDeliveryReceipt() {
        val w = World(); val a = w.Node("A"); val b = w.Node("B")
        assertTrue(a.engine.offer("B", w.payload(), false).accepted)
        w.pump()
        assertEquals(1, b.arrived.size)
        assertArrayEquals(ByteArray(8192) { (it % 251).toByte() }, b.arrived.single().audioBytes)
        assertTrue(a.events.contains(OutboundFrameEvent.Stage.COMPLETED))
        assertFalse(b.arrived.any { it.type == "DELIVERED" })
    }

    @Test fun freshTransferAndFirstReadinessSendDataWithoutAStatusRoundTrip() {
        val w = World(); val a = w.Node("A"); val b = w.Node("B", online = false)
        a.engine.offer("B", w.payload(), false)
        assertTrue(w.queue.isEmpty())
        b.online = true
        a.engine.wake("B", reconcile = false)
        assertFalse(ProtoBuf.decodeFromByteArray<TransferPiece>(w.queue.first().payload.liveAudioChunk!!).status)
        w.pump()
        assertEquals(1, b.arrived.size)
    }

    @Test fun queuedProbeDoesNotRetryEarlyAndDelayedReplySurvivesRetry() {
        val w = World(); val a = w.Node("A"); val b = w.Node("B")
        a.engine.offer("B", w.payload(), false)
        w.queue.clear()
        w.completeFrames = { false }
        a.engine.wake("B")
        val query = w.queue.removeFirst()
        w.advance(7_000)
        assertTrue(w.queue.isEmpty())
        a.engine.frame(query.payload, OutboundFrameEvent.Stage.COMPLETED)
        w.advance(2_900)
        assertTrue(w.queue.isEmpty())
        w.advance(200)
        val retry = w.queue.removeFirst()
        val oldPacket = ProtoBuf.decodeFromByteArray<TransferPiece>(query.payload.liveAudioChunk!!)
        val retryPacket = ProtoBuf.decodeFromByteArray<TransferPiece>(retry.payload.liveAudioChunk!!)
        assertEquals(oldPacket.requestId, retryPacket.requestId)
        b.engine.receive("A", query.payload)
        a.engine.receive("B", w.queue.removeFirst().payload)
        assertFalse(ProtoBuf.decodeFromByteArray<TransferPiece>(w.queue.first().payload.liveAudioChunk!!).status)
        w.pump()
        assertEquals(1, b.arrived.size)
    }

    @Test fun missingProbeCallbacksStillHaveABoundedQueueWait() {
        val w = World(); val a = w.Node("A"); w.Node("B")
        a.engine.offer("B", w.payload(), false, durableRelay = true)
        w.queue.clear(); w.sent.clear()
        w.completeFrames = { false }
        a.engine.wake("B")
        w.queue.removeFirst()
        w.advance(29_999)
        assertTrue(w.queue.isEmpty())
        w.advance(101) // The deadline is serviced by the bounded 100 ms scheduler.
        assertEquals(1, w.queue.size)
        assertEquals(2, w.sent.count { ProtoBuf.decodeFromByteArray<TransferPiece>(it.payload.liveAudioChunk!!).status })
    }

    @Test fun lateCompletionOfAnOlderProbeDoesNotShortenTheRetryQueueDeadline() {
        val w = World(); val a = w.Node("A"); w.Node("B")
        a.engine.offer("B", w.payload(), false, durableRelay = true)
        w.queue.clear()
        w.completeFrames = { false }
        a.engine.wake("B")
        val old = w.queue.removeFirst()
        a.engine.frame(old.payload, OutboundFrameEvent.Stage.COMPLETED)
        w.advance(3_100)
        val retry = w.queue.removeFirst()
        w.advance(1_000)
        a.engine.frame(old.payload, OutboundFrameEvent.Stage.COMPLETED)
        w.advance(3_200)
        assertTrue(w.queue.isEmpty())
        a.engine.frame(retry.payload, OutboundFrameEvent.Stage.COMPLETED)
        w.advance(3_100)
        assertEquals(1, w.queue.size)
    }

    @Test fun aSecondNoteGetsAFreedSlotBeforeTheOlderNoteCompletes() {
        val w = World(); val a = w.Node("A"); val b = w.Node("B")
        val held = mutableListOf<Wire>()
        w.drop = { wire -> (wire.payload.type == ReliableMeshTransfers.ACK).also { if (it) held.add(wire) } }
        a.engine.offer("B", w.payload("long", 32_000), false); w.pump()
        repeat(3) { w.advance(100); w.pump() }
        assertEquals(4, held.size)
        val second = w.payload("second", 8192)
        a.engine.offer("B", second, false)
        a.engine.receive("B", held.first().payload)
        val next = w.queue.first()
        assertEquals(TransferJournal.hash(second), ProtoBuf.decodeFromByteArray<TransferPiece>(next.payload.liveAudioChunk!!).transferId)
        w.drop = { false }; w.pump()
        assertEquals(listOf("second", "long"), b.arrived.map { it.id })
    }

    @Test fun staleStatusResponseCannotReplaceTheCurrentReconnectQuery() {
        val w = World(); val a = w.Node("A"); val b = w.Node("B")
        a.engine.offer("B", w.payload(), false)
        w.queue.clear()
        a.engine.wake("B")
        val oldQuery = w.queue.removeFirst()
        b.engine.receive("A", oldQuery.payload)
        val oldResponse = w.queue.removeFirst()
        a.engine.wake("B")
        val newQuery = w.queue.removeFirst()
        a.engine.receive("B", oldResponse.payload)
        assertTrue(w.queue.isEmpty())
        b.engine.receive("A", newQuery.payload)
        w.pump()
        assertEquals(1, b.arrived.size)
    }

    @Test fun latePieceFailureDoesNotExtendTheNewRetryDeadline() {
        val w = World(); val a = w.Node("A"); w.Node("B")
        a.engine.offer("B", w.payload(size = 500), false)
        val old = w.queue.removeFirst()
        w.advance(3_300)
        val retry = w.queue.removeFirst()
        assertNotEquals(old.payload.id, retry.payload.id)
        w.advance(5_000)
        a.engine.frame(old.payload, OutboundFrameEvent.Stage.FAILED)
        assertTrue(w.queue.isEmpty())
        w.advance(1_300)
        assertEquals(1, w.queue.size)
    }

    @Test fun noMoreThanFourPiecesAreOutstandingToOneNeighbor() {
        val w = World(); val a = w.Node("A"); w.Node("B")
        a.engine.offer("B", w.payload(size = 16_000), false)
        w.pump()
        // Start another note, drop all data ACKs, and allow the timer to fill the window.
        w.drop = { it.payload.type == ReliableMeshTransfers.ACK &&
            !ProtoBuf.decodeFromByteArray<TransferPiece>(it.payload.liveAudioChunk!!).status }
        w.sentPieces.clear()
        a.engine.offer("B", w.payload("burst", 16_000), false); w.pump()
        repeat(20) { w.advance(100); w.pump() }
        assertTrue(w.sentPieces.size <= ReliableMeshTransfers.WINDOW)
        assertFalse(a.events.last() == OutboundFrameEvent.Stage.COMPLETED)
    }

    @Test fun receiverRestartKeepsPiecesAndReconnectSendsOnlyMissingIndices() {
        val w = World(); val a = w.Node("A"); val b = w.Node("B")
        val held = mutableListOf<Wire>()
        w.drop = { wire ->
            if (wire.payload.type != ReliableMeshTransfers.PIECE) false else {
                val p = ProtoBuf.decodeFromByteArray<TransferPiece>(wire.payload.liveAudioChunk!!)
                (!p.status && p.index >= 2).also { if (it) held.add(wire) }
            }
        }
        a.engine.offer("B", w.payload(), false); w.pump()
        assertTrue(held.isNotEmpty())
        b.online = false
        w.Node("B", b.root)
        w.drop = { false }; w.queue.clear(); w.sentPieces.clear()
        a.engine.wake("B"); w.pump()
        assertTrue(w.sentPieces.all { it.second >= 2 })
        assertEquals(1, w.nodes.getValue("B").arrived.size)
    }

    @Test fun senderRestartQueriesReceiverInsteadOfResendingAcknowledgedPieces() {
        val w = World(); val a = w.Node("A"); w.Node("B")
        w.drop = { wire -> wire.payload.type == ReliableMeshTransfers.PIECE &&
            ProtoBuf.decodeFromByteArray<TransferPiece>(wire.payload.liveAudioChunk!!).let { !it.status && it.index >= 2 } }
        a.engine.offer("B", w.payload(), false, durableRelay = true); w.pump()
        a.online = false
        w.drop = { false }; w.queue.clear(); w.sentPieces.clear()
        val restarted = w.Node("A", a.root)
        w.pump()
        assertTrue(w.sentPieces.all { it.second >= 2 })
        assertTrue(restarted.events.contains(OutboundFrameEvent.Stage.COMPLETED))
    }

    @Test fun relayCustodySurvivesLongOutageAndManagerRestart() {
        val w = World(); val a = w.Node("A"); val b = w.Node("B", online = false)
        a.engine.offer("B", w.payload(), false, durableRelay = true)
        w.advance(120_000); w.pump()
        assertFalse(a.events.contains(OutboundFrameEvent.Stage.FAILED))
        a.online = false; b.online = true
        val restarted = w.Node("A", a.root); w.pump()
        assertEquals(1, b.arrived.size)
        assertTrue(restarted.events.contains(OutboundFrameEvent.Stage.COMPLETED))
    }

    @Test fun ordinaryQueueStillExpiresWhenNoWireStarts() {
        val w = World(); val a = w.Node("A"); w.Node("B", online = false)
        a.engine.offer("B", w.payload(), false)
        w.advance(30_000); w.pump()
        assertEquals(OutboundFrameEvent.Stage.FAILED, a.events.last())
    }

    @Test fun custodyExpiresExplicitlyAfterTwentyFourHours() {
        val w = World(); val a = w.Node("A"); w.Node("B", online = false)
        a.engine.offer("B", w.payload(), false, durableRelay = true)
        w.advance(ReliableMeshTransfers.RETENTION_MS); w.pump()
        assertEquals(OutboundFrameEvent.Stage.FAILED, a.events.last())
        assertTrue(a.logs.any { it.startsWith("TRANSFER_EXPIRED") })
    }

    @Test fun corruptPieceCannotCompleteAudioAndResetCanRepairIt() {
        val w = World(); val a = w.Node("A"); val b = w.Node("B")
        var corrupted = false
        w.drop = { wire ->
            if (!corrupted && wire.payload.type == ReliableMeshTransfers.PIECE) {
                val p = ProtoBuf.decodeFromByteArray<TransferPiece>(wire.payload.liveAudioChunk!!)
                if (!p.status && p.index == 0) {
                    corrupted = true
                    val bad = p.copy(bytes = p.bytes.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() })
                    b.engine.receive("A", wire.payload.copy(liveAudioChunk = ProtoBuf.encodeToByteArray(bad)))
                    true
                } else false
            } else false
        }
        a.engine.offer("B", w.payload(), false); w.pump()
        assertTrue(b.logs.contains("TRANSFER_HASH_FAILED"))
        assertEquals(1, b.arrived.size)
        assertTrue(a.events.contains(OutboundFrameEvent.Stage.COMPLETED))
    }

    @Test fun duplicateCommittedTransferDoesNotPresentSecondNote() {
        val w = World(); val a = w.Node("A"); val b = w.Node("B")
        val bytes = w.payload()
        a.engine.offer("B", bytes, false); w.pump()
        a.engine.offer("B", bytes, false); w.pump()
        assertEquals(1, b.arrived.size)
        assertEquals(2, a.events.count { it == OutboundFrameEvent.Stage.COMPLETED })
    }

    @Test fun incompleteApplicationCommitIsReplayedAfterReceiverRestart() {
        val w = World(); val a = w.Node("A"); val b = w.Node("B")
        b.autoStore = false
        a.engine.offer("B", w.payload(), false); w.pump()
        assertEquals(1, b.arrived.size)
        b.online = false
        val restarted = w.Node("B", b.root)
        restarted.engine.wake("A"); w.pump()
        assertEquals(1, restarted.arrived.size)
    }

    @Test fun storedPrivateReceiptCanBeReplayedOnDuplicateTransfer() {
        val w = World(); val a = w.Node("A"); val b = w.Node("B")
        val bytes = w.payload()
        a.engine.offer("B", bytes, false); w.pump()
        b.engine.stored("note", ProtoBuf.encodeToByteArray(MeshPayload(id = "receipt", type = "DELIVERED")))
        a.engine.offer("B", bytes, false); w.pump()
        assertTrue(b.logs.contains("RECEIPT_REPLAY"))
    }

    @Test fun wrongPeerAckCannotReleaseCustody() {
        val w = World(); val a = w.Node("A"); w.Node("B")
        val bytes = w.payload()
        val id = TransferJournal.hash(bytes)
        w.drop = { true }
        a.engine.offer("B", bytes, false)
        a.engine.receive("C", MeshPayload(type = ReliableMeshTransfers.ACK,
            liveAudioChunk = ProtoBuf.encodeToByteArray(TransferPiece(id, id, bytes.size, 0, complete = true))))
        assertFalse(a.events.contains(OutboundFrameEvent.Stage.COMPLETED))
    }

    @Test fun publicForwardingRosterAndAcceptedPeersSurviveRelayRestart() {
        val w = World(); val a = w.Node("A"); val b = w.Node("B")
        w.Node("C", online = false); w.Node("D", online = false)
        b.autoStore = false
        val bytes = w.payload()
        a.engine.offer("B", bytes, false); w.pump()
        assertEquals(setOf("C", "D"), b.engine.relayTargets("note", setOf("C", "D")))
        b.engine.offer("C", bytes, false, durableRelay = true)
        b.engine.stored("note")
        assertTrue(b.engine.hasPendingCustody("note"))
        b.online = false
        val restarted = w.Node("B", b.root)
        assertEquals(setOf("C", "D"), restarted.engine.relayTargets("note", setOf("E")))
        assertTrue(restarted.engine.alreadyForwarded("note", "C"))
        assertTrue(restarted.engine.hasPendingCustody("note"))
        restarted.engine.offer("D", bytes, false, durableRelay = true)
        restarted.engine.stored("note")
        assertFalse(restarted.engine.hasPendingCustody("note"))
    }

    @Test fun twoRelayPathCompletesWithoutSkippingCustody() {
        val w = World(); val a = w.Node("A"); val b = w.Node("B"); val c = w.Node("C"); val d = w.Node("D")
        b.autoStore = false; c.autoStore = false
        b.onward = { b.engine.offer("C", it, false, durableRelay = true) }
        c.onward = { c.engine.offer("D", it, false, durableRelay = true) }
        a.engine.offer("B", w.payload(), false); w.pump()
        assertEquals(1, d.arrived.size)
        assertTrue(a.events.contains(OutboundFrameEvent.Stage.COMPLETED))
        assertTrue(b.events.contains(OutboundFrameEvent.Stage.COMPLETED))
        assertTrue(c.events.contains(OutboundFrameEvent.Stage.COMPLETED))
    }

    @Test fun receiverStorageFailureNeverAcknowledgesCompleteCustody() {
        val w = World(); val a = w.Node("A"); val b = w.Node("B", temporary.newFile())
        a.engine.offer("B", w.payload(), false); w.pump()
        assertTrue(b.logs.any { it == "TRANSFER_STORAGE_FAILED direction=inbound" })
        assertTrue(b.arrived.isEmpty())
        assertFalse(a.events.contains(OutboundFrameEvent.Stage.COMPLETED))
    }

    @Test fun applicationCommitBeforeLastRelayCommitReleasesCustodyWhenThatRelayCommits() {
        val w = World(); val a = w.Node("A"); val b = w.Node("B")
        w.Node("C", online = false); b.autoStore = false
        val bytes = w.payload()
        a.engine.offer("B", bytes, false); w.pump()
        b.engine.relayTargets("note", setOf("C"))
        b.engine.stored("note")
        assertTrue(b.engine.hasPendingCustody("note"))
        b.engine.offer("C", bytes, false, durableRelay = true)
        assertFalse(b.engine.hasPendingCustody("note"))
        assertTrue(b.engine.hasOutboundTo("C"))
    }

    @Test fun legacyRelayCompletionReleasesStoredNoteWithoutInventingRecipientReceipt() {
        val w = World(); val a = w.Node("A"); val b = w.Node("B")
        b.autoStore = false
        val bytes = w.payload()
        a.engine.offer("B", bytes, false); w.pump()
        b.engine.relayTargets("note", setOf("legacy"))
        b.engine.stored("note")
        assertTrue(b.engine.hasPendingCustody("note"))
        b.engine.legacyForwarded("legacy", ProtoBuf.decodeFromByteArray(bytes))
        assertFalse(b.engine.hasPendingCustody("note"))
        assertFalse(b.arrived.any { it.type == "DELIVERED" })
    }

    @Test fun retainedTransferExpiresWhileNodeRemainsOffline() {
        val w = World(); val a = w.Node("A"); w.Node("B", online = false)
        a.engine.offer("B", w.payload(), false, durableRelay = true)
        a.online = false; a.engine.pause()
        w.advance(100); w.advance(ReliableMeshTransfers.RETENTION_MS)
        assertFalse(a.engine.hasOutbound("note"))
        assertTrue(a.events.contains(OutboundFrameEvent.Stage.FAILED))
        assertTrue(w.queue.isEmpty())
    }

    @Test fun fastRoomCommitCannotReleasePublicCustodyBeforeRelayRosterExists() {
        val w = World(); val a = w.Node("A"); val b = w.Node("B")
        w.Node("C", online = false)
        val bytes = ProtoBuf.encodeToByteArray(MeshPayload(id = "note", type = "MESSAGE", imageBytes = ByteArray(8192)))
        a.engine.offer("B", bytes, false); w.pump()
        // autoStore simulates Room finishing before the dispatcher begins its broadcast.
        assertTrue(b.engine.hasPendingCustody("note"))
        b.engine.relayTargets("note", setOf("C"))
        assertTrue(b.engine.hasPendingCustody("note"))
        b.engine.offer("C", bytes, false, durableRelay = true)
        assertFalse(b.engine.hasPendingCustody("note"))
        assertTrue(b.engine.hasOutboundTo("C"))
    }

    @Test fun duplicateBroadcastFromDifferentPathReusesCommittedOnwardRoster() {
        val w = World(); val a = w.Node("A"); val b = w.Node("B")
        w.Node("C", online = false)
        val payload = MeshPayload(id = "note", type = "MESSAGE", imageBytes = ByteArray(8192))
        val bytes = ProtoBuf.encodeToByteArray(payload)
        a.engine.offer("B", bytes, false); w.pump()
        b.engine.relayTargets("note", setOf("C"))
        b.engine.offer("C", bytes, false, durableRelay = true)
        assertFalse(b.engine.hasPendingCustody("note"))
        a.engine.offer("B", ProtoBuf.encodeToByteArray(payload.copy(routePath = listOf("other path"))), false)
        w.pump()
        assertTrue(b.engine.hasPendingCustody("note"))
        assertEquals(setOf("C"), b.engine.relayTargets("note", setOf("D")))
        assertFalse(b.engine.hasPendingCustody("note"))
        assertTrue(b.engine.hasOutboundTo("C"))
    }
}
