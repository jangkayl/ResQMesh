package com.example.testresqmesh.core.utils

import android.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class JvmBase64Test {
    @Test fun cryptoVectorsUseUnwrappedEncodingAndDecodeBothOverloads() {
        val plain = "foobar".toByteArray()
        assertEquals("Zm9vYmFy", Base64.encodeToString(plain, Base64.NO_WRAP))
        assertArrayEquals(plain, Base64.decode("Zm9v\nYmFy", Base64.DEFAULT))
        assertArrayEquals(plain, Base64.decode("Zm9vYmFy".toByteArray(), Base64.DEFAULT))
    }

    @Test fun defaultWrapsAt76CharactersAndSupportsCrLf() {
        val input = ByteArray(60) { 'a'.code.toByte() }
        val encoded = "YWFh".repeat(20)
        assertEquals(encoded.take(76) + "\n" + encoded.drop(76) + "\n", Base64.encodeToString(input, Base64.DEFAULT))
        assertEquals(encoded.take(76) + "\r\n" + encoded.drop(76) + "\r\n", Base64.encodeToString(input, Base64.CRLF))
    }

    @Test fun urlSafeAndUnpaddedVectorsRemainDistinctFromStandardEncoding() {
        val input = byteArrayOf(0xfb.toByte(), 0xff.toByte())
        assertEquals("+/8=", Base64.encodeToString(input, Base64.NO_WRAP))
        assertEquals("-_8", Base64.encodeToString(input, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
        assertArrayEquals(input, Base64.decode("-_8", Base64.URL_SAFE))
    }
}
