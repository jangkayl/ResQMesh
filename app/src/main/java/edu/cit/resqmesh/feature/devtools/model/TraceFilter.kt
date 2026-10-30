package edu.cit.resqmesh.feature.devtools.model

import edu.cit.resqmesh.core.logging.trace.PacketTrace
import edu.cit.resqmesh.core.logging.trace.TraceDir
import edu.cit.resqmesh.core.logging.trace.TraceScope
import edu.cit.resqmesh.core.logging.trace.TraceType

/** Pure packet filter. Null fields mean "any"; [query] is a case-insensitive substring search. */
data class TraceFilter(
    val query: String = "",
    val dir: TraceDir? = null,
    val scope: TraceScope? = null,
    val type: TraceType? = null,
    val transport: String? = null
) {
    val isActive: Boolean
        get() = query.isNotBlank() || dir != null || scope != null || type != null || transport != null

    /** [nameOf] resolves a short ID to a display name so searching by peer name works on screen. */
    fun matches(t: PacketTrace, nameOf: (String) -> String? = { null }): Boolean {
        val fieldsMatch = (dir == null || t.dir == dir) && (scope == null || t.scope == scope) &&
            (type == null || t.type == type) && (transport == null || transport.equals(t.transport, ignoreCase = true))
        val q = query.trim()
        return fieldsMatch && (q.isEmpty() || textMatches(t, q, nameOf))
    }

    private fun textMatches(t: PacketTrace, q: String, nameOf: (String) -> String?): Boolean {
        val haystack = listOfNotNull(
            t.packetKey, t.sourceId, t.destId, t.nextHop, t.type.name, t.transport, t.stage.name
        )
        val peers = listOfNotNull(t.sourceId, t.destId, t.nextHop)
        return haystack.any { it.contains(q, ignoreCase = true) } ||
            peers.any { nameOf(it)?.contains(q, ignoreCase = true) == true }
    }

    fun apply(list: List<PacketTrace>, nameOf: (String) -> String? = { null }): List<PacketTrace> =
        if (!isActive) list else list.filter { matches(it, nameOf) }
}
