package com.example.testresqmesh.feature.radar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.components.buttons.ButtonVariant
import com.example.testresqmesh.core.ui.components.buttons.ResQButton
import com.example.testresqmesh.core.ui.components.layout.ResQTopBar
import com.example.testresqmesh.core.ui.theme.Spacing

@Composable
fun ResponderTrackerScreen(nodeName: String, onBack: () -> Unit, onChat: () -> Unit) {
    Scaffold(
        topBar = {
            ResQTopBar(
                title = "Tracking Responder",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    Surface(
                        color = Color.White.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("LIVE MESH", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Spacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Radar Distance Visualization
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .padding(Spacing.Medium),
                contentAlignment = Alignment.Center
            ) {
                // Compass-like background
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2
                    drawCircle(color = Color.White.copy(alpha = 0.05f), radius = radius)
                    drawCircle(color = Color.White.copy(alpha = 0.05f), radius = radius * 0.7f, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                    drawCircle(color = Color.White.copy(alpha = 0.05f), radius = radius * 0.4f, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                }
                
                // Target dot
                Surface(
                    modifier = Modifier.size(12.dp).align(Alignment.Center),
                    color = Color.White,
                    shape = CircleShape
                ) {}
                
                // North indicator
                Icon(Icons.Default.North, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.TopCenter).size(24.dp))
                Text("N", modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                Text("S", modifier = Modifier.align(Alignment.BottomCenter), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                Text("E", modifier = Modifier.align(Alignment.CenterEnd), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                Text("W", modifier = Modifier.align(Alignment.CenterStart), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
            }

            Text(
                text = "ESTIMATED DISTANCE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = Color.White.copy(alpha = 0.4f),
                letterSpacing = 1.sp
            )
            
            Text(
                text = "~150m",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Node Details Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(Spacing.Medium)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("TARGET ID", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.4f))
                            Text(nodeName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
                        }
                        Surface(color = Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(12.dp)) {
                            Text("Signal: Weak", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        }
                    }
                    
                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.1f))
                    
                    Text("ROUTING PATH METADATA", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PathNodeIcon(true)
                        PathDottedLine()
                        PathNodeIcon(false)
                        PathDottedLine()
                        PathNodeIcon(false)
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("3 HOPS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Encryption Status", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("E2EE Active", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Surface(
                onClick = onChat,
                modifier = Modifier.fillMaxWidth(),
                color = Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Row(modifier = Modifier.padding(Spacing.Medium), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Send Mesh Message", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Direct relay via multi-hop", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.2f))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            ResQButton(
                onClick = {},
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Broadcast My Position", fontWeight = FontWeight.Black)
            }

            Spacer(modifier = Modifier.height(12.dp))

            ResQButton(
                onClick = {},
                variant = ButtonVariant.Outline,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Default.Wifi, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Attempt Direct Peer Link", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun PathNodeIcon(isMe: Boolean) {
    Surface(
        modifier = Modifier.size(24.dp),
        shape = CircleShape,
        color = if (isMe) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isMe) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (isMe) Icons.Default.LocationOn else Icons.Default.Wifi, 
                contentDescription = null, 
                modifier = Modifier.size(12.dp),
                tint = if (isMe) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun PathDottedLine() {
    Text(" •••• ", color = Color.White.copy(alpha = 0.2f), fontWeight = FontWeight.Black)
}
