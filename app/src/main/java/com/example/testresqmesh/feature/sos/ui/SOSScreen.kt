package com.example.testresqmesh.feature.sos.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.core.ui.components.layout.ResQTopBar
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel

@Composable
fun SOSScreen(viewModel: CommunicationViewModel) {
    var isBroadcasting by remember { mutableStateOf(false) }
    
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Scaffold(
        topBar = { ResQTopBar(title = "Emergency SOS") }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Spacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Initiate Critical Alert",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(Spacing.Small))
                Text(
                    text = "This will flood the local mesh network with a high-priority SOS signal reaching all nearby nodes.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // Pulsing SOS Button
            Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f)) {
                if (isBroadcasting) {
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                    )
                }
                
                Surface(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { 
                            isBroadcasting = !isBroadcasting
                            if (isBroadcasting) {
                                // Simulate network flooding
                                viewModel.sendPublicMessage("🚨 CRITICAL SOS: I NEED HELP!", null, null)
                            }
                        },
                    color = if (isBroadcasting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape,
                    tonalElevation = 8.dp,
                    shadowElevation = 12.dp
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Warning, 
                            contentDescription = null, 
                            modifier = Modifier.size(48.dp),
                            tint = if (isBroadcasting) Color.White else MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(Spacing.Small))
                        Text(
                            text = if (isBroadcasting) "STOP SOS" else "SOS",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Black,
                            color = if (isBroadcasting) Color.White else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.Medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚠️",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.width(Spacing.Medium))
                    Text(
                        text = "Use only in life-threatening emergencies. Your identity and location will be shared with responders.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
