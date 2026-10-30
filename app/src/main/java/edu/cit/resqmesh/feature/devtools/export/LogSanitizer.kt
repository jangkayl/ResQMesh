package edu.cit.resqmesh.feature.devtools.export

/**
 * Exports only strict `MARKER key=value ...` lines. Anything else (which may carry peer names or
 * message text) becomes [OMITTED]. Only allowlisted keys pass; full MACs are shortened.
 */
object LogSanitizer {
    const val OMITTED = "free text omitted"
    private const val MAX_LINE = 240
    private const val SHORT_MAC_CHARS = 5
    private const val SHORT_ID_CHARS = 8
    private val idKeys = setOf("node", "message", "incident", "transmission", "sos", "packet", "origin")

    private val marker = Regex("^[A-Z][A-Z0-9_]{2,39}$")
    private val key = Regex("^[A-Za-z][A-Za-z0-9_]{0,31}$")
    private val value = Regex("^[A-Za-z0-9_.:/+\\-#]{0,64}$")
    private val mac = Regex("(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")
    // Only these keys may carry a value into an export; any other key omits the whole line.
    private val allowedKeys = setOf(
        "id", "node", "message", "incident", "transmission", "sos", "packet", "origin", "endpoint",
        "role", "gen", "generation", "state", "stage", "type", "reason", "transport", "bytes", "count",
        "queuems", "transferms", "attempt", "mtu", "psm", "hop", "ttl", "size", "link", "result", "code"
    )
    private val safeToken = Regex("^[A-Za-z0-9_.\\-]{1,40}$")

    fun sanitize(line: String): String {
        if (line.length > MAX_LINE || line.any { it.isISOControl() }) return OMITTED
        val parts = line.trim().split(' ')
        val pairs = parts.drop(1).map { pair(it) }
        return if (parts.size < 2 || !marker.matches(parts[0]) || pairs.any { it == null }) {
            OMITTED
        } else {
            (listOf(parts[0]) + pairs.filterNotNull()).joinToString(" ")
        }
    }

    /** `key=value` or null when the pair is not strictly well formed. */
    private fun pair(p: String): String? {
        val eq = p.indexOf('=')
        val k = if (eq > 0) p.substring(0, eq) else ""
        val v = if (eq > 0) p.substring(eq + 1) else ""
        val ok = k.lowercase() in allowedKeys && key.matches(k) && value.matches(v)
        val shown = if (isIdKey(k)) shortenMacs(v).take(SHORT_ID_CHARS) else shortenMacs(v)
        return if (ok) "$k=$shown" else null
    }

    private fun isIdKey(k: String): Boolean {
        val l = k.lowercase()
        return l in idKeys || l.endsWith("id")
    }

    /** Fixed identifiers only (tags, event names); anything else is replaced. */
    fun token(raw: String): String = if (safeToken.matches(raw)) raw else "-"

    fun shortenMacs(s: String): String = mac.replace(s) { "~" + it.value.takeLast(SHORT_MAC_CHARS) }
}
