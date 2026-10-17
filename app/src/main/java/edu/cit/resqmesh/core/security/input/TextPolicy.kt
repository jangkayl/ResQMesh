package edu.cit.resqmesh.core.security.input

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

object TextSanitizer {
    private fun isBidiControl(c: Char) =
        c.code in BIDI_EMBED_START..BIDI_EMBED_END || c.code in BIDI_ISOLATE_START..BIDI_ISOLATE_END

    fun sanitize(input: String, field: TextField): String {
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFC)
        val sb = StringBuilder(normalized.length)
        for (c in normalized) {
            when {
                c == '\n' -> sb.append(if (field.singleLine) ' ' else '\n')
                c == '\t' -> sb.append(' ')
                c.code < ASCII_CONTROL_LIMIT || c.code in DEL_CODE..C1_CONTROL_END -> Unit
                isBidiControl(c) -> Unit
                else -> sb.append(c)
            }
        }
        var out = sb.toString()
        if (field.singleLine) out = out.replace(Regex(" {$MIN_COLLAPSE_RUN,}"), " ")
        return out.trim()
    }
}

object TextPolicy {
    fun maxLength(field: TextField): Int = field.maxLength

    /** Sanitizes then checks the limit; the length is measured after sanitizing. */
    fun validate(input: String, field: TextField): ValidationResult {
        val clean = TextSanitizer.sanitize(input, field)
        return when {
            clean.isEmpty() -> ValidationResult.Blank
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
        val cut = text.take(max)
        return if (cut.isNotEmpty() && cut.last().isHighSurrogate()) cut.dropLast(1) else cut
    }
}
