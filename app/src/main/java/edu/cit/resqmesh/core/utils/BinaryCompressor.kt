package edu.cit.resqmesh.core.utils

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

    const val MAX_MEDIA_BYTES = 2 * 1024 * 1024

    sealed interface BoundedResult {
        class Ok(val bytes: ByteArray) : BoundedResult
        /** Output exceeded the cap; [bytes] is the amount inflated when aborted. */
        class TooLarge(val bytes: Int) : BoundedResult
        class Corrupt(val bytes: Int, val cause: String? = null) : BoundedResult
    }

    /** Inflates in chunks, aborting once output exceeds [maxOutputBytes]; never throws on bad input. */
    fun decompressBounded(compressedData: ByteArray, maxOutputBytes: Int = MAX_MEDIA_BYTES): BoundedResult {
        require(maxOutputBytes >= 0)
        val bos = ByteArrayOutputStream()
        var exceeded: Int? = null
        var ioError: java.io.IOException? = null
        try {
            GZIPInputStream(ByteArrayInputStream(compressedData)).use {
                exceeded = inflateInto(it, bos, maxOutputBytes)
            }
        } catch (e: java.io.IOException) {
            ioError = e
        }
        val over = exceeded
        return when {
            ioError != null -> BoundedResult.Corrupt(bos.size(), ioError.javaClass.simpleName)
            over != null -> BoundedResult.TooLarge(over)
            else -> BoundedResult.Ok(bos.toByteArray())
        }
    }

    /** Copies [input] into [bos]; returns the would-be size when the cap is exceeded, else null. */
    private fun inflateInto(input: java.io.InputStream, bos: ByteArrayOutputStream, maxOutputBytes: Int): Int? {
        val buffer = ByteArray(8192)
        var count = input.read(buffer)
        while (count >= 0) {
            if (count > maxOutputBytes - bos.size()) return bos.size() + count
            bos.write(buffer, 0, count)
            count = input.read(buffer)
        }
        return null
    }
}
