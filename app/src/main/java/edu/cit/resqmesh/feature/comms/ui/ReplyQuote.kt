package edu.cit.resqmesh.feature.comms.ui

import edu.cit.resqmesh.core.security.input.TextPolicy

/** Builds and parses the "> sender: snippet\n" reply quote line (wire format unchanged). */
object ReplyQuote {
    const val SNIPPET_MAX = 60

    private val LINE_BREAKS = Regex("[\\r\\n\\u0085\\u2028\\u2029]+")

    /** One-line, bounded preview of [text] so later lines never leak into the quote line. */
    fun snippet(text: String): String =
        TextPolicy.clampLength(text.replace(LINE_BREAKS, " ").trim(), SNIPPET_MAX)

    fun prefix(sender: String, snippet: String): String = "> $sender: $snippet\n"

    /** Splits a message into (quote, body); quote is null when [text] is not a quoted reply. */
    fun parse(text: String): Pair<String?, String> {
        if (text.startsWith("> ") && text.contains("\n")) {
            val firstNewline = text.indexOf("\n")
            val quote = text.substring(2, firstNewline).trim()
            val rest = text.substring(firstNewline + 1).trim()
            return quote to rest
        }
        return null to text
    }
}
