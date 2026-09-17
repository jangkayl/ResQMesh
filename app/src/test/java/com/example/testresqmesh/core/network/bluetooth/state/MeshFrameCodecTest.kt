package com.example.testresqmesh.core.network.bluetooth.state

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

    @Test fun invalidClaimedLengthIsRejectedBeforeAllocation() {
        val oversizedHeader = ByteBuffer.allocate(Int.SIZE_BYTES)
            .putInt(MeshFrameCodec.MAX_PAYLOAD_BYTES + 1)
            .array()
        assertTrue(MeshFrameCodec.append(ByteArray(0), oversizedHeader) is MeshFrameCodec.AppendResult.Rejected)
        assertTrue(MeshFrameCodec.append(ByteArray(0), ByteBuffer.allocate(4).putInt(-1).array()) is MeshFrameCodec.AppendResult.Rejected)
    }
}
