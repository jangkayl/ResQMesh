package edu.cit.resqmesh.core.network

import java.io.File
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private typealias World = TransferWorld
private typealias Wire = TransferWire

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
class ReliableMeshTransfersTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun world() = World(temporary)
    private val relayMark = "RELAY_ROSTER"
    private fun imageFrame(route: List<String> = listOf("B#B"), id: String = "note") =
        ProtoBuf.encodeToByteArray(MeshPayload(id = id, type = "MESSAGE", senderName = "Origin#A",
            routePath = route, imageBytes = ByteArray(8192)))
    private fun World.relayed(a: TransferWorld.Node, b: TransferWorld.Node) {
        a.engine.offer("B", imageFrame(emptyList()), false); pump()
        assertEquals(1, b.arrived.size)
    }
    /** B meets C now, and C finishes settling; moving the clock alone leaves held copies for the next wake. */
    private fun World.settle(b: TransferWorld.Node, c: TransferWorld.Node) {
        c.online = true; b.neighbors = setOf("C")
        b.engine.wake("C", reconcile = false)
        time += ReliableMeshTransfers.LATE_RELAY_SETTLE_MS
    }
    private fun World.holdNotes(a: TransferWorld.Node, b: TransferWorld.Node, vararg ids: String) = ids.forEach { id ->
        a.engine.offer("B", imageFrame(emptyList(), id), false); pump()
        b.engine.relayTargets(id, emptySet(), imageFrame(id = id))
        advance(1_000)
    }

    @Test fun newNeighborWaitsToSettleBeforeLateForwardingStarts() {
        val w = world(); val a = w.Node("A"); val b = w.Node("B"); val c = w.Node("C", online = false)
        w.relayed(a, b)
        b.engine.relayTargets("note", emptySet(), imageFrame())
        w.settle(b, c)
        w.time -= 1
        b.engine.wake("C", reconcile = false); w.pump()
        assertTrue(c.arrived.isEmpty())
        assertTrue(b.logs.any { it.contains("C:settling") })
        w.time += 1
        b.engine.wake("C", reconcile = false); w.pump()
        assertEquals(1, c.arrived.size)
    }

    @Test fun neighborThatDropsSettlesAgain() {
        val w = world(); val a = w.Node("A"); val b = w.Node("B"); val c = w.Node("C", online = false)
        w.relayed(a, b)
        b.engine.relayTargets("note", emptySet(), imageFrame())
        w.settle(b, c)
        b.neighbors = emptySet(); b.engine.wake()
        b.neighbors = setOf("C"); b.engine.wake("C", reconcile = false); w.pump()
        assertTrue(c.arrived.isEmpty())
    }

    @Test fun heldCopiesGoOneAtATimeNewestFirst() {
        val w = world(); val a = w.Node("A"); val b = w.Node("B"); val c = w.Node("C", online = false)
        w.holdNotes(a, b, "oldest", "middle", "newest")
        w.settle(b, c)
        repeat(3) { b.engine.wake("C", reconcile = false); w.pump() }
        assertEquals(listOf("newest", "middle", "oldest"), c.arrived.map { it.id })
    }

    @Test fun onlyOneHeldCopyIsOfferedWhileThePeerIsBusy() {
        val w = world(); val a = w.Node("A"); val b = w.Node("B"); val c = w.Node("C", online = false)
        w.holdNotes(a, b, "first", "second")
        w.settle(b, c)
        b.engine.wake("C", reconcile = false)
        assertEquals(1, b.logs.count { it.startsWith("TRANSFER_RETAINED") && it.contains("nextHop=C") })
        assertTrue(b.logs.any { it.contains("C:busy") })
    }

    @Test fun liveTransferFinishesBeforeARunningLateCopyContinues() {
        val w = world(); val a = w.Node("A"); val b = w.Node("B"); val c = w.Node("C", online = false)
        w.relayed(a, b)
        b.engine.relayTargets("note", emptySet(), imageFrame())
        w.settle(b, c)
        b.engine.wake("C", reconcile = false)
        b.engine.offer("C", w.payload("live"), false, durableRelay = true); w.pump()
        assertEquals(listOf("live", "note"), c.arrived.map { it.id })
    }

    @Test fun peerReadyOnlyAfterFirstRelayStillGetsTheImage() {
        val w = world(); val a = w.Node("A"); val b = w.Node("B"); val c = w.Node("C", online = false)
        w.relayed(a, b)
        assertEquals(emptySet<String>(), b.engine.relayTargets("note", emptySet(), imageFrame()))
        assertFalse(b.engine.hasPendingCustody("note"))
        assertTrue(b.engine.hasRelayHold("note"))
        w.settle(b, c)
        b.engine.wake("C", reconcile = false); w.pump()
        assertEquals(listOf("B#B"), c.arrived.single().routePath)
        assertTrue(b.logs.any { it.contains("$relayMark payload=note event=late chosen=C") })
    }

    @Test fun emptyRosterKeepsTheCopyUntilTheWindowEndsThenReleasesIt() {
        val w = world(); val a = w.Node("A"); val b = w.Node("B"); val c = w.Node("C", online = false)
        w.relayed(a, b)
        b.engine.relayTargets("note", emptySet(), imageFrame())
        w.advance(ReliableMeshTransfers.RELAY_HOLD_MS - 10_000)
        assertTrue(b.engine.hasRelayHold("note"))
        w.advance(10_000); w.advance(5_000)
        assertFalse(b.engine.hasRelayHold("note"))
        assertTrue(b.logs.any { it.contains("event=expired") })
        c.online = true; b.neighbors = setOf("C")
        b.engine.wake("C", reconcile = false); w.pump()
        assertTrue(c.arrived.isEmpty())
    }

    @Test fun acceptedPeerSenderAndVisitedPeersAreNeverForwardedAgain() {
        val w = world(); val a = w.Node("A"); val b = w.Node("B"); val c = w.Node("C"); val d = w.Node("D")
        c.onward = { c.engine.relayTargets("note", emptySet()) }
        w.relayed(a, b)
        b.engine.relayTargets("note", setOf("C"), imageFrame(listOf("D#D", "B#B")))
        b.engine.offer("C", imageFrame(listOf("D#D", "B#B")), false, durableRelay = true); w.pump()
        assertEquals(1, c.arrived.size)
        b.neighbors = setOf("A", "C", "D")
        repeat(3) { b.engine.wake(); w.advance(5_000); w.pump() }
        assertEquals(1, c.arrived.size)
        assertTrue(d.arrived.isEmpty())
        assertTrue(b.logs.any { it.contains("A:sender") && it.contains("C:forwarded") && it.contains("D:visited") })
    }

    @Test fun rosterLogRepeatsOnlyWhenTheDecisionChanges() {
        val w = world(); val a = w.Node("A")
        repeat(5) { a.engine.logRoster("note", "first", setOf("B"), mapOf("C" to "not_ready")) }
        a.engine.logRoster("note", "first", setOf("B", "C"), emptyMap())
        assertEquals(2, a.logs.count { it.startsWith(relayMark) })
    }

    @Test fun senderDisconnectAfterCompletionStillDeliversAndForwards() {
        val w = world(); val a = w.Node("A"); val b = w.Node("B"); val c = w.Node("C")
        b.onward = { bytes -> b.engine.relayTargets("note", setOf("C"), bytes)
            b.engine.offer("C", bytes, false, durableRelay = true) }
        b.autoStore = false
        var cut = false
        w.drop = { wire ->
            if (wire.to == "B" && wire.payload.type == ReliableMeshTransfers.PIECE && !cut) {
                // The final piece arrives while the link back to the sender is already gone.
                val piece = ProtoBuf.decodeFromByteArray<TransferPiece>(wire.payload.liveAudioChunk!!)
                if (piece.index == TransferJournal.pieceCount(piece.size) - 1) { a.online = false; cut = true }
            }
            false
        }
        w.relayed(a, b); w.pump()
        assertEquals(1, c.arrived.size)
    }

    @Test fun blockedSenderStaysRefusedUntilTheBlockEnds() {
        val w = world(); val a = w.Node("A"); val b = w.Node("B")
        b.blockedPeers += "A"
        a.engine.offer("B", imageFrame(emptyList()), false); w.pump()
        a.online = false
        b.engine.wake("A"); w.advance(ReliableMeshTransfers.ACK_WAIT_MS)
        assertTrue(b.arrived.isEmpty())
        b.blockedPeers.clear()
        b.engine.wake("A")
        assertEquals(1, b.arrived.size)
    }

    @Test fun restartKeepsTheLateForwardWindowAndAcceptedPeers() {
        val w = world(); val a = w.Node("A"); val b = w.Node("B"); val c = w.Node("C")
        val d = w.Node("D", online = false)
        w.relayed(a, b)
        b.engine.relayTargets("note", setOf("C"), imageFrame())
        b.engine.offer("C", imageFrame(), false, durableRelay = true); w.pump()
        b.online = false
        val restarted = w.Node("B", b.root); w.pump()
        assertTrue(restarted.engine.hasRelayHold("note"))
        assertTrue(restarted.engine.alreadyForwarded("note", "C"))
        d.online = true; restarted.neighbors = setOf("C", "D")
        restarted.engine.wake("D", reconcile = false)
        w.time += ReliableMeshTransfers.LATE_RELAY_SETTLE_MS
        restarted.engine.wake("D", reconcile = false); w.pump()
        assertEquals(1, c.arrived.size)
        assertEquals(1, d.arrived.size)
    }

    @Test fun piecesReassembleExactlyAndCustodyDoesNotInventDeliveryReceipt() {
        val w = world(); val a = w.Node("A"); val b = w.Node("B")
        assertTrue(a.engine.offer("B", w.payload(), false).accepted)
        w.pump()
        assertEquals(1, b.arrived.size)
        assertArrayEquals(ByteArray(8192) { (it % 251).toByte() }, b.arrived.single().audioBytes)
        assertTrue(a.events.contains(OutboundFrameEvent.Stage.COMPLETED))
        assertFalse(b.arrived.any { it.type == "DELIVERED" })
    }

    @Test fun trackedMessageStateStaysBoundedUnderPayloadIdChurn() {
        val w = world(); val a = w.Node("A")
        repeat(3000) { a.engine.relayTargets("payload$it", setOf("B")) }
        // The oldest rosters were evicted, so the first id is recomputed from the current roster.
        assertEquals(setOf("C"), a.engine.relayTargets("payload0", setOf("C")))
        assertEquals(setOf("B"), a.engine.relayTargets("payload2999", setOf("C")))
    }

    @Test fun flooderCannotOpenMoreInboundTransfersThanItsShare() {
        val w = world(); val b = w.Node("B")
        repeat(40) { n ->
            val id = n.toString(16).padStart(64, '0')
            val piece = TransferPiece(id, id, 2048, 0, ByteArray(1024))
            val envelope = MeshPayload(id = "w$n", type = ReliableMeshTransfers.PIECE,
                liveAudioChunk = ProtoBuf.encodeToByteArray(piece))
            b.engine.receive("A", envelope)
        }
        val refused = b.logs.count { it.contains("reason=peer_quota") }
        assertEquals(40 - TransferJournal.MAX_PENDING * TransferJournal.PEER_SHARE_PERCENT / 100, refused)
    }

    @Test fun freshTransferAndFirstReadinessSendDataWithoutAStatusRoundTrip() {
        val w = world(); val a = w.Node("A"); val b = w.Node("B", online = false)
        a.engine.offer("B", w.payload(), false)
        assertTrue(w.queue.isEmpty())
        b.online = true
        a.engine.wake("B", reconcile = false)
        assertFalse(ProtoBuf.decodeFromByteArray<TransferPiece>(w.queue.first().payload.liveAudioChunk!!).status)
        w.pump()
        assertEquals(1, b.arrived.size)
    }

    @Test fun queuedProbeDoesNotRetryEarlyAndDelayedReplySurvivesRetry() {
        val w = world(); val a = w.Node("A"); val b = w.Node("B")
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
        val w = world(); val a = w.Node("A"); w.Node("B")
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
        val w = world(); val a = w.Node("A"); w.Node("B")
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
        val w = world(); val a = w.Node("A"); val b = w.Node("B")
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
        val w = world(); val a = w.Node("A"); val b = w.Node("B")
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
        val w = world(); val a = w.Node("A"); w.Node("B")
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
        val w = world(); val a = w.Node("A"); w.Node("B")
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

    @Test fun silentPeerOnReadyLinkIsReprobedWithoutReconnect() {
        val w = world(); val a = w.Node("A"); val b = w.Node("B")
        fun isStatus(wire: Wire) = wire.payload.type == ReliableMeshTransfers.PIECE &&
            ProtoBuf.decodeFromByteArray<TransferPiece>(wire.payload.liveAudioChunk!!).status
        // Pieces reach B, but every data ACK back to A is lost while the link stays ready.
        w.drop = { it.payload.type == ReliableMeshTransfers.ACK &&
            !ProtoBuf.decodeFromByteArray<TransferPiece>(it.payload.liveAudioChunk!!).status }
        a.engine.offer("B", w.payload(), false); w.pump()
        repeat(95) { w.advance(100); w.pump() }
        assertTrue(w.sent.none(::isStatus))
        repeat(10) { w.advance(100); w.pump() }
        assertTrue(w.sent.any(::isStatus))
        assertTrue(a.logs.any { it.startsWith("TRANSFER_REPROBE") })
        assertTrue(a.progress.any { it.state == SendProgress.State.STALLED && it.reprobes == 1 })
        assertTrue(a.progress.any { it.resent > 0 })
        assertEquals(SendProgress.State.HOP_COMPLETE, a.progress.last().state)
        assertEquals(OutboundFrameEvent.Stage.COMPLETED, a.events.last())
        assertEquals(1, b.arrived.size)
    }

    @Test fun senderProgressCountsAcknowledgedPiecesUpToHopCustody() {
        val w = world(); val a = w.Node("A"); val b = w.Node("B", online = false)
        a.engine.offer("B", w.payload(size = 8192), false)
        assertEquals(SendProgress.State.QUEUED, a.progress.single().state)
        b.online = true
        a.engine.wake("B", reconcile = false); w.pump()
        val sending = a.progress.filter { it.state == SendProgress.State.SENDING }
        assertTrue(sending.isNotEmpty())
        assertEquals(sending.map { it.acked }.sorted(), sending.map { it.acked })
        val done = a.progress.last()
        assertEquals(SendProgress.State.HOP_COMPLETE, done.state)
        assertEquals(done.total, done.acked)
        assertEquals(0, done.resent)
        assertEquals(1, b.arrived.size)
    }

    @Test fun answeringPeerIsNotReprobed() {
        val w = world(); val a = w.Node("A"); w.Node("B")
        a.engine.offer("B", w.payload(size = 64_000), false)
        repeat(200) { w.advance(100); w.pump() }
        assertTrue(a.logs.none { it.startsWith("TRANSFER_REPROBE") })
    }

    @Test fun receiverRestartKeepsPiecesAndReconnectSendsOnlyMissingIndices() {
        val w = world(); val a = w.Node("A"); val b = w.Node("B")
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
        val w = world(); val a = w.Node("A"); w.Node("B")
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
        val w = world(); val a = w.Node("A"); val b = w.Node("B", online = false)
        a.engine.offer("B", w.payload(), false, durableRelay = true)
        w.advance(120_000); w.pump()
        assertFalse(a.events.contains(OutboundFrameEvent.Stage.FAILED))
        a.online = false; b.online = true
        val restarted = w.Node("A", a.root); w.pump()
        assertEquals(1, b.arrived.size)
        assertTrue(restarted.events.contains(OutboundFrameEvent.Stage.COMPLETED))
    }

    @Test fun ordinaryQueueStillExpiresWhenNoWireStarts() {
        val w = world(); val a = w.Node("A"); w.Node("B", online = false)
        a.engine.offer("B", w.payload(), false)
        w.advance(30_000); w.pump()
        assertEquals(OutboundFrameEvent.Stage.FAILED, a.events.last())
    }

    @Test fun custodyExpiresExplicitlyAfterTwentyFourHours() {
        val w = world(); val a = w.Node("A"); w.Node("B", online = false)
        a.engine.offer("B", w.payload(), false, durableRelay = true)
        w.advance(ReliableMeshTransfers.RETENTION_MS); w.pump()
        assertEquals(OutboundFrameEvent.Stage.FAILED, a.events.last())
        assertTrue(a.logs.any { it.startsWith("TRANSFER_EXPIRED") })
    }

    @Test fun corruptPieceCannotCompleteAudioAndResetCanRepairIt() {
        val w = world(); val a = w.Node("A"); val b = w.Node("B")
        var corrupted = false
        w.drop = { wire ->
            if (!corrupted && wire.payload.type == ReliableMeshTransfers.PIECE) {
                val p = ProtoBuf.decodeFromByteArray<TransferPiece>(wire.payload.liveAudioChunk!!)
                if (!p.status && p.index == 0) {
                    corrupted = true
                    // A legacy sender has no CRC, so only the transfer hash can catch this.
                    val bad = p.copy(crc = 0L, hasCrc = false,
                        bytes = p.bytes.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() })
                    b.engine.receive("A", wire.payload.copy(liveAudioChunk = ProtoBuf.encodeToByteArray(bad)))
                    true
                } else false
            } else false
        }
        a.engine.offer("B", w.payload(), false); w.pump()
        assertTrue(b.logs.any { it.startsWith("TRANSFER_HASH_FAILED") })
        assertEquals(1, b.arrived.size)
        assertTrue(a.events.contains(OutboundFrameEvent.Stage.COMPLETED))
        // Only this explicit reset may lower the shown count, and it is flagged as a restart.
        val shown = a.progress.map { it.acked }
        val drop = shown.zipWithNext().indexOfFirst { (before, after) -> after < before }
        assertTrue(drop < 0 || a.progress[drop + 1].restarted)
        assertTrue(a.progress.last().restarted)
    }

    @Test fun duplicateCommittedTransferDoesNotPresentSecondNote() {
        val w = world(); val a = w.Node("A"); val b = w.Node("B")
        val bytes = w.payload()
        a.engine.offer("B", bytes, false); w.pump()
        a.engine.offer("B", bytes, false); w.pump()
        assertEquals(1, b.arrived.size)
        assertEquals(2, a.events.count { it == OutboundFrameEvent.Stage.COMPLETED })
    }

    @Test fun incompleteApplicationCommitIsReplayedAfterReceiverRestart() {
        val w = world(); val a = w.Node("A"); val b = w.Node("B")
        b.autoStore = false
        a.engine.offer("B", w.payload(), false); w.pump()
        assertEquals(1, b.arrived.size)
        b.online = false
        val restarted = w.Node("B", b.root)
        restarted.engine.wake("A"); w.pump()
        assertEquals(1, restarted.arrived.size)
    }

    @Test fun storedPrivateReceiptCanBeReplayedOnDuplicateTransfer() {
        val w = world(); val a = w.Node("A"); val b = w.Node("B")
        val bytes = w.payload()
        a.engine.offer("B", bytes, false); w.pump()
        b.engine.stored("note", ProtoBuf.encodeToByteArray(MeshPayload(id = "receipt", type = "DELIVERED")))
        a.engine.offer("B", bytes, false); w.pump()
        assertTrue(b.logs.contains("RECEIPT_REPLAY"))
    }

    @Test fun wrongPeerAckCannotReleaseCustody() {
        val w = world(); val a = w.Node("A"); w.Node("B")
        val bytes = w.payload()
        val id = TransferJournal.hash(bytes)
        w.drop = { true }
        a.engine.offer("B", bytes, false)
        a.engine.receive("C", MeshPayload(type = ReliableMeshTransfers.ACK,
            liveAudioChunk = ProtoBuf.encodeToByteArray(TransferPiece(id, id, bytes.size, 0, complete = true))))
        assertFalse(a.events.contains(OutboundFrameEvent.Stage.COMPLETED))
    }

    @Test fun publicForwardingRosterAndAcceptedPeersSurviveRelayRestart() {
        val w = world(); val a = w.Node("A"); val b = w.Node("B")
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
        val w = world(); val a = w.Node("A"); val b = w.Node("B"); val c = w.Node("C"); val d = w.Node("D")
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
        val w = world(); val a = w.Node("A"); val b = w.Node("B", temporary.newFile())
        a.engine.offer("B", w.payload(), false); w.pump()
        assertTrue(b.logs.any { it == "TRANSFER_STORAGE_FAILED direction=inbound" })
        assertTrue(b.arrived.isEmpty())
        assertFalse(a.events.contains(OutboundFrameEvent.Stage.COMPLETED))
    }

    @Test fun applicationCommitBeforeLastRelayCommitReleasesCustodyWhenThatRelayCommits() {
        val w = world(); val a = w.Node("A"); val b = w.Node("B")
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
        val w = world(); val a = w.Node("A"); val b = w.Node("B")
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
        val w = world(); val a = w.Node("A"); w.Node("B", online = false)
        a.engine.offer("B", w.payload(), false, durableRelay = true)
        a.online = false; a.engine.pause()
        w.advance(100); w.advance(ReliableMeshTransfers.RETENTION_MS)
        assertFalse(a.engine.hasOutbound("note"))
        assertTrue(a.events.contains(OutboundFrameEvent.Stage.FAILED))
        assertTrue(w.queue.isEmpty())
    }

    @Test fun fastRoomCommitCannotReleasePublicCustodyBeforeRelayRosterExists() {
        val w = world(); val a = w.Node("A"); val b = w.Node("B")
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
        val w = world(); val a = w.Node("A"); val b = w.Node("B")
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
