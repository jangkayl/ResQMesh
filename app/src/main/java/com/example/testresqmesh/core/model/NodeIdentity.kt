package com.example.testresqmesh.core.model

/**
 * Canonical node identity resolution for the ResQMesh network.
 *
 * A fully qualified node name has the shape `"<display name> [<TAG>]#<NODE_ID>"` where `NODE_ID`
 * is a short, stable, per-install identifier persisted in SharedPreferences (`node_id`).
 *
 * A BLE advertisement can only carry 26 bytes of `0xFFFF` manufacturer data, so the human readable
 * part of the name is routinely truncated on the wire. The previous approach of comparing truncated
 * names with `String.contains(other.take(15))` produced both false positives (unrelated nodes that
 * share a prefix collapsing into one row) and false negatives (a direct peer looking like a brand
 * new node, which is what made the Radar label a physically connected device as
 * "Connected (Via Relay)").
 *
 * Every identity comparison in the app must go through this object so the rules stay consistent
 * across the scanner, the repository, the router and the UI.
 */
object NodeIdentity {

    private const val ID_DELIMITER = '#'

    /**
     * Minimum number of characters two ID-less names must share before they may be treated as the
     * same node. Short names are far too ambiguous to match on a prefix.
     */
    private const val MIN_FUZZY_PREFIX = 6

    /** Placeholder assigned to an inbound GATT server link before the handshake reveals its name. */
    const val UNKNOWN_NAME = "Unknown Node"

    /** Extracts the stable node ID from a fully qualified name, or null when absent. */
    fun idOf(fullName: String?): String? {
        val name = fullName?.trim().orEmpty()
        if (name.isEmpty()) return null
        val index = name.lastIndexOf(ID_DELIMITER)
        if (index == -1 || index == name.length - 1) return null
        val id = name.substring(index + 1).trim()
        return if (id.isEmpty()) null else id.uppercase()
    }

    /** Strips the `#NODE_ID` suffix, leaving the human readable portion of the name. */
    fun displayNameOf(fullName: String?): String {
        val name = fullName?.trim().orEmpty()
        val index = name.lastIndexOf(ID_DELIMITER)
        return if (index <= 0) name else name.substring(0, index).trim()
    }

    /**
     * A stable grouping key: the node ID when known, otherwise the normalised name. Safe to use with
     * `distinctBy` because it never collapses two nodes that advertise different IDs.
     */
    fun key(fullName: String?): String =
        idOf(fullName) ?: fullName?.trim()?.lowercase().orEmpty()

    /** True when the name carries no usable identity yet (blank or an "Unknown ..." placeholder). */
    fun isPlaceholder(fullName: String?): Boolean {
        val name = fullName?.trim().orEmpty()
        return name.isEmpty() || name.contains("Unknown", ignoreCase = true)
    }

    /** Re-attaches a node ID to a (possibly truncated) advertised display name. */
    fun compose(displayName: String?, nodeId: String?): String {
        val display = displayNameOf(displayName)
        val id = nodeId?.trim()?.uppercase().orEmpty()
        if (id.isEmpty()) return display
        if (display.isEmpty()) return "$ID_DELIMITER$id"
        return "$display$ID_DELIMITER$id"
    }

    /**
     * True when both names refer to the same physical node.
     *
     * Resolution order:
     * 1. Placeholders only ever match themselves, so an un-handshaked socket is never mistaken for
     *    a known peer.
     * 2. When both names carry a node ID the comparison is an exact ID match. This is the reliable
     *    path and works even when one side is heavily truncated.
     * 3. Otherwise fall back to a prefix comparison of the display names, which is far tighter than
     *    the old two-way `contains` check.
     */
    fun matches(a: String?, b: String?): Boolean {
        val left = a?.trim().orEmpty()
        val right = b?.trim().orEmpty()
        if (left.isEmpty() || right.isEmpty()) return false
        if (left.equals(right, ignoreCase = true)) return true
        if (isPlaceholder(left) || isPlaceholder(right)) return false

        val idLeft = idOf(left)
        val idRight = idOf(right)
        if (idLeft != null && idRight != null) return idLeft == idRight

        return sharesPrefix(displayNameOf(left), displayNameOf(right))
    }

    /** Convenience helper: does [candidate] match any name in [names]? */
    fun matchesAny(candidate: String?, names: Iterable<String>): Boolean =
        names.any { matches(candidate, it) }

    private fun sharesPrefix(a: String, b: String): Boolean {
        val left = a.trim().lowercase()
        val right = b.trim().lowercase()
        if (left.isEmpty() || right.isEmpty()) return false
        if (left == right) return true
        val shared = minOf(left.length, right.length)
        if (shared < MIN_FUZZY_PREFIX) return false
        return left.take(shared) == right.take(shared)
    }
}
