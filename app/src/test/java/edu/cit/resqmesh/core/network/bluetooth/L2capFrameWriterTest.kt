package edu.cit.resqmesh.core.network.bluetooth

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class L2capFrameWriterTest {
    /** Records each write call separately, like an L2CAP socket turning each write into one SDU. */
    private class RecordingStream : OutputStream() {
        val writes = mutableListOf<ByteArray>()
        override fun write(b: Int) { writes += byteArrayOf(b.toByte()) }
        override fun write(b: ByteArray, off: Int, len: Int) { writes += b.copyOfRange(off, off + len) }
        fun bytes(): ByteArray = ByteArrayOutputStream().also { out -> writes.forEach { out.write(it) } }.toByteArray()
    }

    private fun payload(size: Int) = ByteArray(size) { (it % 251).toByte() }

    private fun assertFrame(size: Int, chunkBytes: Int, expectedWrites: Int) {
        val data = payload(size)
        val out = RecordingStream()
        var before = 0
        var after = 0
        L2capFrameWriter.write(out, data, chunkBytes, beforeChunk = { before++ }, afterChunk = { after++ })
        assertEquals(expectedWrites, out.writes.size)
        assertEquals(expectedWrites, before)
        assertEquals(expectedWrites, after)
        out.writes.forEach { assert(it.size <= chunkBytes) }
        val expected = ByteBuffer.allocate(Int.SIZE_BYTES + size).putInt(size).put(data).array()
        assertArrayEquals(expected, out.bytes())
    }

    @Test fun smallFrameIsOneWriteWithHeader() = assertFrame(size = 26, chunkBytes = 64, expectedWrites = 1)

    @Test fun emptyFrameIsHeaderOnly() = assertFrame(size = 0, chunkBytes = 64, expectedWrites = 1)

    @Test fun exactFirstChunkFitsOneWrite() = assertFrame(size = 60, chunkBytes = 64, expectedWrites = 1)

    @Test fun oneByteOverFirstChunkSpills() = assertFrame(size = 61, chunkBytes = 64, expectedWrites = 2)

    @Test fun largeFrameSplitsIntoBoundedChunks() =
        assertFrame(size = 60 + 64 * 3 + 5, chunkBytes = 64, expectedWrites = 5)

    @Test fun defaultChunkRoundTripsThroughReader() {
        val data = payload(100_000)
        val out = RecordingStream()
        L2capFrameWriter.write(out, data)
        assertEquals(Int.SIZE_BYTES + L2capFrameReader.IO_CHUNK_BYTES - Int.SIZE_BYTES, out.writes.first().size)
        val reader = L2capFrameReader(ByteArrayInputStream(out.bytes()))
        val header = reader.readHeader() as L2capFrameReader.Header.Length
        assertArrayEquals(data, reader.readPayload(header.value))
    }
}
