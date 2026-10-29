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

    @Test fun invalidLengthIsFlagged() {
        val tooBig = ByteBuffer.allocate(4).putInt(MeshFrameCodec.MAX_PAYLOAD_BYTES + 1).array()
        assertTrue(L2capFrameReader(ByteArrayInputStream(tooBig)).readHeader() is L2capFrameReader.Header.Invalid)
    }

    @Test fun fullGateMakesReaderWaitThenProceedsWhenSpaceFrees() {
        val gate = L2capInboundGate(maxFrames = 1, maxBytes = 100)
        assertTrue(gate.tryAcquire(10))
        var result: L2capInboundGate.Admission? = null
        val reader = Thread { result = gate.awaitAcquire(10, 5_000) }
        reader.start()
        Thread.sleep(150)
        assertTrue(reader.isAlive)
        gate.release(10)
        reader.join(2_000)
        assertEquals(L2capInboundGate.Admission.ADMITTED, result)
    }

    @Test fun fullGateTimesOutInsteadOfSkipping() {
        val gate = L2capInboundGate(maxFrames = 1, maxBytes = 100)
        assertTrue(gate.tryAcquire(10))
        assertEquals(L2capInboundGate.Admission.TIMED_OUT, gate.awaitAcquire(10, 100))
    }

    @Test fun oversizeFrameIsAdmittedOnlyWhenQueueIsEmpty() {
        val gate = L2capInboundGate(maxFrames = 4, maxBytes = 100)
        assertEquals(L2capInboundGate.Admission.ADMITTED, gate.awaitAcquire(500, 100))
        assertFalse(gate.tryAcquire(1))
        gate.release(500)
        assertTrue(gate.tryAcquire(1))
    }

    @Test fun closeUnblocksWaitingReader() {
        val gate = L2capInboundGate(maxFrames = 1, maxBytes = 100)
        assertTrue(gate.tryAcquire(10))
        var result: L2capInboundGate.Admission? = null
        val reader = Thread { result = gate.awaitAcquire(10, 30_000) }
        reader.start()
        Thread.sleep(100)
        gate.close()
        reader.join(2_000)
        assertFalse(reader.isAlive)
        assertEquals(L2capInboundGate.Admission.CLOSED, result)
    }

    @Test fun lostOwnershipUnblocksWaitingReader() {
        val gate = L2capInboundGate(maxFrames = 1, maxBytes = 100)
        assertTrue(gate.tryAcquire(10))
        var open = true
        var result: L2capInboundGate.Admission? = null
        val reader = Thread { result = gate.awaitAcquire(10, 30_000) { open } }
        reader.start()
        Thread.sleep(100)
        open = false
        reader.join(2_000)
        assertEquals(L2capInboundGate.Admission.CLOSED, result)
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
