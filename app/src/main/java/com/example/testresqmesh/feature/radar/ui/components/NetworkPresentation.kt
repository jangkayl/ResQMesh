package com.example.testresqmesh.feature.radar.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.testresqmesh.core.ui.components.feedback.ResQStatusTone
import com.example.testresqmesh.core.ui.model.NodeItemData
import com.example.testresqmesh.core.ui.model.NodeKind

internal fun knownMeshPath(
    target: NodeItemData
): List<String> {
    if (target.kind == NodeKind.DIRECT) return listOf("You", target.label)
    return target.route.mapIndexed { index, name ->
        if (index == 0) "You" else com.example.testresqmesh.core.model.NodeIdentity.displayNameOf(name).ifBlank { name }
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

internal fun networkSummaryLabel(nodes: List<NodeItemData>): String = when {
    nodes.any { it.kind == NodeKind.DIRECT } -> "${nodes.count { it.kind == NodeKind.DIRECT }} direct"
    nodes.any { it.kind == NodeKind.RELAY || it.kind == NodeKind.HOPPED } -> "${nodes.count { it.kind == NodeKind.RELAY || it.kind == NodeKind.HOPPED }} relayed"
    nodes.isNotEmpty() -> "${nodes.size} found"
    else -> "No people nearby"
}

@Composable
internal fun networkStatusTone(kind: NodeKind) = when (kind) {
    NodeKind.DIRECT -> ResQStatusTone.Success
    NodeKind.UNRESPONSIVE, NodeKind.HANDSHAKING, NodeKind.SYNCING -> ResQStatusTone.Warning
    NodeKind.RELAY, NodeKind.HOPPED -> ResQStatusTone.Information
    NodeKind.DISCOVERED -> ResQStatusTone.Neutral
    NodeKind.OFFLINE, NodeKind.BLOCKED_OFFLINE -> ResQStatusTone.Neutral
}
