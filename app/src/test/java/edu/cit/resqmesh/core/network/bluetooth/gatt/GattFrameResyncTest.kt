package edu.cit.resqmesh.core.network.bluetooth.gatt

import edu.cit.resqmesh.core.network.bluetooth.state.MeshFrameCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GattFrameResyncTest {
    private fun bufferedWithTail(): MeshFrameCodec.Reassembler = MeshFrameCodec.Reassembler().also {
        // A complete 1-byte frame plus 5 bytes of a following frame that is still incomplete.
        it.append(MeshFrameCodec.encode(byteArrayOf(1))!! + MeshFrameCodec.encode(ByteArray(40))!!.copyOf(5))
    }

    @Test fun decodeFailureClearsBufferedTailAndStillForwardsFrame() {
        val buffer = bufferedWithTail()
        val tail = buffer.size
        val dropped = mutableListOf<Int>()
        val processed = mutableListOf<ByteArray>()
        GattFrameResync.deliver(
            buffer, byteArrayOf(9), { processed += it }, decodes = { false }, log = { dropped += it }
        )
        assertTrue(buffer.isEmpty())
        assertEquals(listOf(tail), dropped)
        assertEquals(1, processed.size)
    }

    @Test fun handlerExceptionDoesNotClearBufferedTail() {
        val buffer = bufferedWithTail()
        val tail = buffer.size
        val dropped = mutableListOf<Int>()
        runCatching {
            GattFrameResync.deliver(
                buffer, byteArrayOf(9), { error("handler failed") }, decodes = { true }, log = { dropped += it }
            )
        }
        assertEquals(tail, buffer.size)
        assertTrue(dropped.isEmpty())
    }
}
