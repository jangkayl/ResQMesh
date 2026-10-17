package edu.cit.resqmesh.core.security.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextPolicyTest {

    @Test
    fun stripsControlAndBidiCharacters() {
        val input = "a\u0000b‮c⁦d\u0085e"
        assertEquals("abcde", TextSanitizer.sanitize(input, TextField.MESSAGE))
    }

    @Test
    fun singleLineFieldsCollapseNewlinesAndSpaces() {
        assertEquals("a b c", TextSanitizer.sanitize("a\nb   c", TextField.DISPLAY_NAME))
        assertEquals("a\nb", TextSanitizer.sanitize("a\nb", TextField.MESSAGE))
    }

    @Test
    fun trimsAndReportsBlank() {
        assertEquals(ValidationResult.Blank, TextPolicy.validate(" \n\t ", TextField.MESSAGE))
        assertEquals(ValidationResult.Valid("hi"), TextPolicy.validate("  hi  ", TextField.MESSAGE))
    }

    @Test
    fun enforcesLimits() {
        assertEquals(
            ValidationResult.Valid("a".repeat(32)),
            TextPolicy.validate("a".repeat(32), TextField.DISPLAY_NAME)
        )
        assertEquals(ValidationResult.TooLong(32), TextPolicy.validate("a".repeat(33), TextField.DISPLAY_NAME))
        assertEquals(ValidationResult.TooLong(1000), TextPolicy.validate("a".repeat(1001), TextField.MESSAGE))
        assertEquals(80, TextField.INCIDENT_TITLE.maxLength)
        assertEquals(240, TextField.HELP_NOTE.maxLength)
    }

    @Test
    fun counterShowsNearLimit() {
        assertFalse(TextPolicy.showCounter(899, TextField.MESSAGE))
        assertTrue(TextPolicy.showCounter(900, TextField.MESSAGE))
    }

    @Test
    fun clampLengthDoesNotSplitSurrogatePair() {
        val emoji = "😀"
        val text = "a".repeat(31) + emoji
        assertEquals("a".repeat(31), TextPolicy.clampLength(text, TextField.DISPLAY_NAME))
        assertEquals(text, TextPolicy.clampLength(text, 33))
        assertEquals("abc", TextPolicy.clampLength("abcdef", 3))
    }
}
