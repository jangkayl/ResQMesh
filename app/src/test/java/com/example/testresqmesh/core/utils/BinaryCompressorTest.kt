package com.example.testresqmesh.core.utils

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

    @Test fun corruptAttachmentIsRejected() {
        assertThrows(IOException::class.java) { BinaryCompressor.decompress(byteArrayOf(1, 2, 3), 1024) }
    }
}
