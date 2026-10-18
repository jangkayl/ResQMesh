package edu.cit.resqmesh.core.network.bluetooth

import edu.cit.resqmesh.core.network.bluetooth.state.MeshFrameCodec
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class L2capInboundTest {
    @Test fun gateCapsFramesAndBytesAndReleases() {
        val gate = L2capInboundGate(maxFrames = 2, maxBytes = 100)
        assertTrue(gate.tryAcquire(40))
        assertTrue(gate.tryAcquire(40))
        assertFalse(gate.tryAcquire(1))
        gate.release(40)
        assertFalse(gate.tryAcquire(61))
        assertTrue(gate.tryAcquire(60))
    }

    @Test fun watchdogOnlyStallsMidFrame() {
        val dog = L2capReadWatchdog(timeoutMs = 100)
        assertFalse(dog.isStalled(10_000))
        dog.frameStarted(0)
        assertFalse(dog.isStalled(100))
        assertTrue(dog.isStalled(101))
        dog.progress(150)
        assertFalse(dog.isStalled(200))
        dog.frameEnded()
        assertFalse(dog.isStalled(10_000))
    }

    @Test fun readerReassemblesPayloadLargerThanInitialBuffer() {
        val payload = ByteArray(100_000) { (it % 251).toByte() }
        val reader = L2capFrameReader(ByteArrayInputStream(MeshFrameCodec.encode(payload)!!))
        val length = (reader.readHeader() as L2capFrameReader.Header.Length).value
        assertEquals(payload.size, length)
        assertArrayEquals(payload, reader.readPayload(length))
    }

    @Test fun truncatedLargeFrameEndsInEofWithoutFullAllocation() {
        val header = byteArrayOf(0x00, 0x20, 0x00, 0x00) // 2 MiB claimed, 3 bytes delivered
        val reader = L2capFrameReader(ByteArrayInputStream(header + byteArrayOf(1, 2, 3)))
        val length = (reader.readHeader() as L2capFrameReader.Header.Length).value
        try {
            reader.readPayload(length)
            fail("expected EOF")
        } catch (_: EOFException) {
        }
    }

    @Test fun invalidLengthIsFlaggedAndSkipKeepsStreamAligned() {
        val tooBig = ByteBuffer.allocate(4).putInt(MeshFrameCodec.MAX_PAYLOAD_BYTES + 1).array()
        assertTrue(L2capFrameReader(ByteArrayInputStream(tooBig)).readHeader() is L2capFrameReader.Header.Invalid)

        val big = MeshFrameCodec.encode(ByteArray(40_000))!!
        val stream = ByteArrayInputStream(big + MeshFrameCodec.encode(byteArrayOf(7))!!)
        val reader = L2capFrameReader(stream)
        val first = (reader.readHeader() as L2capFrameReader.Header.Length).value
        assertTrue(reader.skipPayload(first))
        val second = (reader.readHeader() as L2capFrameReader.Header.Length).value
        assertArrayEquals(byteArrayOf(7), reader.readPayload(second))
    }

    @Test fun trickledBytesReportOneFrameStartAndProgressPerRead() {
        var started = 0
        var progress = 0
        val trickle = object : InputStream() {
            private val data = MeshFrameCodec.encode(ByteArray(10) { 1 })!!
            private var pos = 0
            override fun read(): Int = if (pos < data.size) data[pos++].toInt() and 0xff else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (pos >= data.size) return -1
                b[off] = data[pos++]
                return 1
            }
        }
        val reader = L2capFrameReader(trickle, { started++ }, { progress++ })
        val length = (reader.readHeader() as L2capFrameReader.Header.Length).value
        assertEquals(10, reader.readPayload(length)!!.size)
        assertEquals(1, started)
        assertEquals(11, progress)
    }
}
