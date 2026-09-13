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

    fun decompress(compressedData: ByteArray): ByteArray {
        val bis = ByteArrayInputStream(compressedData)
        val bos = ByteArrayOutputStream()
        GZIPInputStream(bis).use { it.copyTo(bos) }
        return bos.toByteArray()
    }
}
