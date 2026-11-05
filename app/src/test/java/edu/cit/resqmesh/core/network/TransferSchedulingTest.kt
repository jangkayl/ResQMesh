package edu.cit.resqmesh.core.network

import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Sender scheduling policy: origin release/cancel, one live copy per message, oldest media first. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
class TransferSchedulingTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun world() = TransferWorld(temporary)

    @Test fun strandedOriginFlightIsReleasedAfterItsPeerStaysAwayButRelayCustodyIsNot() {
        val w = world(); val a = w.Node("A"); w.Node("B", online = false)
        a.engine.offer("B", w.payload("note"), false, durableRelay = true, origin = true)
        a.engine.offer("B", w.payload("relayed"), false, durableRelay = true)
        assertFalse(a.engine.releaseStranded("note", 30_000))
        w.advance(30_000)
        assertFalse(a.engine.releaseStranded("relayed", 30_000))
        assertTrue(a.engine.releaseStranded("note", 30_000))
        assertFalse(a.engine.hasOutbound("note"))
        assertTrue(a.engine.hasOutbound("relayed"))
        assertTrue(a.logs.any { it.startsWith("TRANSFER_STRANDED") })
        assertEquals(OutboundFrameEvent.Stage.FAILED, a.events.last())
    }

    @Test fun readyPeerNeverCountsAsStranded() {
        val w = world(); val a = w.Node("A"); val b = w.Node("B")
        w.drop = { true }
        a.engine.offer("B", w.payload("note"), false, durableRelay = true, origin = true)
        w.advance(30_000); w.pump()
        assertFalse(a.engine.releaseStranded("note", 30_000))
        assertTrue(a.engine.hasOutbound("note"))
        assertTrue(b.arrived.isEmpty())
    }

    @Test fun cancelOutboundRetiresOnlyTheOriginFlightAndItsJournalRecord() {
        val w = world(); val a = w.Node("A"); w.Node("B", online = false)
        a.engine.offer("B", w.payload("note"), false, durableRelay = true, origin = true)
        a.engine.offer("B", w.payload("relayed"), false, durableRelay = true)
        assertFalse(a.engine.cancelOutbound("relayed"))
        assertTrue(a.engine.cancelOutbound("note"))
        assertFalse(a.engine.hasOutbound("note"))
        assertTrue(a.engine.hasOutbound("relayed"))
        val restarted = w.Node("A", a.root)
        assertFalse(restarted.engine.hasOutbound("note"))
        assertTrue(restarted.engine.hasOutbound("relayed"))
    }

    @Test fun originFlagSurvivesRestartSoAStrandedRecoveredFlightCanBeReleased() {
        val w = world(); val a = w.Node("A"); w.Node("B", online = false)
        a.engine.offer("B", w.payload("note"), false, durableRelay = true, origin = true)
        val restarted = w.Node("A", a.root)
        w.advance(30_000)
        assertTrue(restarted.engine.releaseStranded("note", 30_000))
    }

    private fun messageFrame(id: String, messageId: String, size: Int = 32_000) = ProtoBuf.encodeToByteArray(
        MeshPayload(id = id, type = "MESSAGE", targetMessageId = messageId,
            imageBytes = ByteArray(size) { it.toByte() }))
    /** Transfer ids of data pieces in wire order. */
    private fun TransferWorld.dataPieces() = sent.filter { it.payload.type == ReliableMeshTransfers.PIECE }
        .map { ProtoBuf.decodeFromByteArray<TransferPiece>(it.payload.liveAudioChunk!!) }
        .filterNot { it.status }.map { it.transferId }

    @Test fun newerTransmissionOfAMessageSupersedesTheOlderFlightToThatPeer() {
        val w = world(); val a = w.Node("A"); val b = w.Node("B", online = false)
        a.engine.offer("B", messageFrame("tx1", "m", 8192), false, durableRelay = true)
        a.engine.offer("B", messageFrame("other", "n", 8192), false, durableRelay = true)
        a.engine.offer("B", messageFrame("tx2", "m", 8192), false, durableRelay = true)
        assertTrue(a.logs.any { it.startsWith("TRANSFER_SUPERSEDED") && it.contains("peer=B") })
        assertTrue(a.progress.none { it.state == SendProgress.State.FAILED })
        b.online = true; a.engine.wake("B"); w.pump()
        assertEquals(setOf("other", "tx2"), b.arrived.map { it.id }.toSet())
        assertEquals(2, b.arrived.size)
    }

    @Test fun twoMediaFlightsToOnePeerGoOldestFirst() {
        val w = world(); val a = w.Node("A"); val b = w.Node("B")
        val first = messageFrame("first", "m1"); val second = messageFrame("second", "m2")
        a.engine.offer("B", first, false, durableRelay = true)
        a.engine.offer("B", second, false, durableRelay = true)
        assertTrue(a.progress.any { it.heldBehind && it.state == SendProgress.State.QUEUED })
        w.pump()
        val order = w.dataPieces()
        assertTrue(order.lastIndexOf(TransferJournal.hash(first)) < order.indexOf(TransferJournal.hash(second)))
        assertEquals(listOf("first", "second"), b.arrived.map { it.id })
    }

    @Test fun smallFlightInterleavesWhileMediaHeadIsSending() {
        val w = world(); val a = w.Node("A"); w.Node("B")
        val media = messageFrame("media", "m1"); val small = messageFrame("small", "m2", 4096)
        a.engine.offer("B", media, false, durableRelay = true)
        a.engine.offer("B", small, false, durableRelay = true)
        w.pump()
        val order = w.dataPieces()
        assertTrue(order.indexOf(TransferJournal.hash(small)) < order.lastIndexOf(TransferJournal.hash(media)))
    }

    @Test fun stalledMediaHeadLetsTheNextMediaFlightProceed() {
        val w = world(); val a = w.Node("A"); val b = w.Node("B")
        val first = messageFrame("first", "m1"); val second = messageFrame("second", "m2")
        val firstId = TransferJournal.hash(first)
        // B stores every piece of the first image, but none of its answers reach A.
        w.drop = { it.payload.type == ReliableMeshTransfers.ACK &&
            ProtoBuf.decodeFromByteArray<TransferPiece>(it.payload.liveAudioChunk!!).transferId == firstId }
        a.engine.offer("B", first, false, durableRelay = true)
        a.engine.offer("B", second, false, durableRelay = true)
        w.pump()
        repeat(150) { w.advance(100); w.pump() }
        assertFalse(w.dataPieces().contains(TransferJournal.hash(second)))
        repeat(150) { w.advance(100); w.pump() }
        assertTrue(b.arrived.any { it.id == "second" })
        assertTrue(a.engine.hasOutbound("m1"))
    }

    @Test fun mediaKeepsItsOldestFirstTurnAfterARestart() {
        val w = world(); val a = w.Node("A"); val b = w.Node("B", online = false)
        val ids = listOf("one", "two", "three", "four")
        ids.forEach { id ->
            a.engine.offer("B", messageFrame(id, "m-$id"), false, durableRelay = true); w.time += 1_000
        }
        a.online = false
        val restarted = w.Node("A", a.root)
        b.online = true; restarted.engine.wake("B"); w.pump()
        assertEquals(ids, b.arrived.map { it.id })
    }

    @Test fun mediaHeldAgainGivesBackItsSlotsAndRechecksStatusWhenReleased() {
        val w = world(); val a = w.Node("A"); w.Node("B")
        val first = messageFrame("first", "m1"); val second = messageFrame("second", "m2")
        val firstId = TransferJournal.hash(first); val secondId = TransferJournal.hash(second)
        var blockFirst = true
        // The head loses its tail pieces and every answer until [blockFirst] clears; the second is never answered.
        w.drop = { wire ->
            val piece = ProtoBuf.decodeFromByteArray<TransferPiece>(wire.payload.liveAudioChunk!!)
            val ack = wire.payload.type == ReliableMeshTransfers.ACK
            when (piece.transferId) {
                firstId -> blockFirst && (ack || (!piece.status && piece.index >= 10))
                secondId -> ack
                else -> false
            }
        }
        a.engine.offer("B", first, false, durableRelay = true)
        a.engine.offer("B", second, false, durableRelay = true)
        w.pump()
        repeat(250) { w.advance(100); w.pump() }
        assertTrue(w.dataPieces().contains(secondId))
        blockFirst = false
        repeat(1_000) { w.advance(100); w.pump() }
        assertTrue(a.events.contains(OutboundFrameEvent.Stage.COMPLETED))
        val wires = w.sent.filter { it.payload.type == ReliableMeshTransfers.PIECE }
            .map { ProtoBuf.decodeFromByteArray<TransferPiece>(it.payload.liveAudioChunk!!) }
        val headDone = wires.indexOfLast { it.transferId == firstId && !it.status }
        assertTrue(wires.drop(headDone + 1).first { it.transferId == secondId }.status)
    }
}
