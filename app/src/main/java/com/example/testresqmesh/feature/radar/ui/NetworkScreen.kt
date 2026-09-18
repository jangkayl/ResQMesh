package com.example.testresqmesh.feature.radar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.R
import com.example.testresqmesh.core.ui.components.feedback.ResQEmptyState
import com.example.testresqmesh.core.ui.components.layout.ResQGlassSurface
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.feature.radar.viewmodel.RadarViewModel

@Composable
fun NetworkScreen(
    viewModel: RadarViewModel,
    onMessagePeer: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val nodes = remember(state) { classifyRadarNodes(state) }
    var selectedNode by remember { mutableStateOf<NodeItemData?>(null) }
    var showTopology by remember { mutableStateOf(false) }

    if (selectedNode != null) {
        NetworkPeerDetails(
            node = selectedNode!!,
            knownPath = knownMeshPath(selectedNode!!, nodes, state.topology),
            onBack = { selectedNode = null },
            onDisconnect = { viewModel.disconnectDevice(selectedNode!!.endpointId) },
            onConnect = { viewModel.forceConnect(selectedNode!!.endpointId, selectedNode!!.name) },
            onBlock = { viewModel.blockDevice(selectedNode!!.name) },
            onUnblock = { viewModel.unblockDevice(selectedNode!!.name) },
            onMessage = { onMessagePeer(selectedNode!!.name) }
        )
        return
    }

    if (showTopology) {
        NetworkTopology(
            topology = state.topology,
            onBack = { showTopology = false }
        )
        return
    }

    NetworkList(
        nodes = nodes,
        onRefresh = viewModel::rescan,
        onNodeClick = { selectedNode = it },
        onTopologyClick = { showTopology = true }
    )
}

@Composable
internal fun NetworkList(
    nodes: List<NodeItemData>,
    onRefresh: () -> Unit,
    onNodeClick: (NodeItemData) -> Unit,
    onTopologyClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.Medium, top = Spacing.Large, end = Spacing.Medium, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Network", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black)
                    Text("People around you.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                    onClick = onRefresh
                ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Refresh, "Refresh network", tint = MaterialTheme.colorScheme.primary) } }
            }
        }
        item {
            ResQGlassSurface(shape = RoundedCornerShape(28.dp), contentPadding = PaddingValues(Spacing.Medium)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(modifier = Modifier.size(48.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Hub, null, tint = MaterialTheme.colorScheme.secondary) }
                    }
                    Spacer(Modifier.width(Spacing.Medium))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Nearby people", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(networkSummaryLabel(nodes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onTopologyClick) { Text("Details") }
                }
            }
        }
        if (nodes.isEmpty()) {
            item {
                ResQEmptyState("No people nearby", "Keep ResQMesh open to find people.", icon = Icons.Outlined.Hub)
            }
        } else {
            items(nodes, key = { "${it.endpointId}-${it.name}" }) { node ->
                NetworkNodeRow(node, onClick = { onNodeClick(node) })
            }
        }
    }
}

@Composable
private fun NetworkNodeRow(node: NodeItemData, onClick: () -> Unit) {
    val status = networkStatus(node.kind)
    ResQGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(Spacing.Medium),
        shadowElevation = 10.dp
    ) {
        Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(48.dp), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(node.label.firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                Spacer(Modifier.width(Spacing.Medium))
                Column(modifier = Modifier.weight(1f)) {
                    Text(node.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(status, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = networkStatusColor(node.kind))
                }
            }
        }
    }
}

@Composable
private fun NetworkPeerDetails(
    node: NodeItemData,
    knownPath: List<String>,
    onBack: () -> Unit,
    onDisconnect: () -> Unit,
    onConnect: () -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
    onMessage: () -> Unit
) {
    var confirmBlock by remember { mutableStateOf(false) }
    val canMessage = !node.isBlocked && node.kind in setOf(NodeKind.DIRECT, NodeKind.RELAY, NodeKind.HOPPED)
    Column(Modifier.fillMaxSize().padding(Spacing.Medium), verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Text(node.label, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ResQGlassSurface(shape = RoundedCornerShape(28.dp), contentPadding = PaddingValues(Spacing.Medium)) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                Text(networkStatus(node.kind), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = networkStatusColor(node.kind))
                Text("Status follows current network evidence.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("Known mesh path", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            if (knownPath.isEmpty()) "Path unavailable right now. This is not a delivery guarantee."
            else knownPath.joinToString("  →  "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            if (canMessage) TextButton(onClick = onMessage) { Text("Message") }
            if (node.kind == NodeKind.DIRECT || node.kind == NodeKind.UNRESPONSIVE) TextButton(onClick = onDisconnect) { Text("Disconnect") }
            if (node.endpointId.isNotEmpty() && (node.kind == NodeKind.DISCOVERED || node.kind == NodeKind.SYNCING)) TextButton(onClick = onConnect) { Text("Connect") }
            if (node.isBlocked) TextButton(onClick = onUnblock) { Text("Unblock") } else TextButton(onClick = { confirmBlock = true }) { Text("Block") }
        }
    }
    if (confirmBlock) {
        AlertDialog(
            onDismissRequest = { confirmBlock = false },
            title = { Text("Block ${node.label}?") },
            text = { Text("This denies a direct link on both phones after acknowledgement. Each phone must unblock locally before direct contact can resume. Relayed messages may still be available.") },
            confirmButton = { TextButton(onClick = { confirmBlock = false; onBlock() }) { Text("Block") } },
            dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text("Cancel") } }
        )
    }
}

/** A current topology hint, deliberately not a promise of the next delivery route. */
internal fun knownMeshPath(
    target: NodeItemData,
    nodes: List<NodeItemData>,
    topology: Map<String, Set<String>>
): List<String> {
    if (target.kind == NodeKind.DIRECT) return listOf("You", target.label)
    val direct = nodes.filter { it.kind == NodeKind.DIRECT }.map { it.name }
    if (direct.isEmpty()) return emptyList()
    val adjacency = mutableMapOf<String, MutableSet<String>>()
    topology.forEach { (from, to) ->
        adjacency.getOrPut(from) { mutableSetOf() }.addAll(to)
        to.forEach { neighbor -> adjacency.getOrPut(neighbor) { mutableSetOf() }.add(from) }
    }
    val queue = ArrayDeque<List<String>>()
    direct.forEach { queue.add(listOf(it)) }
    val visited = direct.toMutableSet()
    while (queue.isNotEmpty()) {
        val path = queue.removeFirst()
        val current = path.last()
        if (com.example.testresqmesh.core.model.NodeIdentity.matches(current, target.name)) {
            return listOf("You") + path.map { name -> nodes.firstOrNull { com.example.testresqmesh.core.model.NodeIdentity.matches(it.name, name) }?.label ?: name }
        }
        adjacency[current].orEmpty().forEach { next ->
            if (visited.add(next)) queue.add(path + next)
        }
    }
    return emptyList()
}

@Composable
private fun NetworkTopology(topology: Map<String, Set<String>>, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(Spacing.Large), verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Text("Network details", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        }
        Text("Routes currently reported by the mesh.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (topology.isEmpty()) ResQEmptyState("No routes yet", "Routes appear as people connect.", icon = Icons.Outlined.Hub)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            topology.forEach { (from, to) -> item { ResQGlassSurface(shape = RoundedCornerShape(22.dp), contentPadding = PaddingValues(Spacing.Medium)) { Column { Text(from, fontWeight = FontWeight.Bold); Text(if (to.isEmpty()) "No current route" else to.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }
        }
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
private fun networkStatusColor(kind: NodeKind) = when (kind) {
    NodeKind.DIRECT -> ResQTheme.colors.success
    NodeKind.UNRESPONSIVE, NodeKind.HANDSHAKING, NodeKind.SYNCING -> ResQTheme.colors.warning
    NodeKind.RELAY, NodeKind.HOPPED -> MaterialTheme.colorScheme.primary
    NodeKind.DISCOVERED -> MaterialTheme.colorScheme.secondary
    NodeKind.OFFLINE, NodeKind.BLOCKED_OFFLINE -> MaterialTheme.colorScheme.onSurfaceVariant
}
