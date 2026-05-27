package com.example.testresqmesh.feature.radar.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextAlign
import com.example.testresqmesh.core.ui.components.buttons.ResQButton
import com.example.testresqmesh.core.ui.components.layout.ResQCard
import com.example.testresqmesh.core.ui.theme.*
import com.example.testresqmesh.feature.radar.viewmodel.RadarViewModel
import com.example.testresqmesh.core.utils.AppLogger

@Composable
fun RadarScreen(viewModel: RadarViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    
    val nodes = uiState.connectedDevices.map { 
        NodeItemData(it.endpointId, it.name, "Connected", isConnected = true)
    } + uiState.scannedDevices.map {
        NodeItemData(it.endpointId, it.name, "Nearby", isConnected = false)
    }

    DashboardContent(
        nodes = nodes,
        activeNodesCount = nodes.size,
        onRefresh = { viewModel.rescan() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    nodes: List<NodeItemData>,
    activeNodesCount: Int,
    onRefresh: () -> Unit
) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(modifier = Modifier.size(32.dp), shape = CircleShape, color = PrimaryRed) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("R", color = WarmWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("ResQMesh", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = DarkGray)
                    }
                },
                actions = {
                    IconButton(onClick = { AppLogger.toggleTerminal() }) {
                        Icon(Icons.Outlined.Shield, contentDescription = "Debug", tint = MediumGray)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        },
        containerColor = AppBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = Spacing.Medium)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // SOS Hero Card
            ResQCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = PrimaryRed,
                cornerRadius = 32.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "EMERGENCY ASSISTANCE",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarmWhite.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))

                    // Pulse SOS Button
                    Surface(
                        onClick = { /* SOS Logic */ },
                        modifier = Modifier.size(140.dp),
                        shape = CircleShape,
                        color = WarmWhite,
                        shadowElevation = 8.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "SOS",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Black,
                                color = PrimaryRed
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Press and hold to alert nearby responders",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WarmWhite,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Status Cards Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatusCard(
                    icon = Icons.Default.SignalWifiStatusbar4Bar,
                    label = "Network",
                    value = if (activeNodesCount > 0) "Healthy" else "Searching...",
                    modifier = Modifier.weight(1f)
                )
                StatusCard(
                    icon = Icons.Default.BatteryChargingFull,
                    label = "Battery",
                    value = "84%",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Mesh Network Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Nearby Safety Network",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkGray
                )
                TextButton(onClick = onRefresh) {
                    Text("Sync", color = PrimaryRed, fontWeight = FontWeight.SemiBold)
                }
            }

            ResQCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = 1.dp,
                cornerRadius = 24.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (nodes.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Scanning for nearby ResQMesh devices...", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                    } else {
                        nodes.forEachIndexed { index, node ->
                            NearbyNodeRow(node)
                            if (index < nodes.size - 1) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = LightGray)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun StatusCard(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    ResQCard(
        modifier = modifier,
        cornerRadius = 24.dp,
        elevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = PrimaryRed, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = DarkGray)
        }
    }
}

@Composable
fun NearbyNodeRow(node: NodeItemData) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = OffWhite) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, contentDescription = null, tint = MediumGray, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(node.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = DarkGray)
            Text(node.status, style = MaterialTheme.typography.labelSmall, color = if (node.isConnected) SuccessGreen else TextMuted)
        }
        if (node.isConnected) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
        }
    }
}

data class NodeItemData(
    val endpointId: String,
    val name: String,
    val status: String,
    val isConnected: Boolean
)
