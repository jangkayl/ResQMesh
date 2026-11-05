package edu.cit.resqmesh.core.network.bluetooth.state

import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshFrameCodecTest {
    @Test fun incompleteFrameIsRetainedUntilPayloadCompletes() {
        val frame = MeshFrameCodec.encode(byteArrayOf(1, 2, 3))!!
        val first = MeshFrameCodec.append(ByteArray(0), frame.copyOfRange(0, 5))
            as MeshFrameCodec.AppendResult.Accepted
        assertTrue(first.payloads.isEmpty())
        val second = MeshFrameCodec.append(first.remainder, frame.copyOfRange(5, frame.size))
            as MeshFrameCodec.AppendResult.Accepted
        assertArrayEquals(byteArrayOf(1, 2, 3), second.payloads.single())
        assertTrue(second.remainder.isEmpty())
    }

    @Test fun multipleCompleteFramesAreParsedInOrder() {
        val first = MeshFrameCodec.encode(byteArrayOf(1))!!
        val second = MeshFrameCodec.encode(byteArrayOf(2, 3))!!
        val result = MeshFrameCodec.append(ByteArray(0), first + second)
            as MeshFrameCodec.AppendResult.Accepted
        assertEquals(2, result.payloads.size)
        assertArrayEquals(byteArrayOf(1), result.payloads[0])
        assertArrayEquals(byteArrayOf(2, 3), result.payloads[1])
    }

    @Test fun reassemblerHandlesManySmallChunks() {
        val payload = ByteArray(100_000) { (it % 251).toByte() }
        val frame = MeshFrameCodec.encode(payload)!!
        val reassembler = MeshFrameCodec.Reassembler()
        val out = mutableListOf<ByteArray>()
        frame.asList().chunked(20).forEach { chunk ->
            out += (reassembler.append(chunk.toByteArray()) as MeshFrameCodec.AppendResult.Accepted).payloads
        }
        assertEquals(1, out.size)
        assertArrayEquals(payload, out.single())
        assertTrue(reassembler.isEmpty())
    }

    @Test fun reassemblerSplitsFrameAcrossChunksAndKeepsTrailingBytes() {
        val first = MeshFrameCodec.encode(byteArrayOf(1, 2, 3))!!
        val second = MeshFrameCodec.encode(byteArrayOf(4, 5))!!
        val reassembler = MeshFrameCodec.Reassembler()
        val a = reassembler.append(first + second.copyOfRange(0, 3)) as MeshFrameCodec.AppendResult.Accepted
        assertEquals(1, a.payloads.size)
        assertEquals(3, reassembler.size)
        val b = reassembler.append(second.copyOfRange(3, second.size)) as MeshFrameCodec.AppendResult.Accepted
        assertArrayEquals(byteArrayOf(4, 5), b.payloads.single())
        assertTrue(reassembler.isEmpty())
    }

    @Test fun reassemblerRejectsOversizeLengthAndClears() {
        val reassembler = MeshFrameCodec.Reassembler()
        val header = ByteBuffer.allocate(4).putInt(MeshFrameCodec.MAX_PAYLOAD_BYTES + 1).array()
        assertTrue(reassembler.append(header) is MeshFrameCodec.AppendResult.Rejected)
        assertTrue(reassembler.isEmpty())
        val ok = MeshFrameCodec.encode(byteArrayOf(9))!!
        val accepted = reassembler.append(ok) as MeshFrameCodec.AppendResult.Accepted
        assertArrayEquals(byteArrayOf(9), accepted.payloads.single())
    }

    /**
     * Documents the splice: a lost chunk leaves frame A short, so the reassembler keeps waiting and swallows the
     * head of frame B as A's tail. The result has A's claimed length, so size-only checks cannot tell it from A.
     */
    @Test fun lostChunkSplicesNextFrameIntoCurrentOneAndClearResyncs() {
        val a = ByteArray(40) { 1 }
        // B's tail looks like a plausible 256-byte length prefix, so the leftover stalls instead of rejecting.
        val b = ByteArray(40) { 2 }.also { byteArrayOf(0, 0, 1, 0).copyInto(it, 20) }
        val frameA = MeshFrameCodec.encode(a)!!
        val frameB = MeshFrameCodec.encode(b)!!
        val reassembler = MeshFrameCodec.Reassembler()
        val chunksA = frameA.toList().chunked(20).map { it.toByteArray() }
        assertTrue((reassembler.append(chunksA[0]) as MeshFrameCodec.AppendResult.Accepted).payloads.isEmpty())
        // The rest of A is lost, then frame B follows.
        val spliced = (reassembler.append(frameB) as MeshFrameCodec.AppendResult.Accepted).payloads
        assertEquals(1, spliced.size)
        assertEquals(a.size, spliced.single().size)
        assertTrue(spliced.single().any { it == 2.toByte() } && spliced.single().any { it == 1.toByte() })
        // B's tail is now buffered as a bogus incomplete frame that would swallow the next real one.
        assertEquals(20, reassembler.size)
        reassembler.clear()
        assertTrue(reassembler.isEmpty())
        val next = reassembler.append(MeshFrameCodec.encode(byteArrayOf(7))!!) as MeshFrameCodec.AppendResult.Accepted
        assertArrayEquals(byteArrayOf(7), next.payloads.single())
    }

    @Test fun maxSizedPayloadRoundTripsAndOneMoreByteIsRefused() {
        assertEquals(2 * 1024 * 1024 + 64 * 1024, MeshFrameCodec.MAX_PAYLOAD_BYTES)
        val frame = MeshFrameCodec.encode(ByteArray(MeshFrameCodec.MAX_PAYLOAD_BYTES))!!
        assertEquals(MeshFrameCodec.MAX_FRAME_BYTES, frame.size)
        assertTrue(MeshFrameCodec.encode(ByteArray(MeshFrameCodec.MAX_PAYLOAD_BYTES + 1)) == null)
    }

    @Test fun invalidClaimedLengthIsRejectedBeforeAllocation() {
        val oversizedHeader = ByteBuffer.allocate(Int.SIZE_BYTES)
            .putInt(MeshFrameCodec.MAX_PAYLOAD_BYTES + 1)
            .array()
        assertTrue(MeshFrameCodec.append(ByteArray(0), oversizedHeader) is MeshFrameCodec.AppendResult.Rejected)
        assertTrue(MeshFrameCodec.append(ByteArray(0), ByteBuffer.allocate(4).putInt(-1).array()) is MeshFrameCodec.AppendResult.Rejected)
    }
}
