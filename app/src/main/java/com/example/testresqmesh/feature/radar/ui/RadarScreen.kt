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
import com.example.testresqmesh.core.utils.AppLogger

@Composable
fun RadarScreen(viewModel: RadarViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Create a set of names that are already connected for visual filtering
    val connectedNames = uiState.connectedDevices.map { it.name }.toSet()

    val connectedNodes = uiState.connectedDevices
        .distinctBy { it.name }
        .map { 
            val statusText = if (it.isClassicConnected) "Connected (Bluetooth)" else "Online"
            NodeItemData(it.endpointId, it.name, statusText, isConnected = true, isActiveRelay = true)
        }
    
    // VISUAL-ONLY FILTER: Hide any scanned node that has the same name as a connected one
    val scannedNodes = uiState.scannedDevices
        .filter { it.name !in connectedNames }
        .distinctBy { it.name }
        .map {
            val displayStatus = if (it.isConnecting) "SYNCING..." else "Offline"
            NodeItemData(
                it.endpointId,
                it.name, 
                displayStatus,
                isConnected = false,
                isActiveRelay = it.myRole == "MASTER" || it.isConnecting,
                isBlocked = uiState.blockedDeviceNames.contains(it.name)
            )
        }

    // Include blocked devices that are completely out of range so the user can still unblock them
    val scannedAndConnectedNames = uiState.connectedDevices.map { it.name }.toSet() + scannedNodes.map { it.name }
    
    val offlineBlockedNodes = uiState.blockedDeviceNames
        .filter { it !in scannedAndConnectedNames }
        .map { name ->
            NodeItemData(
                endpointId = "",
                name = name,
                status = "OFFLINE",
                isConnected = false,
                isActiveRelay = false,
                isBlocked = true
            )
        }

    val offlineTrackedNodes = uiState.knownNodes
        .filter { it.name !in scannedAndConnectedNames && !uiState.blockedDeviceNames.contains(it.name) }
        .map { node ->
            NodeItemData(
                endpointId = "",
                name = node.name,
                status = "OFFLINE (Last Seen: ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(node.lastSeen))})",
                isConnected = false,
                isActiveRelay = false,
                isBlocked = false
            )
        }

    RadarScreenContent(
        activeNodesCount = connectedNodes.size + scannedNodes.size,
        nodes = connectedNodes + scannedNodes + offlineBlockedNodes + offlineTrackedNodes,
        onRefresh = { viewModel.rescan() },
        onDisconnect = { viewModel.disconnectDevice(it) },
        onForceConnect = { id, name -> viewModel.forceConnect(id, name) },
        onBlock = { name -> viewModel.blockDevice(name) },
        onUnblock = { name -> viewModel.unblockDevice(name) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreenContent(
    activeNodesCount: Int,
    nodes: List<NodeItemData>,
    onRefresh: () -> Unit,
    onDisconnect: (String) -> Unit,
    onForceConnect: (String, String) -> Unit,
    onBlock: (String) -> Unit,
    onUnblock: (String) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition()
    val radarSweep by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

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

                Canvas(modifier = Modifier.size(240.dp)) {
                    val center = size.center
                    val radius = size.minDimension / 2
                    
                    // Circles
                    drawCircle(color = Color.White.copy(alpha = 0.05f), radius = radius)
                    drawCircle(color = Color.White.copy(alpha = 0.05f), radius = radius * 0.75f, style = Stroke(1.dp.toPx()))
                    drawCircle(color = Color.White.copy(alpha = 0.05f), radius = radius * 0.5f, style = Stroke(1.dp.toPx()))
                    drawCircle(color = Color.White.copy(alpha = 0.05f), radius = radius * 0.25f, style = Stroke(1.dp.toPx()))
                    
                    // Crosshair lines
                    drawLine(color = Color.White.copy(alpha = 0.1f), start = androidx.compose.ui.geometry.Offset(0f, center.y), end = androidx.compose.ui.geometry.Offset(size.width, center.y))
                    drawLine(color = Color.White.copy(alpha = 0.1f), start = androidx.compose.ui.geometry.Offset(center.x, 0f), end = androidx.compose.ui.geometry.Offset(center.x, size.height))

                    // Sweep
                    drawArc(
                        color = InboxAccentBlue.copy(alpha = 0.3f),
                        startAngle = radarSweep,
                        sweepAngle = 60f,
                        useCenter = true,
                        size = size
                    )
                    
                    // Dynamic Nodes (the orange dots)
                    nodes.forEach { node ->
                        // Deterministic position based on name
                        val random = java.util.Random(node.name.hashCode().toLong())
                        val angle = random.nextFloat() * 360f
                        val distance = (0.3f + random.nextFloat() * 0.6f) * radius
                        
                        val x = center.x + distance * kotlin.math.cos(Math.toRadians(angle.toDouble())).toFloat()
                        val y = center.y + distance * kotlin.math.sin(Math.toRadians(angle.toDouble())).toFloat()
                        
                        drawCircle(
                            color = Color(0xFFF97316), 
                            radius = 5.dp.toPx(), 
                            center = androidx.compose.ui.geometry.Offset(x, y)
                        )
                    }

                    // Center point (Me)
                    drawCircle(color = Color.White, radius = 4.dp.toPx(), center = center)
                    drawCircle(color = InboxAccentBlue, radius = 8.dp.toPx(), center = center, style = Stroke(2.dp.toPx()))
                }
                
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
                val connectedNodesList = nodes.filter { it.status.contains("Connected", ignoreCase = true) }
                val onlineNodesList = nodes.filter { it.status.contains("Online", ignoreCase = true) || it.status.contains("SYNCING") }
                val offlineNodesList = nodes.filter { it.status.contains("OFFLINE", ignoreCase = true) }

                if (connectedNodesList.isNotEmpty()) {
                    Text("CONNECTED", style = MaterialTheme.typography.labelSmall, color = InboxAccentBlue, modifier = Modifier.padding(top = Spacing.Small))
                    connectedNodesList.forEach { node ->
                        NearbyNodeItem(node, onDisconnect, onForceConnect, onBlock, onUnblock)
                    }
                }

                if (onlineNodesList.isNotEmpty()) {
                    Text("ONLINE", style = MaterialTheme.typography.labelSmall, color = Color(0xFF10B981), modifier = Modifier.padding(top = Spacing.Small))
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
                Text(node.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black, color = if (node.isBlocked) Color.Gray else Color.White)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusColor = if (node.isBlocked) Color.Red else if (node.status.contains("MASTER") || node.isConnected) InboxAccentBlue else Color.White.copy(alpha = 0.4f)
                    Icon(
                        if (node.isBlocked) Icons.Default.Block else if (node.status.contains("MASTER") || node.isConnected) Icons.Default.Bolt else Icons.Default.Info, 
                        contentDescription = null, 
                        tint = statusColor, 
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (node.isBlocked) "BLOCKED" else node.status, style = MaterialTheme.typography.labelSmall, color = statusColor)
                }
            }

            if (node.isBlocked) {
                TextButton(
                    onClick = { onUnblock(node.name) },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF10B981))
                ) {
                    Text("UNBLOCK", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                }
            } else if (node.isConnected) {
                IconButton(onClick = { onBlock(node.name) }) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = "Block Device",
                        tint = Color.Gray
                    )
                }
                IconButton(onClick = { onDisconnect(node.endpointId) }) {
                    Icon(
                        imageVector = Icons.Outlined.LinkOff,
                        contentDescription = "Unlink Device",
                        tint = Color(0xFFEF4444)
                    )
                }
            } else {
                IconButton(onClick = { onBlock(node.name) }) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = "Block Device",
                        tint = Color.Gray
                    )
                }
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
    }
}

data class NodeItemData(
    val endpointId: String,
    val name: String,
    val status: String,
    val isConnected: Boolean = false,
    val isActiveRelay: Boolean = false,
    val isBlocked: Boolean = false
)

@Preview(showBackground = true)
@Composable
fun RadarScreenPreview() {
    val mockNodes = listOf(
        NodeItemData("id1", "Node_X77A", "120m • End Device"),
        NodeItemData("id2", "Node_BK29", "250m • Active Relay", isConnected = true, isActiveRelay = true),
        NodeItemData("id3", "Node_L005", "410m • End Device"),
        NodeItemData("id4", "Node_MN04", "680m • Active Relay", isConnected = false, isActiveRelay = true),
        NodeItemData("id5", "Node_PJ88", "910m • End Device")
    )
    TestResQMeshTheme {
        RadarScreenContent(
            activeNodesCount = 6,
            nodes = mockNodes,
            onRefresh = {},
            onDisconnect = {},
            onForceConnect = { _, _ -> },
            onBlock = {},
            onUnblock = {}
        )
    }
}
