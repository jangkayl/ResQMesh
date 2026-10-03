package com.example.testresqmesh.feature.radar.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import com.example.testresqmesh.feature.radar.viewmodel.RadarViewModel
import com.example.testresqmesh.core.ui.model.NodeItemData
import com.example.testresqmesh.core.ui.model.NodeKind
import com.example.testresqmesh.core.ui.peers.classifyRadarNodes
import com.example.testresqmesh.feature.radar.ui.components.NetworkList
import com.example.testresqmesh.feature.radar.ui.components.NetworkPeerDetails
import com.example.testresqmesh.feature.radar.ui.components.NetworkTopology
import com.example.testresqmesh.feature.radar.ui.components.knownMeshPath

/**
 * Modernized Network & Routing Screen.
 *
 * Features:
 * - High-tech Tactical Operator Cards for People & Paths
 * - Visual Holographic 2D Map for Known Mesh Paths with animated data pulses
 * - Tactical Mesh Field overview with live topology telemetry
 */
@Composable
fun NetworkScreen(
    viewModel: RadarViewModel,
    initialSelectedNodeKey: String? = null,
    onMessagePeer: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val nodes = remember(state) { classifyRadarNodes(state) }
    var selectedNodeKey by remember(initialSelectedNodeKey) { mutableStateOf(initialSelectedNodeKey) }
    var showTopology by remember { mutableStateOf(false) }
    val selectedNode = selectedNodeKey?.let { key ->
        nodes.firstOrNull { com.example.testresqmesh.core.model.NodeIdentity.key(it.name) == key }
    }

    if (selectedNodeKey != null && selectedNode == null) {
        LaunchedEffect(selectedNodeKey) { selectedNodeKey = null }
    }

    if (selectedNode != null) {
        NetworkPeerDetails(
            node = selectedNode,
            knownPath = knownMeshPath(selectedNode),
            onBack = { selectedNodeKey = null },
            onDisconnect = { viewModel.disconnectDevice(selectedNode.endpointId) },
            onConnect = { viewModel.forceConnect(selectedNode.endpointId, selectedNode.name) },
            onBlock = { viewModel.blockDevice(selectedNode.name) },
            onUnblock = { viewModel.unblockDevice(selectedNode.name) },
            onMessage = { onMessagePeer(selectedNode.name) }
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
        onNodeClick = { selectedNodeKey = com.example.testresqmesh.core.model.NodeIdentity.key(it.name) },
        onTopologyClick = { showTopology = true }
    )
}

@Preview(name = "Mesh — reachable states", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun NetworkListPreview() {
    TestResQMeshTheme {
        NetworkList(
            nodes = listOf(
                NodeItemData("AA:01", "alpha", "", NodeKind.DIRECT, label = "Alpha"),
                NodeItemData("BB:02", "bravo", "", NodeKind.RELAY, label = "Bravo"),
                NodeItemData("CC:03", "charlie", "", NodeKind.DISCOVERED, label = "Charlie")
            ),
            onRefresh = {},
            onNodeClick = {},
            onTopologyClick = {}
        )
    }
}
