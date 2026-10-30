package edu.cit.resqmesh.feature.devtools.model

import edu.cit.resqmesh.core.logging.TerminalLogEntry
import edu.cit.resqmesh.core.logging.trace.shortId
import edu.cit.resqmesh.core.model.NodeIdentity
import edu.cit.resqmesh.core.network.LinkDiagnostic

/** One live GATT role (and its transport) to a peer. */
data class LinkPath(
    val role: String,
    val generation: Long,
    val transport: String,
    val endpoint: String,
    val state: String
) {
    val ready: Boolean get() = state == READY

    private companion object {
        const val READY = "READY"
    }
}

/** One peer and every live path to it. [key] is stable while the peer stays the same. */
data class LinkGroup(val key: String, val title: String, val paths: List<LinkPath>) {
    val readyCount: Int get() = paths.count { it.ready }
}

/**
 * Groups live links by peer: node ID first, then a non-placeholder identity key, then the endpoint.
 * A placeholder name never groups, and no title ever says "Unknown".
 */
object LinkGrouper {
    const val IDENTIFYING = "Identifying…"

    fun group(links: List<LinkDiagnostic>, peerNames: Map<String, String> = emptyMap()): List<LinkGroup> =
        links.groupBy(::keyOf).map { (key, members) ->
            LinkGroup(key, titleOf(members, peerNames), members.map(::pathOf).sortedWith(PATH_ORDER))
        }.sortedBy { it.key }

    private val PATH_ORDER = compareBy<LinkPath>({ !it.ready }, { it.role }, { it.generation }, { it.endpoint })

    private fun pathOf(l: LinkDiagnostic) = LinkPath(l.role, l.generation, l.transport, l.endpoint, l.state)

    private fun idOf(l: LinkDiagnostic): String? =
        l.nodeId?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
            ?: l.name?.takeUnless(NodeIdentity::isPlaceholder)?.let(NodeIdentity::idOf)

    private fun keyOf(l: LinkDiagnostic): String {
        val named = l.name?.takeUnless(NodeIdentity::isPlaceholder)
        return idOf(l)?.let { "id:$it" } ?: named?.let { "name:${NodeIdentity.key(it)}" } ?: "ep:${l.endpoint}"
    }

    private fun titleOf(members: List<LinkDiagnostic>, peerNames: Map<String, String>): String {
        val id = members.firstNotNullOfOrNull(::idOf)
        val linkName = NodeIdentity.preferredName(members.mapNotNull { it.name })
        val name = linkName.ifEmpty {
            id?.let { peerNames[it] ?: peerNames[it.lowercase()] }?.takeUnless(NodeIdentity::isPlaceholder).orEmpty()
        }
        val display = NodeIdentity.displayNameOf(name)
        val short = shortId(id)
        return when {
            display.isNotEmpty() && short != null -> "$display ($short)"
            display.isNotEmpty() -> display
            short != null -> short
            else -> "$IDENTIFYING ${TerminalLogEntry.shortEndpoint(members.first().endpoint)}"
        }
    }
}
