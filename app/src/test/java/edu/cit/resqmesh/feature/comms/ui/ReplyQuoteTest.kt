package edu.cit.resqmesh.feature.comms.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ReplyQuoteTest {
    @Test
    fun multiLineSnippetStaysOnOneLine() {
        val s = ReplyQuote.snippet("first\r\nsecond\n\nthird fourth")
        assertEquals("first second third fourth", s)
    }

    @Test
    fun parseRoundTripsPrefixAndBody() {
        val text = ReplyQuote.prefix("Me", ReplyQuote.snippet("a\nb")) + "my reply"
        val (quote, body) = ReplyQuote.parse(text)
        assertEquals("Me: a b", quote)
        assertEquals("my reply", body)
    }

    @Test
    fun emojiAtCutLeavesNoLoneSurrogate() {
        val s = ReplyQuote.snippet("a".repeat(59) + "😀" + "tail")
        assertFalse(s.isNotEmpty() && s.last().isHighSurrogate())
        assertEquals(59, s.length)
    }

    @Test
    fun nonQuoteTextReturnsNullQuote() {
        assertEquals(null to "hello\nworld", ReplyQuote.parse("hello\nworld"))
        assertNull(ReplyQuote.parse("> no newline").first)
    }
}
