package com.example.testresqmesh.feature.sos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.core.ui.components.buttons.SosSlideToSend
import com.example.testresqmesh.core.ui.theme.Spacing

@Composable
fun SOSBroadcastScreen(onCancel: () -> Unit, onSosTriggered: (String) -> Unit = {}) {
    var selectedType by remember { mutableStateOf("General") }
    val emergency = Color(0xFFE5484D)
    val types = listOf("Medical", "Fire", "Trapped", "General")
    Column(Modifier.fillMaxSize().background(Color(0xFFFBFAFF)).padding(Spacing.Large), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onCancel, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Close, "Cancel emergency alert") }
        }
        Surface(Modifier.size(92.dp), CircleShape, color = emergency.copy(alpha = .12f)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Warning, null, tint = emergency, modifier = Modifier.size(46.dp)) } }
        Spacer(Modifier.height(20.dp))
        Text("Send an SOS", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text("Choose the emergency, then slide to send.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        Text("Emergency type", modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            types.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { type ->
                        val selected = type == selectedType
                        Surface(modifier = Modifier.weight(1f).height(68.dp).clickable { selectedType = type }, shape = RoundedCornerShape(22.dp), color = if (selected) emergency else Color.White, border = BorderStroke(1.dp, if (selected) emergency else Color(0xFFE7E2F0)), shadowElevation = if (selected) 4.dp else 0.dp) {
                            Box(contentAlignment = Alignment.Center) { Text(type, fontWeight = FontWeight.Bold, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text("Slide only when you need help", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        SosSlideToSend(onSlideComplete = { onSosTriggered(selectedType.uppercase()) })
        Spacer(Modifier.height(14.dp))
        Text("Your alert uses available nearby connections. Location is included only when available.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
