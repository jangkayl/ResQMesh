package com.example.testresqmesh.feature.sos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel

@Composable
fun ActiveSOSMonitoringScreen(commsViewModel: CommunicationViewModel, onResolve: () -> Unit) {
    val id by commsViewModel.activeSosMessageId.collectAsState()
    val chat by commsViewModel.uiState.collectAsState()
    val message = chat.publicMessages.find { it.id == id }
    val receipts = message?.deliveredTo?.toList().orEmpty()
    val red = Color(0xFFE5484D)
    Column(Modifier.fillMaxSize().background(Color(0xFFFBFAFF)).padding(Spacing.Large), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(28.dp))
        Surface(Modifier.size(80.dp), CircleShape, color = red.copy(alpha = .12f)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Warning, null, tint = red, modifier = Modifier.size(40.dp)) } }
        Spacer(Modifier.height(16.dp))
        Text("SOS active", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
        Text("Keep this alert active until help is no longer needed.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(24.dp))
        Surface(Modifier.fillMaxWidth().weight(1f), RoundedCornerShape(28.dp), color = Color.White, shadowElevation = 4.dp) {
            Column(Modifier.padding(20.dp)) {
                Text(if (message?.locationLat != null && message.locationLng != null) "Location attached" else "Location unavailable", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                Text("Delivery receipts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(6.dp))
                if (receipts.isEmpty()) Text("No receipt yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(receipts) { name -> Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF18A66A)); Spacer(Modifier.width(10.dp)); Text(name) } } }
            }
        }
        Spacer(Modifier.height(18.dp))
        Button(onClick = onResolve, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(22.dp), colors = ButtonDefaults.buttonColors(containerColor = red)) { Text("Resolve SOS", fontWeight = FontWeight.Bold) }
    }
}
