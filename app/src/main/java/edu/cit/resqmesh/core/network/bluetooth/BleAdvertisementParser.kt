package edu.cit.resqmesh.core.network.bluetooth

import edu.cit.resqmesh.core.model.NodeIdentity

/** Strict, non-throwing parser for the manufacturer-data advertisement `score|connections|nodeId|name`. */
internal object BleAdvertisementParser {
    const val MAX_PAYLOAD_BYTES = 26
    private const val MAX_CONNECTIONS = 64
    private const val MAX_NAME_CHARS = 26
    private const val MAX_CONNECTION_DIGITS = 2
    private const val MIN_PARTS = 3
    private const val FULL_PARTS = 4
    private const val REPLACEMENT = '�'
    private val SCORE = Regex("[0-9]{3}[0-9A-Fa-f]{2}")
    private val NODE_ID = Regex("[0-9A-F]{4}")

    fun parse(endpoint: String, bytes: ByteArray?): BleAdvertisement? = runCatching {
        if (bytes == null || bytes.isEmpty() || bytes.size > MAX_PAYLOAD_BYTES) return null
        val parts = String(bytes, Charsets.UTF_8).replace("\u0000", "").trim().split("|")
        if (parts.size < MIN_PARTS) return null
        val score = parts[0].takeIf { SCORE.matches(it) } ?: return null
        val connections = connections(parts[1]) ?: return null
        val rawName = if (parts.size >= FULL_PARTS) parts.drop(MIN_PARTS).joinToString("|") else parts[2]
        val nodeId = nodeId(parts, rawName) ?: return null
        val displayName = displayName(rawName, allowEmpty = nodeId.isNotEmpty()) ?: return null
        val peerName = if (nodeId.isNotEmpty()) NodeIdentity.compose(displayName, nodeId) else displayName
        BleAdvertisement(endpoint, peerName, nodeId, score, connections)
    }.getOrNull()

    /** Four-part form carries an explicit ID; the legacy three-part form carries it inside the name, if at all. */
    private fun nodeId(parts: List<String>, rawName: String): String? =
        if (parts.size >= FULL_PARTS) parts[2].trim().uppercase().takeIf { NODE_ID.matches(it) && '#' !in rawName }
        else NodeIdentity.idOf(rawName).orEmpty().takeIf { it.isEmpty() || NODE_ID.matches(it) }

    private fun connections(text: String): Int? =
        text.takeIf { it.length in 1..MAX_CONNECTION_DIGITS && it.all { c -> c in '0'..'9' } }
            ?.toIntOrNull()?.takeIf { it <= MAX_CONNECTIONS }

    /** ASCII digits regardless of the device locale (e.g. ar-EG would otherwise emit Arabic-Indic digits). */
    fun formatScore(specScore: Int, hex: String): String = String.format(java.util.Locale.ROOT, "%03d%s", specScore, hex)

    /** Name as the encoder must send it: no character the strict [parse] would refuse (empty is allowed). */
    fun advertisedName(displayName: String): String =
        displayName.filter { !it.isISOControl() && it != REPLACEMENT && it != '#' }.trim()

    private fun displayName(rawName: String, allowEmpty: Boolean): String? {
        // The byte cap can cut a multi-byte character; drop only that trailing replacement.
        val name = NodeIdentity.displayNameOf(rawName.trimEnd(REPLACEMENT)).trim()
        // An empty name is fine when the node id identifies the peer (the UI falls back to "#ID").
        val valid = (name.isNotEmpty() || allowEmpty) && name.length <= MAX_NAME_CHARS &&
            name.none { it.isISOControl() || it == REPLACEMENT || it == '#' }
        return name.takeIf { valid }
    }
}
