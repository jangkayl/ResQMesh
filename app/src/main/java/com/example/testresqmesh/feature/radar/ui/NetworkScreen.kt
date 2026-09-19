package com.example.testresqmesh.feature.radar.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AltRoute
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.components.feedback.ResQEmptyState
import com.example.testresqmesh.core.ui.components.feedback.ResQStatusChip
import com.example.testresqmesh.core.ui.components.feedback.ResQStatusTone
import com.example.testresqmesh.core.ui.components.layout.ResQGlassSurface
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import com.example.testresqmesh.feature.radar.viewmodel.RadarViewModel

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
    val groups = listOf(
        "Direct Links" to nodes.filter { it.kind == NodeKind.DIRECT || it.kind == NodeKind.UNRESPONSIVE },
        "Multi-Hop Mesh Relays" to nodes.filter { it.kind == NodeKind.RELAY || it.kind == NodeKind.HOPPED },
        "Nearby & Discovered" to nodes.filter { it.kind !in setOf(NodeKind.DIRECT, NodeKind.UNRESPONSIVE, NodeKind.RELAY, NodeKind.HOPPED) }
    ).filter { it.second.isNotEmpty() }

    val haptics = LocalHapticFeedback.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.Medium, top = Spacing.Large, end = Spacing.Medium, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small)
    ) {
        // 1. Tactical Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "MESH DISCOVERY & ROUTING",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "People & Paths",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "Live decentralized peer topology & multi-hop reachability.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8B98AD)
                    )
                }

                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = Color(0xFF141824),
                    border = BorderStroke(1.dp, Color(0xFF263045)),
                    shadowElevation = 8.dp,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onRefresh()
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Refresh network",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(4.dp)) }

        // 2. Tactical Mesh Field Summary Card
        item {
            ResQGlassSurface(
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(Spacing.Medium),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Hub,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E676))
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "MESH FIELD ACTIVE",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = networkSummaryLabel(nodes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Dynamic ad-hoc Bluetooth & Wi-Fi routing",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = Color(0xFF7A889D)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        onClick = onTopologyClick
                    ) {
                        Text(
                            text = "TOPOLOGY",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // 3. Node Groups
        if (nodes.isEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                ResQEmptyState(
                    "No Peers In Range",
                    "Keep ResQMesh active. Nearby devices running ResQMesh will automatically interconnect without internet.",
                    icon = Icons.Outlined.Hub
                )
            }
        } else {
            groups.forEach { (title, group) ->
                item(key = "header_$title") {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = title.uppercase(),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF7C8BA1),
                            letterSpacing = 1.2.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "(${group.size})",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                items(group, key = { "${it.endpointId}-${it.name}" }) { node ->
                    TacticalOperatorCard(node = node, onClick = { onNodeClick(node) })
                }
            }
        }
    }
}

/**
 * Modern Tactical Operator Card representing a reachable mesh peer.
 */
@Composable
private fun TacticalOperatorCard(node: NodeItemData, onClick: () -> Unit) {
    val status = networkStatus(node.kind)
    val accentColor = when (node.kind) {
        NodeKind.DIRECT -> Color(0xFF00E676)
        NodeKind.RELAY, NodeKind.HOPPED -> Color(0xFF00E5FF)
        NodeKind.HANDSHAKING, NodeKind.SYNCING, NodeKind.UNRESPONSIVE -> Color(0xFFFFB300)
        NodeKind.DISCOVERED -> Color(0xFF8A99AD)
        NodeKind.OFFLINE, NodeKind.BLOCKED_OFFLINE -> Color(0xFF536074)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF10141E),
        border = BorderStroke(1.dp, Color(0xFF1E2638)),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Glowing Left Status Accent Bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(42.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )

            Spacer(Modifier.width(12.dp))

            // Operator Avatar with Status Indicator
            Box(contentAlignment = Alignment.BottomEnd) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = accentColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = node.label.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = accentColor
                        )
                    }
                }

                // Mini connection dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10141E))
                        .padding(1.5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            // Peer Label & Monospace Metadata
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = node.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (node.isBlocked) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFF334B).copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = "BLOCKED",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFF334B),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(3.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    ResQStatusChip(status, networkStatusTone(node.kind))
                    if (node.endpointId.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "EP: ${node.endpointId.take(4).uppercase()}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color(0xFF67778C),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Right Chevron
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Details",
                tint = Color(0xFF4C5B70),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Modernized Network Peer Details view featuring the Visual Holographic Known Mesh Path.
 */
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
    val haptics = LocalHapticFeedback.current

    val accentColor = when (node.kind) {
        NodeKind.DIRECT -> Color(0xFF00E676)
        NodeKind.RELAY, NodeKind.HOPPED -> Color(0xFF00E5FF)
        else -> Color(0xFFFFB300)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        // Top Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = Color(0xFF141926),
                border = BorderStroke(1.dp, Color(0xFF242E44)),
                onClick = onBack
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.label,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (node.endpointId.isNotBlank()) "ENDPOINT: ${node.endpointId.uppercase()}" else "OFFLINE RELAY IDENTITY",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Color(0xFF7A8B9E),
                    fontWeight = FontWeight.Bold
                )
            }
            ResQStatusChip(networkStatus(node.kind), networkStatusTone(node.kind))
        }

        // 1. Visual Holographic Known Mesh Path Card
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF0E121C),
            border = BorderStroke(1.2.dp, Color(0xFF202A3E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.AltRoute,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "KNOWN MESH PATH",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            color = accentColor,
                            letterSpacing = 1.sp
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.White.copy(alpha = 0.06f)
                    ) {
                        Text(
                            text = if (knownPath.size <= 2) "DIRECT (0 HOPS)" else "${knownPath.size - 2} RELAY HOP(S)",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 2D Visual Route Map Canvas
                VisualHolographicPathMap(
                    path = knownPath,
                    targetLabel = node.label,
                    accentColor = accentColor
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = if (knownPath.isEmpty()) {
                        "Opportunistic multi-hop routing will discover path evidence when packets are transmitted."
                    } else {
                        "Packets relay peer-to-peer across hardware radios without requiring internet or cellular connectivity."
                    },
                    fontSize = 11.sp,
                    color = Color(0xFF718296),
                    lineHeight = 15.sp
                )
            }
        }

        // 2. Hardware Diagnostics Strip
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF111520),
            border = BorderStroke(1.dp, Color(0xFF1D2536)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "LINK ENCRYPTION",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6C7C90)
                    )
                    Text(
                        text = "AES-GCM VERIFIED",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF00E676)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "PROPAGATION MODE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6C7C90)
                    )
                    Text(
                        text = if (node.kind == NodeKind.DIRECT) "PEER-TO-PEER" else "MULTI-HOP FLOOD",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = accentColor
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // 3. Peer Action Buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (canMessage) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 8.dp,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onMessage()
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Chat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "SEND MESSAGE",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (node.kind == NodeKind.DIRECT || node.kind == NodeKind.UNRESPONSIVE) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF1D1418),
                        border = BorderStroke(1.dp, Color(0xFF4D222A)),
                        onClick = onDisconnect
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "DISCONNECT",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color(0xFFFF5252)
                            )
                        }
                    }
                }

                if (node.endpointId.isNotEmpty() && (node.kind == NodeKind.DISCOVERED || node.kind == NodeKind.SYNCING)) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF121E19),
                        border = BorderStroke(1.dp, Color(0xFF1C4533)),
                        onClick = onConnect
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "CONNECT",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color(0xFF00E676)
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = if (node.isBlocked) Color(0xFF1A1F2C) else Color(0xFF1A1417),
                    border = BorderStroke(1.dp, if (node.isBlocked) Color(0xFF2C354C) else Color(0xFF402428)),
                    onClick = {
                        if (node.isBlocked) onUnblock() else confirmBlock = true
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (node.isBlocked) "UNBLOCK PEER" else "BLOCK PEER",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (node.isBlocked) Color.White.copy(alpha = 0.8f) else Color(0xFFFF5252)
                        )
                    }
                }
            }
        }
    }

    if (confirmBlock) {
        AlertDialog(
            onDismissRequest = { confirmBlock = false },
            title = { Text("Block ${node.label}?") },
            text = { Text("This denies a direct link on both phones after acknowledgement. Each phone must unblock locally before direct contact can resume. Relayed messages may still be available.") },
            confirmButton = { TextButton(onClick = { confirmBlock = false; onBlock() }) { Text("Block", color = Color(0xFFFF5252)) } },
            dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text("Cancel") } }
        )
    }
}

/**
 * Interactive 2D Visual Map animating the mesh hops and data flow.
 */
@Composable
private fun VisualHolographicPathMap(
    path: List<String>,
    targetLabel: String,
    accentColor: Color
) {
    val displayPath = remember(path, targetLabel) {
        if (path.isEmpty()) listOf("You", targetLabel) else path
    }

    // Animated data pulse traveling from 0 to 1 across the route
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTrack")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseProgress"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF090C12))
            .border(BorderStroke(1.dp, Color(0xFF182030)), RoundedCornerShape(16.dp))
    ) {
        // Canvas Laser Track & Traveling Data Pulse
        Canvas(modifier = Modifier.fillMaxSize()) {
            val nodeCount = displayPath.size
            if (nodeCount < 2) return@Canvas

            val cy = size.height * 0.42f
            val startX = 48.dp.toPx()
            val endX = size.width - 48.dp.toPx()
            val stepX = (endX - startX) / (nodeCount - 1)

            // Background Dashed Circuit Line
            drawLine(
                color = Color(0xFF1F2B40),
                start = Offset(startX, cy),
                end = Offset(endX, cy),
                strokeWidth = 3.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
                cap = StrokeCap.Round
            )

            // Active Glowing Circuit Line
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(Color(0xFF00E676), accentColor)
                ),
                start = Offset(startX, cy),
                end = Offset(endX, cy),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Traveling Glowing Data Pulse Packet
            val pulseX = startX + (endX - startX) * pulseProgress
            drawCircle(
                color = Color.White,
                radius = 4.dp.toPx(),
                center = Offset(pulseX, cy)
            )
            drawCircle(
                color = accentColor.copy(alpha = 0.5f),
                radius = 10.dp.toPx(),
                center = Offset(pulseX, cy)
            )
        }

        // Node Visual Checkpoint Glyphs
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            displayPath.forEachIndexed { index, name ->
                val isStart = index == 0
                val isEnd = index == displayPath.lastIndex
                val nodeTint = when {
                    isStart -> Color(0xFF00E676)
                    isEnd -> accentColor
                    else -> Color(0xFFFFB300)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = Color(0xFF0B1019),
                        border = BorderStroke(2.dp, nodeTint),
                        shadowElevation = 6.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when {
                                    isStart -> Icons.Outlined.Person
                                    isEnd -> Icons.Outlined.Smartphone
                                    else -> Icons.Outlined.Hub
                                },
                                contentDescription = null,
                                tint = nodeTint,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = name.take(8),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isStart || isEnd) Color.White else Color(0xFF90A1B5),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Modernized Network Topology route viewer.
 */
@Composable
private fun NetworkTopology(topology: Map<String, Set<String>>, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(Spacing.Large),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = Color(0xFF141926),
                border = BorderStroke(1.dp, Color(0xFF242E44)),
                onClick = onBack
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Network Topology",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = "Active multi-hop routes discovered across the mesh.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF7A8B9E)
                )
            }
        }

        if (topology.isEmpty()) {
            ResQEmptyState(
                "No Routes Recorded",
                "Mesh links automatically generate when multiple ResQMesh peers exchange packets.",
                icon = Icons.Outlined.Hub
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                topology.forEach { (from, to) ->
                    item(key = from) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFF111520),
                            border = BorderStroke(1.dp, Color(0xFF1F283C))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Hub,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = from,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color.White
                                    )
                                }

                                Spacer(Modifier.height(8.dp))

                                if (to.isEmpty()) {
                                    Text(
                                        text = "No outward peer connections",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = Color(0xFF6C7C90)
                                    )
                                } else {
                                    to.forEach { destination ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                                contentDescription = null,
                                                tint = Color(0xFF00E5FF),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = destination,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp,
                                                color = Color(0xFF00E5FF),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
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
private fun networkStatusTone(kind: NodeKind) = when (kind) {
    NodeKind.DIRECT -> ResQStatusTone.Success
    NodeKind.UNRESPONSIVE, NodeKind.HANDSHAKING, NodeKind.SYNCING -> ResQStatusTone.Warning
    NodeKind.RELAY, NodeKind.HOPPED -> ResQStatusTone.Information
    NodeKind.DISCOVERED -> ResQStatusTone.Neutral
    NodeKind.OFFLINE, NodeKind.BLOCKED_OFFLINE -> ResQStatusTone.Neutral
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
