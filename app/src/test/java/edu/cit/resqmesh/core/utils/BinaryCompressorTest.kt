package edu.cit.resqmesh.core.utils

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class BinaryCompressorTest {
    @Test fun validAttachmentAtLimitRoundTrips() {
        val original = ByteArray(16_384) { (it % 251).toByte() }
        assertArrayEquals(original, BinaryCompressor.decompress(BinaryCompressor.compress(original), original.size))
    }

    @Test fun compressedExpansionBeyondLimitIsRejected() {
        val compressed = BinaryCompressor.compress(ByteArray(1024 * 1024))
        assertThrows(IOException::class.java) { BinaryCompressor.decompress(compressed, 1024) }
    }

    @Test fun boundedRoundTripAndExactLimit() {
        val original = ByteArray(10_000) { (it % 13).toByte() }
        val c = BinaryCompressor.compress(original)
        val ok = BinaryCompressor.decompressBounded(c, original.size) as BinaryCompressor.BoundedResult.Ok
        assertArrayEquals(original, ok.bytes)
        assertTrue(BinaryCompressor.decompressBounded(c, original.size - 1) is BinaryCompressor.BoundedResult.TooLarge)
    }

    @Test fun boundedBombAbortsEarly() {
        val bomb = BinaryCompressor.compress(ByteArray(50 * 1024 * 1024))
        val r = BinaryCompressor.decompressBounded(bomb, BinaryCompressor.MAX_MEDIA_BYTES)
        assertTrue(r is BinaryCompressor.BoundedResult.TooLarge)
        assertTrue((r as BinaryCompressor.BoundedResult.TooLarge).bytes < 3 * 1024 * 1024)
    }

    @Test fun boundedCorruptAndTruncatedAreCorrupt() {
        assertTrue(BinaryCompressor.decompressBounded(byteArrayOf(1, 2, 3)) is BinaryCompressor.BoundedResult.Corrupt)
        val c = BinaryCompressor.compress(ByteArray(100_000) { (it * 31).toByte() })
        assertTrue(BinaryCompressor.decompressBounded(c.copyOf(c.size / 2)) is BinaryCompressor.BoundedResult.Corrupt)
    }

    @Test fun corruptAttachmentIsRejected() {
        assertThrows(IOException::class.java) { BinaryCompressor.decompress(byteArrayOf(1, 2, 3), 1024) }
    }
}
