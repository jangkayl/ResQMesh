package edu.cit.resqmesh.core.security.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextPolicyTest {

    private fun assertNoLoneSurrogate(s: String) {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (Character.isHighSurrogate(c)) {
                assertTrue("dangling high surrogate in $s", i + 1 < s.length && Character.isLowSurrogate(s[i + 1]))
                i += 2
                continue
            }
            assertFalse("lone low surrogate in $s", Character.isLowSurrogate(c))
            i++
        }
    }

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

    @Test
    fun firstGraphemeAndSafePrefixNeverSplitSurrogates() {
        val samples = listOf(
            "😀abc", "👨‍👩‍👧x", "🇵🇭flag",
            "a😀", "", "x"
        )
        for (s in samples) {
            assertNoLoneSurrogate(TextPolicy.firstGrapheme(s))
            for (n in 0..s.length + 1) assertNoLoneSurrogate(TextPolicy.safePrefix(s, n))
        }
        assertEquals("", TextPolicy.firstGrapheme(""))
        assertEquals("a", TextPolicy.firstGrapheme("abc"))
        assertEquals("ab", TextPolicy.safePrefix("abc", 2))
        assertEquals("a", TextPolicy.safePrefix("a😀", 2))
    }

    @Test
    fun clampLengthDropsTrailingJoinerAndVariationSelector() {
        assertEquals("ab", TextPolicy.clampLength("ab‍😀", 3))
        assertEquals("ab", TextPolicy.clampLength("ab️cd", 3))
        assertEquals("ab‍", TextPolicy.clampLength("ab‍", 5))
    }

    @Test
    fun emojiSequencesSurviveSanitize() {
        val family = "👨‍👩‍👧"
        val flag = "🇵🇭"
        val keycap = "1️⃣"
        val heart = "❤️"
        for (e in listOf(family, flag, keycap, heart, "😀")) {
            assertEquals(e, TextSanitizer.sanitize(e, TextField.MESSAGE))
        }
    }

    @Test
    fun bidiAndInvisibleCharactersAreRemoved() {
        val input = "a​b‎c‏d؜e﻿f­g‮h⁦i"
        assertEquals("abcdefghi", TextSanitizer.sanitize(input, TextField.MESSAGE))
        assertEquals("a\nb", TextSanitizer.sanitize("a b", TextField.MESSAGE))
        assertEquals("a\nb", TextSanitizer.sanitize("a b", TextField.MESSAGE))
        assertEquals("a b", TextSanitizer.sanitize("a b", TextField.DISPLAY_NAME))
    }

    @Test
    fun invisibleOnlyInputIsBlank() {
        val invisibleOnly = "​‍‌️́ ‮"
        assertEquals(ValidationResult.Blank, TextPolicy.validate(invisibleOnly, TextField.MESSAGE))
        assertEquals(ValidationResult.Blank, TextPolicy.validate("‍‍", TextField.DISPLAY_NAME))
        assertFalse(TextSanitizer.hasVisibleContent("́̂"))
        assertTrue(TextSanitizer.hasVisibleContent("a"))
    }

    @Test
    fun combiningMarkRunsAreCappedAtFour() {
        val zalgo = "a" + "́".repeat(30) + "b" + "😀"
        assertEquals("á" + "́".repeat(4) + "b😀", TextSanitizer.sanitize(zalgo, TextField.MESSAGE))
    }

    @Test
    fun sanitizeIsIdempotent() {
        val samples = listOf(
            "á".repeat(9) + "​#x \n y z", "e​́#­",
            "👨‍👩  \t#n", "﻿  a‮b  ",
            "a" + "́‍".repeat(20), "a\uD800b"
        )
        for (field in TextField.values()) for (s in samples) {
            val once = TextSanitizer.sanitize(s, field)
            assertEquals(once, TextSanitizer.sanitize(once, field))
        }
    }

    @Test
    fun displayNameRejectsEmojiAndSymbols() {
        val name = TextField.DISPLAY_NAME
        assertEquals("Ana", TextSanitizer.sanitize("😀Ana❤️", name))
        assertEquals("1", TextSanitizer.sanitize("1️⃣", name))
        assertEquals("", TextSanitizer.sanitize("🇵🇭", name))
        assertEquals(ValidationResult.Blank, TextPolicy.validate("😀😀", name))
        assertEquals("Ana", TextSanitizer.sanitize("A\$n*a@!", name))
    }

    @Test
    fun displayNameKeepsRealNames() {
        listOf("Juan dela Cruz", "Peña", "O'Brien", "Anne-Marie", "Jose Jr.", "Cruz, Juan", "SM-A536E", "ناصر", "हिन्दी", "สมชาย").forEach {
            assertEquals(it, TextSanitizer.sanitize(it, TextField.DISPLAY_NAME))
        }
    }

    @Test
    fun liveNameFilterKeepsSpacesWhileTyping() {
        assertEquals("Ana ", TextSanitizer.keepNameCharacters("Ana😀 "))
    }

    @Test
    fun emojiStaysAllowedOutsideNames() {
        assertEquals("hi 😀", TextSanitizer.sanitize("hi 😀", TextField.MESSAGE))
    }

    @Test
    fun hashIsDroppedOnlyForDisplayName() {
        assertEquals("ab", TextSanitizer.sanitize("a#b", TextField.DISPLAY_NAME))
        assertEquals("a#b", TextSanitizer.sanitize("a#b", TextField.MESSAGE))
        assertEquals("a#b", TextSanitizer.sanitize("a#b", TextField.INCIDENT_TITLE))
    }

    @Test
    fun inboundSanitizesAndClamps() {
        assertEquals("ab", TextSanitizer.inbound("a‮b", TextField.MESSAGE))
        assertEquals("", TextSanitizer.inbound("​‍", TextField.MESSAGE))
        assertEquals(1000, TextSanitizer.inbound("x".repeat(5000), TextField.MESSAGE).length)
    }

    @Test
    fun joinersDoNotResetMarkRunAndEnclosingMarksCount() {
        val bypass = "a" + "́‍".repeat(20)
        assertEquals(4, TextSanitizer.sanitize(bypass, TextField.MESSAGE).count { it == '́' })
        val enclosing = "a" + "⃣".repeat(10)
        assertEquals(4, TextSanitizer.sanitize(enclosing, TextField.MESSAGE).count { it == '⃣' })
        assertEquals("1️⃣", TextSanitizer.sanitize("1️⃣", TextField.MESSAGE))
    }

    @Test
    fun loneSurrogatesAreStrippedAndFormatCharsAreNotVisible() {
        assertEquals("ab", TextSanitizer.sanitize("a\uD800b\uDC00", TextField.MESSAGE))
        assertEquals("a😀", TextSanitizer.sanitize("a😀\uD83D", TextField.MESSAGE))
        assertFalse(TextSanitizer.hasVisibleContent("⁠⁡\uD800"))
        assertEquals(ValidationResult.Blank, TextPolicy.validate("⁠‍", TextField.MESSAGE))
        assertEquals("a‍b", TextSanitizer.sanitize("a‍b", TextField.MESSAGE))
    }

    @Test
    fun inboundPeerNameFallsBackWhenNameIsInvisible() {
        assertEquals("Unknown Node#AB12", TextSanitizer.inboundPeerName("​‮#AB12"))
        assertEquals("Unknown Node", TextSanitizer.inboundPeerName("​"))
        assertEquals("A B#AB12", TextSanitizer.inboundPeerName("A\nB#AB12"))
    }

    @Test
    fun inboundPeerNameKeepsNodeIdSuffix() {
        assertEquals("Ann#AB12", TextSanitizer.inboundPeerName("A‮n​n#AB12"))
        assertEquals("Ann [RESCUE]#AB12", TextSanitizer.inboundPeerName("Ann [RESCUE]#AB12"))
        assertTrue(TextSanitizer.inboundPeerName("n".repeat(200) + "#AB12").endsWith("#AB12"))
    }
}
