package edu.cit.resqmesh.core.security.input

import java.text.BreakIterator
import java.text.Normalizer

private const val MESSAGE_MAX = 1000
private const val DISPLAY_NAME_MAX = 32
private const val INCIDENT_TITLE_MAX = 80
private const val INCIDENT_DESCRIPTION_MAX = 2000
private const val INCIDENT_AREA_MAX = 120
private const val HELP_NOTE_MAX = 240
private const val SEARCH_MAX = 100

private const val COUNTER_THRESHOLD_DIVISOR = 10
private const val BIDI_EMBED_START = 0x202A
private const val BIDI_EMBED_END = 0x202E
private const val BIDI_ISOLATE_START = 0x2066
private const val BIDI_ISOLATE_END = 0x2069
private const val ASCII_CONTROL_LIMIT = 0x20
private const val DEL_CODE = 0x7F
private const val C1_CONTROL_END = 0x9F
private const val MIN_COLLAPSE_RUN = 2
private const val ZWSP = 0x200B
private const val LRM = 0x200E
private const val RLM = 0x200F
private const val ALM = 0x061C
private const val BOM = 0xFEFF
private const val SOFT_HYPHEN = 0x00AD
private const val LINE_SEPARATOR = 0x2028
private const val PARAGRAPH_SEPARATOR = 0x2029
private const val LINE_FEED = 0x0A
private const val TAB = 0x09
private const val ZWNJ = 0x200C
private const val TAG_START = 0xE0000
private const val TAG_END = 0xE007F
private const val MAX_COMBINING_RUN = 4
private const val PEER_HEAD_MAX = 48
private const val PEER_FALLBACK_NAME = "Unknown Node"
private const val NAME_PUNCTUATION = " .,'-’"
private const val VARIATION_SELECTOR_START = 0xFE00
private const val ZWJ = '‍'
private const val VARIATION_SELECTOR_16 = '️'

/** User-entered text fields that share one length and character policy across UI and domain layers. */
enum class TextField(val maxLength: Int, val singleLine: Boolean) {
    MESSAGE(MESSAGE_MAX, false),
    DISPLAY_NAME(DISPLAY_NAME_MAX, true),
    INCIDENT_TITLE(INCIDENT_TITLE_MAX, true),
    INCIDENT_DESCRIPTION(INCIDENT_DESCRIPTION_MAX, false),
    INCIDENT_AREA(INCIDENT_AREA_MAX, true),
    HELP_NOTE(HELP_NOTE_MAX, false),
    SEARCH(SEARCH_MAX, true)
}

sealed interface ValidationResult {
    data class Valid(val value: String) : ValidationResult
    data object Blank : ValidationResult
    data class TooLong(val max: Int) : ValidationResult
}

/** Character classification helpers for [TextSanitizer]. */
internal object CharClass {
    private fun isBidiControl(c: Int) =
        c in BIDI_EMBED_START..BIDI_EMBED_END || c in BIDI_ISOLATE_START..BIDI_ISOLATE_END

    /** Zero-width and directional marks that render nothing; ZWJ, ZWNJ, FE0F and tag characters are kept for emoji. */
    private fun isInvisibleFormat(c: Int) =
        c == ZWSP || c == LRM || c == RLM || c == ALM || c == BOM || c == SOFT_HYPHEN

    fun isCleanOut(c: Int) = c < ASCII_CONTROL_LIMIT || c in DEL_CODE..C1_CONTROL_END ||
        isBidiControl(c) || isInvisibleFormat(c)

    /** Letters of any language with their marks, digits, spaces and common name punctuation; no emoji or symbols. */
    fun isNameCharacter(c: Int): Boolean {
        if (c in VARIATION_SELECTOR_START..VARIATION_SELECTOR_16.code) return false
        val type = Character.getType(c)
        return Character.isLetterOrDigit(c) || String(Character.toChars(c)) in NAME_PUNCTUATION ||
            type == Character.NON_SPACING_MARK.toInt() || type == Character.COMBINING_SPACING_MARK.toInt()
    }

    fun isInvisibleType(type: Int) =
        type == Character.NON_SPACING_MARK.toInt() || type == Character.ENCLOSING_MARK.toInt() ||
            type == Character.FORMAT.toInt() || type == Character.SURROGATE.toInt() ||
            type == Character.CONTROL.toInt() || type == Character.UNASSIGNED.toInt()

    fun isInvisibleGlyph(c: Int): Boolean =
        Character.isWhitespace(c) || Character.isSpaceChar(c) || c == ZWJ.code || c == ZWNJ ||
            c == VARIATION_SELECTOR_16.code || c in TAG_START..TAG_END || isCleanOut(c) ||
            isInvisibleType(Character.getType(c))
}

object TextSanitizer {
    /** Live filter for the name field. Unlike [sanitize] it keeps spaces at the ends so typing is not disturbed. */
    fun keepNameCharacters(input: String): String {
        val sb = StringBuilder(input.length)
        input.codePoints().forEach { if (CharClass.isNameCharacter(it)) sb.appendCodePoint(it) }
        return sb.toString()
    }

    private fun cleaned(input: String, singleLine: Boolean, nameOnly: Boolean): String {
        val sb = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val c = input.codePointAt(i)
            i += Character.charCount(c)
            when {
                c == LINE_FEED || c == LINE_SEPARATOR || c == PARAGRAPH_SEPARATOR ->
                    sb.append(if (singleLine) ' ' else '\n')
                c == TAB -> sb.append(' ')
                CharClass.isCleanOut(c) -> Unit
                c in Character.MIN_SURROGATE.code..Character.MAX_SURROGATE.code -> Unit // lone surrogate
                nameOnly && !CharClass.isNameCharacter(c) -> Unit
                else -> sb.appendCodePoint(c)
            }
        }
        return capCombiningMarks(Normalizer.normalize(sb, Normalizer.Form.NFC))
    }

    private fun capCombiningMarks(text: String): String {
        val sb = StringBuilder(text.length)
        var run = 0
        var i = 0
        while (i < text.length) {
            val c = text.codePointAt(i)
            i += Character.charCount(c)
            val type = Character.getType(c)
            // Joiners do not reset the run, otherwise "mark ZWJ mark ZWJ ..." would bypass the cap.
            val joiner = c == VARIATION_SELECTOR_16.code || c == ZWJ.code || c == ZWNJ
            val mark = !joiner &&
                (type == Character.NON_SPACING_MARK.toInt() || type == Character.ENCLOSING_MARK.toInt())
            run = when {
                joiner -> run
                mark -> run + 1
                else -> 0
            }
            if (mark && run > MAX_COMBINING_RUN) continue
            sb.appendCodePoint(c)
        }
        return sb.toString()
    }

    fun sanitize(input: String, field: TextField): String {
        var out = cleaned(input, field.singleLine, nameOnly = field == TextField.DISPLAY_NAME)
        if (field.singleLine) out = out.replace(Regex(" {$MIN_COLLAPSE_RUN,}"), " ")
        return out.trim()
    }

    /** Peer-supplied display name: control/bidi/invisible removal and a clamp, keeping '#' so `#NODEID` survives. */
    fun inboundPeerName(name: String): String {
        val out = cleaned(name, singleLine = true, nameOnly = false)
            .replace(Regex(" {$MIN_COLLAPSE_RUN,}"), " ")
            .trim()
        val hash = out.lastIndexOf('#')
        val head = TextPolicy.clampLength(if (hash < 0) out else out.substring(0, hash), PEER_HEAD_MAX).trim()
        val suffix = if (hash < 0) "" else out.substring(hash)
        // Only the human part is clamped; the `#NODEID` suffix must never be cut.
        return (if (hasVisibleContent(head)) head else PEER_FALLBACK_NAME) + suffix
    }

    /** Cleans text received from another phone for the local copy only; may return "". */
    fun inbound(input: String, field: TextField): String =
        TextPolicy.clampLength(sanitize(input, field), field.maxLength)
            .trim()
            .takeIf { hasVisibleContent(it) }
            .orEmpty()

    /** True when at least one visible glyph remains (not only ZWJ/ZWNJ/FE0F/combining marks/whitespace). */
    fun hasVisibleContent(text: String): Boolean {
        return text.codePoints().anyMatch { !CharClass.isInvisibleGlyph(it) }
    }
}

object TextPolicy {
    fun maxLength(field: TextField): Int = field.maxLength

    /** Sanitizes then checks the limit; the length is measured after sanitizing. */
    fun validate(input: String, field: TextField): ValidationResult {
        val clean = TextSanitizer.sanitize(input, field)
        return when {
            !TextSanitizer.hasVisibleContent(clean) -> ValidationResult.Blank
            clean.length > field.maxLength -> ValidationResult.TooLong(field.maxLength)
            else -> ValidationResult.Valid(clean)
        }
    }

    /** True once length is within 10% of the limit (used for live UI counters). */
    fun showCounter(length: Int, field: TextField): Boolean =
        length >= field.maxLength - field.maxLength / COUNTER_THRESHOLD_DIVISOR

    /** Truncates to the field limit without leaving a dangling high surrogate. */
    fun clampLength(text: String, field: TextField): String = clampLength(text, field.maxLength)

    fun clampLength(text: String, max: Int): String {
        if (text.length <= max) return text
        var cut = safePrefix(text, max)
        while (cut.isNotEmpty() && (cut.last() == ZWJ || cut.last() == VARIATION_SELECTOR_16)) {
            cut = cut.dropLast(1)
        }
        return cut
    }

    /** UTF-16 prefix of at most [maxChars] chars that never ends on a high surrogate. */
    fun safePrefix(text: String, maxChars: Int): String {
        if (maxChars <= 0) return ""
        val cut = text.take(maxChars)
        return if (text.length > maxChars && cut.last().isHighSurrogate()) cut.dropLast(1) else cut
    }

    /** First user-perceived character (grapheme cluster) of [text], or "" when empty. */
    fun firstGrapheme(text: String): String {
        if (text.isEmpty()) return ""
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        val end = it.next()
        return if (end == BreakIterator.DONE) text else text.substring(0, end)
    }
}
