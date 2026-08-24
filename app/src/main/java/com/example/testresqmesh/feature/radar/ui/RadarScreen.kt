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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import androidx.compose.ui.tooling.preview.Preview
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.feature.radar.viewmodel.RadarViewModel
import com.example.testresqmesh.core.utils.AppLogger
import com.example.testresqmesh.core.ui.components.GlassSurface
import com.example.testresqmesh.core.ui.components.TacticalButton
import com.example.testresqmesh.core.ui.components.TacticalButtonVariant

@Composable
fun RadarScreen(viewModel: RadarViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    
    val connectedNames = uiState.connectedDevices.map { it.name }.toSet()
    val connectedNodes = uiState.connectedDevices.map { 
        NodeItemData(it.endpointId, it.name, "Connected • Mesh Peer", isConnected = true, isActiveRelay = true)
    }
    
    val scannedNodes = uiState.scannedDevices
        .filter { it.name !in connectedNames }
        .map {
            val displayStatus = if (it.isConnecting) "SYNCING..." else "Score: ${it.powerScore} • Role: ${it.myRole}"
            NodeItemData(
                it.endpointId,
                it.name, 
                displayStatus,
                isConnected = false,
                isActiveRelay = it.myRole == "MASTER" || it.isConnecting,
                isBlocked = uiState.blockedDeviceNames.contains(it.name)
            )
        }

    val scannedAndConnectedNames = connectedNames + scannedNodes.map { it.name }
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

    RadarScreenContent(
        activeNodesCount = uiState.connectedDevices.size + scannedNodes.size,
        nodes = connectedNodes + scannedNodes + offlineBlockedNodes,
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
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val radarSweep by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SweepAnimation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "TACTICAL RADAR", 
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black, 
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 2.sp
                    ) 
                },
                actions = {
                    IconButton(onClick = { AppLogger.toggleTerminal() }) {
                        Icon(Icons.Outlined.Shield, contentDescription = "Debug Terminal", tint = Color.White)
                    }
                    IconButton(onClick = { /* Mesh settings */ }) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
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
                GlassSurface(
                    shape = RoundedCornerShape(16.dp),
                    intensity = 0.2f,
                    modifier = Modifier.align(Alignment.TopStart).padding(Spacing.Medium)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "$activeNodesCount ACTIVE NODES", 
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black, 
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }

                val primaryColor = MaterialTheme.colorScheme.primary

                Canvas(modifier = Modifier.size(240.dp)) {
                    val center = size.center
                    val radius = size.minDimension / 2
                    
                    // Circles
                    drawCircle(color = primaryColor.copy(alpha = 0.05f), radius = radius)
                    drawCircle(color = primaryColor.copy(alpha = 0.1f), radius = radius * 0.75f, style = Stroke(1.dp.toPx()))
                    drawCircle(color = primaryColor.copy(alpha = 0.1f), radius = radius * 0.5f, style = Stroke(1.dp.toPx()))
                    drawCircle(color = primaryColor.copy(alpha = 0.1f), radius = radius * 0.25f, style = Stroke(1.dp.toPx()))
                    
                    // Crosshair lines
                    drawLine(color = primaryColor.copy(alpha = 0.1f), start = androidx.compose.ui.geometry.Offset(0f, center.y), end = androidx.compose.ui.geometry.Offset(size.width, center.y))
                    drawLine(color = primaryColor.copy(alpha = 0.1f), start = androidx.compose.ui.geometry.Offset(center.x, 0f), end = androidx.compose.ui.geometry.Offset(center.x, size.height))

                    // Sweep
                    drawArc(
                        color = primaryColor.copy(alpha = 0.3f),
                        startAngle = radarSweep,
                        sweepAngle = 60f,
                        useCenter = true,
                        size = size
                    )
                    
                    // Dynamic Nodes (the primary colored dots)
                    nodes.forEach { node ->
                        // Deterministic position based on name
                        val random = java.util.Random(node.name.hashCode().toLong())
                        val angle = random.nextFloat() * 360f
                        val distance = (0.3f + random.nextFloat() * 0.6f) * radius
                        
                        val x = center.x + distance * kotlin.math.cos(Math.toRadians(angle.toDouble())).toFloat()
                        val y = center.y + distance * kotlin.math.sin(Math.toRadians(angle.toDouble())).toFloat()
                        
                        drawCircle(
                            color = if (node.isBlocked) Color.Gray else primaryColor, 
                            radius = 5.dp.toPx(), 
                            center = androidx.compose.ui.geometry.Offset(x, y)
                        )
                    }

                    // Center point (Me)
                    drawCircle(color = Color.White, radius = 4.dp.toPx(), center = center)
                    drawCircle(color = primaryColor, radius = 8.dp.toPx(), center = center, style = Stroke(2.dp.toPx()))
                }
                
                Text(
                    "SCAN RANGE: 1.2KM",
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.Medium),
                    fontSize = 12.sp
                )
            }

            // Network Status Card
            GlassSurface(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Medium),
                intensity = 0.15f
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Network Status", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Mesh Protocol: v2.4 Active", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))
                        }
                        Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp)) {
                            Text("HEALTHY", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        }
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatusMetric("NODES", String.format(Locale.getDefault(), "%02d", activeNodesCount))
                        StatusMetric("DEPTH", "3 Hops")
                        StatusMetric("RANGE", "~800m")
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.Large))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Medium),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("NEARBY NODES", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, color = Color.White)
                TextButton(onClick = onRefresh) {
                    Text("Refresh", color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Medium),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
                nodes.forEach { node ->
                    NearbyNodeItem(node, onDisconnect, onForceConnect, onBlock, onUnblock)
                }
                
                if (nodes.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Text("NO NODES DETECTED", color = Color.White.copy(alpha = 0.4f), fontFamily = FontFamily.Monospace)
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
                        fontFamily = FontFamily.Monospace,
                        color = Color.White.copy(alpha = 0.2f),
                        fontSize = 8.sp
                    )
                }
                Spacer(modifier = Modifier.height(100.dp)) // padding for the nav bar
            }
        }
    }
}

@Composable
fun StatusMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.4f))
        Text(value, fontFamily = FontFamily.Monospace, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White)
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
    GlassSurface(
        shape = RoundedCornerShape(12.dp),
        intensity = 0.1f,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box {
                Surface(modifier = Modifier.size(44.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.White.copy(alpha = 0.6f))
                    }
                }
                if (node.isActiveRelay) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .align(Alignment.TopEnd)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = Color.White, modifier = Modifier.padding(2.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.width(Spacing.Medium))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.name, 
                    fontFamily = FontFamily.Monospace, 
                    fontWeight = FontWeight.Black, 
                    color = if (node.isBlocked) Color.White.copy(alpha = 0.4f) else Color.White
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusColor = if (node.isBlocked) MaterialTheme.colorScheme.error else if (node.status.contains("MASTER") || node.isConnected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                    Icon(
                        if (node.isBlocked) Icons.Default.Block else if (node.status.contains("MASTER") || node.isConnected) Icons.Default.Bolt else Icons.Default.Info, 
                        contentDescription = null, 
                        tint = statusColor, 
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (node.isBlocked) "BLOCKED" else node.status, 
                        fontFamily = FontFamily.Monospace, 
                        fontSize = 10.sp, 
                        color = statusColor
                    )
                }
            }

            if (node.isBlocked) {
                TacticalButton(
                    onClick = { onUnblock(node.name) },
                    variant = TacticalButtonVariant.Danger
                ) {
                    Text("UNBLOCK", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            } else if (node.isConnected) {
                IconButton(onClick = { onBlock(node.name) }) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = "Block Device",
                        tint = Color.White.copy(alpha = 0.4f)
                    )
                }
                IconButton(onClick = { onDisconnect(node.endpointId) }) {
                    Icon(
                        imageVector = Icons.Outlined.LinkOff,
                        contentDescription = "Unlink Device",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                IconButton(onClick = { onBlock(node.name) }) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = "Block Device",
                        tint = Color.White.copy(alpha = 0.4f)
                    )
                }
                TextButton(
                    onClick = { onForceConnect(node.endpointId, node.name) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("FORCE", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Black)
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
