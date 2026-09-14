package com.example.testresqmesh.feature.radar.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import androidx.compose.ui.tooling.preview.Preview
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import com.example.testresqmesh.core.ui.theme.InboxBackground
import com.example.testresqmesh.core.ui.theme.InboxAccentBlue
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.feature.radar.viewmodel.RadarViewModel
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.ui.state.RadarUiState
import com.example.testresqmesh.core.utils.AppLogger

@Composable
fun RadarScreen(viewModel: RadarViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Must match MeshRepository.startNode exactly, including the "#nodeId" suffix, otherwise the
    // graph fails to collapse the local node onto the centre and "me" is drawn twice.
    val myDeviceName = remember(context) {
        val prefs = context.getSharedPreferences("resqmesh_prefs", android.content.Context.MODE_PRIVATE)
        val customName = prefs.getString("custom_name", android.os.Build.MODEL) ?: android.os.Build.MODEL
        val tag = prefs.getString("node_tag", "NODE") ?: "NODE"
        val nodeId = prefs.getString("node_id", "") ?: ""
        if (nodeId.isEmpty()) "$customName [$tag]" else "$customName [$tag]#$nodeId"
    }

    val nodes = remember(uiState) { classifyRadarNodes(uiState) }
    val activeNodesCount = remember(nodes) { nodes.count { it.kind != NodeKind.BLOCKED_OFFLINE } }
    val directNodeNames = remember(nodes) { nodes.filter { it.kind == NodeKind.DIRECT }.map { it.name } }

    RadarScreenContent(
        activeNodesCount = activeNodesCount,
        nodes = nodes,
        topology = uiState.topology,
        myDeviceName = myDeviceName,
        directNodeNames = directNodeNames,
        onRefresh = { viewModel.rescan() },
        onDisconnect = { viewModel.disconnectDevice(it) },
        onForceConnect = { id, name -> viewModel.forceConnect(id, name) },
        onBlock = { name -> viewModel.blockDevice(name) },
        onUnblock = { name -> viewModel.unblockDevice(name) }
    )
}

/**
 * Derives the "Nearby Nodes" list from network state.
 *
 * The previous implementation decided a scanned device was reachable "Via Relay" by checking whether
 * its name appeared anywhere in [RadarUiState.topology]. That was never a valid test: every node that
 * emits a SYSTEM pulse becomes a topology key, including the peers we are *directly* connected to. So
 * whenever a live direct link was briefly missing from `connectedDevices` (a nameless inbound socket,
 * a dual-MAC rotation, or a stale discovery row) the device fell through to the scanned branch,
 * matched the topology, and was labelled "Connected (Via Relay)" despite being physically connected.
 *
 * Classification is now driven by explicit authoritative signals:
 *  - `connectedDevices`  -> a physical socket exists (DIRECT, or HANDSHAKING while still nameless).
 *  - `knownNodes.isDirect == false` -> genuinely only reachable through the mesh.
 *  - `scannedDevices`    -> in radio range but not routed yet.
 *
 * Kept as a pure function so it is cheap to reason about and unit-testable without Compose.
 */
internal fun classifyRadarNodes(state: RadarUiState): List<NodeItemData> {
    fun blockedNameFor(name: String): String? =
        state.blockedDeviceNames.firstOrNull { NodeIdentity.matches(it, name) }

    fun label(fullName: String): String {
        val display = NodeIdentity.displayNameOf(fullName).ifBlank { fullName }
        val id = NodeIdentity.idOf(fullName)
        return if (id == null) display else "$display #$id"
    }

    // 1. Physical direct links. Authoritative: a socket either exists or it does not.
    val directNodes = state.connectedDevices
        .distinctBy { NodeIdentity.key(it.name).ifEmpty { it.endpointId } }
        .map { device ->
            val handshaking = device.isProvisional || NodeIdentity.isPlaceholder(device.name)
            if (handshaking) {
                NodeItemData(
                    endpointId = device.endpointId,
                    name = device.name,
                    label = "Node ${device.endpointId.takeLast(5)}",
                    status = "Linking (Handshaking)",
                    kind = NodeKind.HANDSHAKING,
                    isConnected = true,
                    isActiveRelay = false
                )
            } else {
                NodeItemData(
                    endpointId = device.endpointId,
                    name = device.name,
                    label = label(device.name),
                    status = "Connected (Direct)",
                    kind = NodeKind.DIRECT,
                    isConnected = true,
                    isActiveRelay = true,
                    isBlocked = blockedNameFor(device.name) != null
                )
            }
        }

    fun isDirectPeer(name: String): Boolean =
        directNodes.any { NodeIdentity.matches(it.name, name) }

    // 2. Nodes the router says are reachable only through the mesh.
    val indirectNodes = state.knownNodes
        .filter { !it.isDirect && !NodeIdentity.isPlaceholder(it.name) && !isDirectPeer(it.name) }
        .distinctBy { NodeIdentity.key(it.name) }
        .map { node ->
            // Still advertising nearby -> it is a relay peer in radio range.
            // Not advertising    -> it is purely a mesh hop somewhere further out.
            val inRadioRange = state.scannedDevices.any { NodeIdentity.matches(it.name, node.name) }
            val blocked = blockedNameFor(node.name) != null
            NodeItemData(
                endpointId = "",
                name = node.name,
                label = label(node.name),
                status = if (inRadioRange) "Connected (Via Relay)" else "Hopped via Mesh",
                kind = if (inRadioRange) NodeKind.RELAY else NodeKind.HOPPED,
                isConnected = false,
                isActiveRelay = true,
                isBlocked = blocked
            )
        }

    fun isRouted(name: String): Boolean =
        indirectNodes.any { NodeIdentity.matches(it.name, name) }

    // 3. Everything else we can physically see but have not linked or routed.
    val discoveredNodes = state.scannedDevices
        .filter { !isDirectPeer(it.name) && !isRouted(it.name) && !NodeIdentity.isPlaceholder(it.name) }
        .distinctBy { NodeIdentity.key(it.name) }
        .map { scanned ->
            NodeItemData(
                endpointId = scanned.endpointId,
                name = scanned.name,
                label = label(scanned.name),
                status = if (scanned.isConnecting) "SYNCING..." else "Discovered / Scanning...",
                kind = if (scanned.isConnecting) NodeKind.SYNCING else NodeKind.DISCOVERED,
                isConnected = false,
                isActiveRelay = false,
                isBlocked = blockedNameFor(scanned.name) != null
            )
        }

    // 4. Blocked nodes that are entirely out of range, so the user can still unblock them.
    val visible = directNodes + indirectNodes + discoveredNodes
    val offlineBlockedNodes = state.blockedDeviceNames
        .filter { blockedName -> visible.none { NodeIdentity.matches(it.name, blockedName) } }
        .map { name ->
            NodeItemData(
                endpointId = "",
                name = name,
                label = label(name),
                status = "OFFLINE",
                kind = NodeKind.BLOCKED_OFFLINE,
                isConnected = false,
                isActiveRelay = false,
                isBlocked = true
            )
        }

    return visible + offlineBlockedNodes
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreenContent(
    activeNodesCount: Int,
    nodes: List<NodeItemData>,
    topology: Map<String, Set<String>>,
    myDeviceName: String,
    onRefresh: () -> Unit,
    onDisconnect: (String) -> Unit,
    onForceConnect: (String, String) -> Unit,
    onBlock: (String) -> Unit,
    onUnblock: (String) -> Unit,
    directNodeNames: List<String> = nodes.filter { it.kind == NodeKind.DIRECT }.map { it.name }
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ResQMesh", fontWeight = FontWeight.Black, color = Color.White) },
                actions = {
                    IconButton(onClick = { AppLogger.toggleTerminal() }) {
                        Icon(Icons.Outlined.Shield, contentDescription = "Debug Terminal", tint = Color.White)
                    }
                    IconButton(onClick = { /* Mesh settings */ }) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = InboxAccentBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = InboxBackground)
            )
        },
        floatingActionButton = {
            LargeFloatingActionButton(
                onClick = { /* Quick SOS */ },
                containerColor = Color(0xFFEF4444),
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(Icons.Default.Notifications, contentDescription = "SOS", tint = Color.White, modifier = Modifier.size(32.dp))
            }
        },
        containerColor = InboxBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Radar Visualization Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                // Active Nodes Counter Badge
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.align(Alignment.TopStart).padding(Spacing.Medium)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFF97316)))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("$activeNodesCount ACTIVE NODES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.White)
                    }
                }

                // Ring 1 must be true physical links only. Passing every `isConnected` node also
                // pushed relay-reachable peers into the inner ring, misrepresenting the topology.
                NetworkGraphVisualizer(topology = topology, myDeviceName = myDeviceName, connectedNodes = directNodeNames)
                
                Text(
                    "SCAN RANGE: 1.2KM",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.Medium)
                )
            }

            // Network Status Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Medium),
                color = Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(Spacing.Medium)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Network Status", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Mesh Protocol: v2.4 Active", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                        }
                        Surface(color = Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp)) {
                            Text("Healthy", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        }
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.1f))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatusMetric("NODES", String.format(Locale.getDefault(), "%02d", activeNodesCount))
                        StatusMetric("DEPTH", "3 Hops")
                        StatusMetric("RANGE", "~800m")
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.Large))

            // Nearby Nodes List Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Medium),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("NEARBY NODES", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = Color.White)
                TextButton(onClick = onRefresh) {
                    Text("Refresh", color = InboxAccentBlue, style = MaterialTheme.typography.labelMedium)
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = InboxAccentBlue, modifier = Modifier.size(16.dp))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Medium),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
                // Group by explicit kind. The previous `status.contains("Connected")` matching would
                // misfile any node whose user-chosen display name happened to contain those words.
                val connectedNodesList = nodes.filter { it.kind == NodeKind.DIRECT || it.kind == NodeKind.HANDSHAKING }
                val relayNodesList = nodes.filter { it.kind == NodeKind.RELAY }
                val hoppedNodesList = nodes.filter { it.kind == NodeKind.HOPPED }
                val onlineNodesList = nodes.filter { it.kind == NodeKind.DISCOVERED || it.kind == NodeKind.SYNCING }
                val offlineNodesList = nodes.filter { it.kind == NodeKind.BLOCKED_OFFLINE }

                if (connectedNodesList.isNotEmpty()) {
                    Text("CONNECTED", style = MaterialTheme.typography.labelSmall, color = InboxAccentBlue, modifier = Modifier.padding(top = Spacing.Small))
                    connectedNodesList.forEach { node ->
                        NearbyNodeItem(node, onDisconnect, onForceConnect, onBlock, onUnblock)
                    }
                }

                if (relayNodesList.isNotEmpty()) {
                    Text("CONNECTED VIA RELAY", style = MaterialTheme.typography.labelSmall, color = Color(0xFF38BDF8), modifier = Modifier.padding(top = Spacing.Small))
                    relayNodesList.forEach { node ->
                        NearbyNodeItem(node, onDisconnect, onForceConnect, onBlock, onUnblock)
                    }
                }

                if (hoppedNodesList.isNotEmpty()) {
                    Text("MESH HOPPED", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF59E0B), modifier = Modifier.padding(top = Spacing.Small))
                    hoppedNodesList.forEach { node ->
                        NearbyNodeItem(node, onDisconnect, onForceConnect, onBlock, onUnblock)
                    }
                }

                if (onlineNodesList.isNotEmpty()) {
                    Text("DISCOVERED", style = MaterialTheme.typography.labelSmall, color = Color(0xFF10B981), modifier = Modifier.padding(top = Spacing.Small))
                    onlineNodesList.forEach { node ->
                        NearbyNodeItem(node, onDisconnect, onForceConnect, onBlock, onUnblock)
                    }
                }
                
                if (offlineNodesList.isNotEmpty()) {
                    Text("BLOCKED", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f), modifier = Modifier.padding(top = Spacing.Small))
                    offlineNodesList.forEach { node ->
                        NearbyNodeItem(node, onDisconnect, onForceConnect, onBlock, onUnblock)
                    }
                }
                
                if (nodes.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Text("No nodes detected nearby.", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.Large))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.SignalCellularAlt,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "AES-256 MESH-TUNNEL ESTABLISHED",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.2f),
                        fontSize = 8.sp
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.Large))
            }
        }
    }
}

@Composable
fun StatusMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.4f))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
    }
}

@Composable
fun NearbyNodeItem(
    node: NodeItemData, 
    onDisconnect: (String) -> Unit, 
    onForceConnect: (String, String) -> Unit,
    onBlock: (String) -> Unit,
    onUnblock: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box {
                Surface(modifier = Modifier.size(44.dp), shape = CircleShape, color = Color.White.copy(alpha = 0.1f)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
                    }
                }
                if (node.isActiveRelay) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(InboxAccentBlue)
                            .align(Alignment.TopEnd)
                            .border(2.dp, Color(0xFF1E293B), CircleShape)
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = Color.White, modifier = Modifier.padding(2.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.width(Spacing.Medium))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    node.label.ifEmpty { node.name },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Black,
                    color = if (node.isBlocked) Color.Gray else Color.White
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isLive = node.kind == NodeKind.DIRECT || node.kind == NodeKind.RELAY || node.kind == NodeKind.HOPPED
                    val statusColor = when {
                        node.isBlocked -> Color.Red
                        isLive -> InboxAccentBlue
                        else -> Color.White.copy(alpha = 0.4f)
                    }
                    Icon(
                        when {
                            node.isBlocked -> Icons.Default.Block
                            isLive -> Icons.Default.Bolt
                            else -> Icons.Default.Info
                        },
                        contentDescription = null, 
                        tint = statusColor, 
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (node.isBlocked) "BLOCKED" else node.status, style = MaterialTheme.typography.labelSmall, color = statusColor)
                }
            }

            when {
                node.isBlocked -> {
                    TextButton(
                        onClick = { onUnblock(node.name) },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF10B981))
                    ) {
                        Text("UNBLOCK", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                    }
                }

                // Only a real physical link owns a valid endpointId to tear down. Relay and hopped
                // nodes previously showed this button too, but their endpointId was the peer's
                // advertising MAC, so the disconnect silently hit the wrong endpoint.
                node.kind == NodeKind.DIRECT -> {
                    IconButton(onClick = { onBlock(node.name) }) {
                        Icon(Icons.Default.Block, contentDescription = "Block Device", tint = Color.Gray)
                    }
                    IconButton(onClick = { onDisconnect(node.endpointId) }) {
                        Icon(Icons.Outlined.LinkOff, contentDescription = "Unlink Device", tint = Color(0xFFEF4444))
                    }
                }

                // Handshaking: socket is up but nameless, so blocking/unlinking by name is unsafe.
                node.kind == NodeKind.HANDSHAKING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = InboxAccentBlue
                    )
                }

                node.kind == NodeKind.RELAY || node.kind == NodeKind.DISCOVERED || node.kind == NodeKind.SYNCING -> {
                    IconButton(onClick = { onBlock(node.name) }) {
                        Icon(Icons.Default.Block, contentDescription = "Block Device", tint = Color.Gray)
                    }
                    if (node.endpointId.isNotEmpty()) {
                        TextButton(
                            onClick = { onForceConnect(node.endpointId, node.name) },
                            colors = ButtonDefaults.textButtonColors(contentColor = InboxAccentBlue)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("FORCE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                        }
                    }
                }

                // HOPPED nodes are out of radio range: nothing to link or unlink directly.
                else -> {
                    IconButton(onClick = { onBlock(node.name) }) {
                        Icon(Icons.Default.Block, contentDescription = "Block Device", tint = Color.Gray)
                    }
                }
            }
        }
    }
}

/** Explicit classification of a Radar row, so grouping and actions never depend on status text. */
enum class NodeKind {
    /** A live physical socket with a confirmed name. */
    DIRECT,
    /** A live physical socket that has not completed the name handshake yet. */
    HANDSHAKING,
    /** Reachable only through the mesh, but still advertising within radio range. */
    RELAY,
    /** Reachable only through the mesh and out of radio range. */
    HOPPED,
    /** Advertising nearby, not linked and not routed. */
    DISCOVERED,
    /** Advertising nearby with an outbound connection attempt in flight. */
    SYNCING,
    /** Blocked and not currently visible anywhere in the mesh. */
    BLOCKED_OFFLINE
}

data class NodeItemData(
    val endpointId: String,
    /** Fully qualified node name. Always used for block/unblock/connect actions. */
    val name: String,
    val status: String,
    val kind: NodeKind = NodeKind.DISCOVERED,
    val isConnected: Boolean = false,
    val isActiveRelay: Boolean = false,
    val isBlocked: Boolean = false,
    /** Human friendly text for display only. Falls back to [name] when empty. */
    val label: String = ""
)

@Preview(showBackground = true)
@Composable
fun RadarScreenPreview() {
    val mockNodes = listOf(
        NodeItemData("id2", "Node_BK29 [MEDIC]#BK29", "Connected (Direct)", NodeKind.DIRECT, isConnected = true, isActiveRelay = true, label = "Node_BK29 [MEDIC] #BK29"),
        NodeItemData("id6", "Node_QQ12 [NODE]#QQ12", "Linking (Handshaking)", NodeKind.HANDSHAKING, isConnected = true, label = "Node 4F:A2"),
        NodeItemData("id4", "Node_MN04 [NODE]#MN04", "Connected (Via Relay)", NodeKind.RELAY, isActiveRelay = true, label = "Node_MN04 [NODE] #MN04"),
        NodeItemData("", "Node_L005 [NODE]#L005", "Hopped via Mesh", NodeKind.HOPPED, isActiveRelay = true, label = "Node_L005 [NODE] #L005"),
        NodeItemData("id1", "Node_X77A [NODE]#X77A", "Discovered / Scanning...", NodeKind.DISCOVERED, label = "Node_X77A [NODE] #X77A"),
        NodeItemData("id5", "Node_PJ88 [NODE]#PJ88", "SYNCING...", NodeKind.SYNCING, label = "Node_PJ88 [NODE] #PJ88")
    )
    TestResQMeshTheme {
        RadarScreenContent(
            activeNodesCount = mockNodes.size,
            nodes = mockNodes,
            topology = emptyMap(),
            myDeviceName = "Me [NODE]#ME01",
            onRefresh = {},
            onDisconnect = {},
            onForceConnect = { _, _ -> },
            onBlock = {},
            onUnblock = {}
        )
    }
}
