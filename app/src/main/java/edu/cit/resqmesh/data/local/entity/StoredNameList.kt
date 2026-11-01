package edu.cit.resqmesh.data.local.entity

private const val ESCAPE_LENGTH = 3

/**
 * Codec for the comma-joined name columns (deliveredTo, seenBy, outboundRoute).
 * Items escape '%' and ',' so names containing commas survive; legacy rows decode unchanged.
 */
internal object StoredNameList {
    fun encode(items: List<String>): String =
        items.joinToString(",") { it.replace("%", "%25").replace(",", "%2C") }

    fun decode(stored: String): List<String> {
        if (stored.isEmpty()) return emptyList()
        return stored.split(",").map(::unescape)
    }

    private fun unescape(item: String): String {
        if (!item.contains('%')) return item
        val out = StringBuilder(item.length)
        var i = 0
        while (i < item.length) {
            val c = item[i]
            if (c == '%' && item.startsWith("25", i + 1)) {
                out.append('%'); i += ESCAPE_LENGTH
            } else if (c == '%' && item.startsWith("2C", i + 1)) {
                out.append(','); i += ESCAPE_LENGTH
            } else {
                out.append(c); i++
            }
        }
        return out.toString()
    }
}
