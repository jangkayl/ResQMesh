package com.example.testresqmesh.ui.screens.radar

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.ui.theme.InboxBackground
import com.example.testresqmesh.ui.theme.InboxAccentBlue
import com.example.testresqmesh.ui.theme.Spacing
import com.example.testresqmesh.ui.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(viewModel: ChatViewModel) {
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
                    IconButton(onClick = { /* Security settings */ }) {
                        Icon(Icons.Outlined.Shield, contentDescription = null, tint = Color.White)
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
                        Text("6 ACTIVE NODES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.White)
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
                    
                    // Mock Nodes (the orange dots)
                    drawCircle(color = Color(0xFFF97316), radius = 6.dp.toPx(), center = androidx.compose.ui.geometry.Offset(center.x + 80, center.y - 60))
                    drawCircle(color = Color(0xFFF97316), radius = 5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(center.x - 90, center.y + 70))
                    drawCircle(color = Color(0xFFF97316), radius = 7.dp.toPx(), center = androidx.compose.ui.geometry.Offset(center.x + 40, center.y + 90))
                    drawCircle(color = Color(0xFFF97316), radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(center.x - 60, center.y - 100))

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
                        Icon(Icons.Default.TrendingUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Network Status", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Mesh Protocol: v2.4 Active", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                        }
                        Surface(color = Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp)) {
                            Text("Healthy", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        }
                    }
                    
                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.1f))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatusMetric("NODES", "06")
                        StatusMetric("DEPTH", "3 Hops")
                        StatusMetric("RANGE", "~800m")
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.Large))

            // Nearby Nodes List
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Medium),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("NEARBY NODES", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = Color.White)
                TextButton(onClick = {}) {
                    Text("Refresh", color = InboxAccentBlue, style = MaterialTheme.typography.labelMedium)
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = InboxAccentBlue, modifier = Modifier.size(16.dp))
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Medium),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
                val mockNodes = listOf(
                    NodeItemData("Node_X77A", "120m • End Device"),
                    NodeItemData("Node_BK29", "250m • Active Relay", true),
                    NodeItemData("Node_L005", "410m • End Device"),
                    NodeItemData("Node_MN04", "680m • Active Relay", true),
                    NodeItemData("Node_PJ88", "910m • End Device")
                )
                
                items(mockNodes) { node ->
                    NearbyNodeItem(node)
                }

                item {
                    Spacer(modifier = Modifier.height(Spacing.Large))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SignalCellularAlt, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AES-256 MESH-TUNNEL ESTABLISHED", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.2f), fontSize = 8.sp)
                    }
                    Spacer(modifier = Modifier.height(Spacing.Large))
                }
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
fun NearbyNodeItem(node: NodeItemData) {
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
                Text(node.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black, color = Color.White)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(12.dp))
                    Text(node.status, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Icon(Icons.Default.SignalCellularAlt, contentDescription = null, tint = Color(0xFFF97316))
                Text("CONNECTED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, fontSize = 8.sp, color = Color.White.copy(alpha = 0.4f))
            }
        }
    }
}

data class NodeItemData(val name: String, val status: String, val isActiveRelay: Boolean = false)
