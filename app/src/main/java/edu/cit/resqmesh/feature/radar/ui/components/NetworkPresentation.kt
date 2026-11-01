package edu.cit.resqmesh.feature.radar.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import edu.cit.resqmesh.core.ui.components.feedback.ResQStatusTone
import edu.cit.resqmesh.core.ui.model.NodeItemData
import edu.cit.resqmesh.core.ui.model.NodeKind

internal fun knownMeshPath(
    target: NodeItemData
): List<String> {
    if (target.kind == NodeKind.DIRECT) return listOf("You", target.label)
    return target.route.mapIndexed { index, name ->
        if (index == 0) "You" else edu.cit.resqmesh.core.model.NodeIdentity.displayNameOf(name).ifBlank { name }
    }
}

internal fun networkStatus(kind: NodeKind): String = when (kind) {
    NodeKind.DIRECT -> "Direct"
    NodeKind.UNRESPONSIVE -> "Checking"
    NodeKind.HANDSHAKING, NodeKind.SYNCING -> "Connecting"
    NodeKind.RELAY, NodeKind.HOPPED -> "Relayed"
    NodeKind.DISCOVERED -> "Nearby"
    NodeKind.OFFLINE -> "Offline"
    NodeKind.BLOCKED_OFFLINE -> "Blocked"
}

/**
 * Chip text for a row. A blocked row says where the person is, not that a link exists: a socket still
 * present on a blocked peer is being closed, so it reads "Closing link" rather than Direct or Connecting.
 */
internal fun networkStatus(node: NodeItemData): String {
    if (!node.isBlocked) return networkStatus(node.kind)
    val where = when (node.kind) {
        NodeKind.DIRECT, NodeKind.UNRESPONSIVE, NodeKind.HANDSHAKING -> "Closing link"
        NodeKind.RELAY, NodeKind.HOPPED -> "Relayed"
        NodeKind.DISCOVERED, NodeKind.SYNCING -> "Nearby"
        NodeKind.OFFLINE, NodeKind.BLOCKED_OFFLINE -> "Offline"
    }
    return "Blocked · $where"
}

/** Blocked peers never count as direct, relayed or found: they are not reachable for a direct link. */
internal fun networkSummaryLabel(nodes: List<NodeItemData>): String {
    val usable = nodes.filter { !it.isBlocked }
    val direct = usable.count { it.kind == NodeKind.DIRECT }
    val relayed = usable.count { it.kind == NodeKind.RELAY || it.kind == NodeKind.HOPPED }
    return when {
        direct > 0 -> "$direct direct"
        relayed > 0 -> "$relayed relayed"
        usable.isNotEmpty() -> "${usable.size} found"
        else -> "No people nearby"
    }
}

internal fun networkStatusTone(kind: NodeKind) = when (kind) {
    NodeKind.DIRECT -> ResQStatusTone.Success
    NodeKind.UNRESPONSIVE, NodeKind.HANDSHAKING, NodeKind.SYNCING -> ResQStatusTone.Warning
    NodeKind.RELAY, NodeKind.HOPPED -> ResQStatusTone.Information
    NodeKind.DISCOVERED -> ResQStatusTone.Neutral
    NodeKind.OFFLINE, NodeKind.BLOCKED_OFFLINE -> ResQStatusTone.Neutral
}

internal fun networkStatusTone(node: NodeItemData): ResQStatusTone =
    if (node.isBlocked) ResQStatusTone.Critical else networkStatusTone(node.kind)
