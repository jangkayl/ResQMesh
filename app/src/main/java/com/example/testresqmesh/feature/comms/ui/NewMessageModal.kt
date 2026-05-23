package com.example.testresqmesh.feature.comms.ui

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.theme.InboxBackground
import com.example.testresqmesh.core.ui.theme.InboxAccentBlue
import com.example.testresqmesh.core.ui.theme.Spacing

import com.example.testresqmesh.ui.state.ChatUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewMessageModal(uiState: ChatUiState, onDismiss: () -> Unit, onNodeSelected: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .background(Color(0xFF232E35)) // Surface color
            .padding(Spacing.Medium)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("New Mesh Thread", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Color.White)
                Text("Select a node to establish a link", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f))
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(Spacing.Large))

        // Search Bar
        Surface(
            color = Color.Black.copy(alpha = 0.4f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Search Mesh ID or Name...", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(modifier = Modifier.height(Spacing.Medium))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {},
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = InboxAccentBlue),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Broadcast to Local", fontWeight = FontWeight.Bold)
            }
            Surface(
                onClick = {},
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(10.dp),
                color = Color.White.copy(alpha = 0.05f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = InboxAccentBlue)
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.Large))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.SignalCellularAlt, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("CURRENTLY IN RANGE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.weight(1f))
            Surface(color = Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp)) {
                Text("4 Active", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
            }
        }

        Spacer(modifier = Modifier.height(Spacing.Medium))

        val nodesInRange = uiState.knownNodes.map { node ->
            RangeNode(
                name = node.name, 
                id = "MESH-NODE", 
                distance = if (node.isDirect) "🟢 DIRECT LINK" else "🌐 MESH HOP"
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(nodesInRange) { node ->
                ModalNodeItem(node, onNodeSelected)
            }
            
            item {
                Spacer(modifier = Modifier.height(Spacing.Large))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PREVIOUSLY CONNECTED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.weight(1f))
                    Text("12 Cached", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
                }
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

@Composable
fun ModalNodeItem(node: RangeNode, onClick: (String) -> Unit) {
    Surface(
        onClick = { onClick(node.name) },
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Surface(modifier = Modifier.size(52.dp), shape = CircleShape, color = Color.White.copy(alpha = 0.1f)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
                    }
                }
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                        .align(Alignment.BottomEnd)
                        .border(2.dp, Color(0xFF232E35), CircleShape)
                )
            }
            
            Spacer(modifier = Modifier.width(Spacing.Medium))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(node.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Outlined.Shield, contentDescription = null, tint = InboxAccentBlue, modifier = Modifier.size(14.dp))
                }
                Text("ID: ${node.id}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SignalCellularAlt, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(12.dp))
                    Text(node.distance, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                if (node.distance.contains("HOP")) {
                    Icon(Icons.Outlined.Shield, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.2f))
            }
        }
    }
}

data class RangeNode(val name: String, val id: String, val distance: String)
