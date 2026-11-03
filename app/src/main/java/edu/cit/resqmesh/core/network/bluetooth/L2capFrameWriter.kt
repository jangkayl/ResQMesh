package edu.cit.resqmesh.core.network.bluetooth

import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * Writes one length-prefixed frame with the 4-byte header in the same write as the first chunk.
 * Each L2CAP socket write becomes its own SDU, so a separate header write costs extra packets and
 * credits; the wire bytes are unchanged, so [L2capFrameReader] and older builds read it as before.
 */
object L2capFrameWriter {
    fun write(
        output: OutputStream,
        payload: ByteArray,
        chunkBytes: Int = L2capFrameReader.IO_CHUNK_BYTES,
        beforeChunk: () -> Unit = {},
        afterChunk: () -> Unit = {}
    ) {
        require(chunkBytes > Int.SIZE_BYTES)
        val firstCount = minOf(chunkBytes - Int.SIZE_BYTES, payload.size)
        val first = ByteBuffer.allocate(Int.SIZE_BYTES + firstCount)
            .putInt(payload.size)
            .put(payload, 0, firstCount)
            .array()
        beforeChunk()
        output.write(first)
        afterChunk()
        var offset = firstCount
        while (offset < payload.size) {
            beforeChunk()
            val count = minOf(chunkBytes, payload.size - offset)
            output.write(payload, offset, count)
            offset += count
            afterChunk()
        }
        output.flush()
    }
}
