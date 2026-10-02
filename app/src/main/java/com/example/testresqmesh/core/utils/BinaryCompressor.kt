package com.example.testresqmesh.core.utils

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object BinaryCompressor {
    fun compress(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    fun decompress(compressedData: ByteArray, maxDecodedBytes: Int = Int.MAX_VALUE): ByteArray {
        require(maxDecodedBytes >= 0)
        val bis = ByteArrayInputStream(compressedData)
        val bos = ByteArrayOutputStream()
        GZIPInputStream(bis).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > maxDecodedBytes - bos.size()) throw java.io.IOException("Decoded attachment exceeds limit")
                bos.write(buffer, 0, count)
            }
        }
        return bos.toByteArray()
    }
}
