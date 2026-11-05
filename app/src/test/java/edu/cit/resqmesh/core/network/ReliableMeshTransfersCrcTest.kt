package edu.cit.resqmesh.core.network

import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
class ReliableMeshTransfersCrcTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun world() = TransferWorld(temporary)

    @Test fun pieceWithBadCrcIsRejectedWithoutPoisoningAndCleanResendCompletes() {
        val w = world(); val a = w.Node("A"); val b = w.Node("B")
        var corrupted = false
        w.drop = { wire ->
            if (!corrupted && wire.payload.type == ReliableMeshTransfers.PIECE) {
                val p = ProtoBuf.decodeFromByteArray<TransferPiece>(wire.payload.liveAudioChunk!!)
                if (!p.status && p.index == 0) {
                    corrupted = true
                    assertTrue(p.hasCrc)
                    val bad = p.copy(bytes = p.bytes.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() })
                    b.engine.receive("A", wire.payload.copy(liveAudioChunk = ProtoBuf.encodeToByteArray(bad)))
                    true
                } else false
            } else false
        }
        a.engine.offer("B", w.payload(), false); w.pump()
        assertTrue(b.logs.any { it.startsWith("TRANSFER_PIECE_CRC_FAILED transfer=") && " index=0 peer=A" in it })
        repeat(4) { w.advance(31_000); w.pump() }
        assertFalse(b.logs.any { it.startsWith("TRANSFER_HASH_FAILED") })
        assertEquals(1, b.arrived.size)
        assertTrue(a.events.contains(OutboundFrameEvent.Stage.COMPLETED))
    }

    @Test fun pieceWithoutCrcIsStillAccepted() {
        val w = world(); val a = w.Node("A"); val b = w.Node("B")
        w.drop = { wire ->
            if (wire.payload.type == ReliableMeshTransfers.PIECE) {
                val p = ProtoBuf.decodeFromByteArray<TransferPiece>(wire.payload.liveAudioChunk!!)
                val legacy = p.copy(crc = 0L, hasCrc = false)
                b.engine.receive("A", wire.payload.copy(liveAudioChunk = ProtoBuf.encodeToByteArray(legacy)))
                true
            } else false
        }
        a.engine.offer("B", w.payload(), false); w.pump()
        assertEquals(1, b.arrived.size)
        assertFalse(b.logs.any { it.startsWith("TRANSFER_PIECE_CRC_FAILED") })
    }

    @Test fun legacyPieceEncodingRoundTripsWithoutCrc() {
        val legacy = TransferPiece("t1", "dg", 1024, 3, byteArrayOf(1, 2, 3), requestId = "rq")
        val encoded = ProtoBuf.encodeToByteArray(legacy)
        // Defaults are never encoded, so a piece without a CRC is byte-identical to the pre-CRC wire.
        assertArrayEquals(encoded, ProtoBuf.encodeToByteArray(legacy.copy(crc = 0L, hasCrc = false)))
        val decoded = ProtoBuf.decodeFromByteArray<TransferPiece>(encoded)
        assertEquals(legacy.index, decoded.index)
        assertArrayEquals(legacy.bytes, decoded.bytes)
        assertEquals(legacy.requestId, decoded.requestId)
        assertFalse(decoded.hasCrc)
        val withCrc = ProtoBuf.decodeFromByteArray<TransferPiece>(ProtoBuf.encodeToByteArray(legacy.withCrc()))
        assertTrue(withCrc.hasCrc)
        assertEquals(TransferPiece.crcOf(byteArrayOf(1, 2, 3)), withCrc.crc)
    }
}
