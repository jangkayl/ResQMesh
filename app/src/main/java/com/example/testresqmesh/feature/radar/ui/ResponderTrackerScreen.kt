package com.example.testresqmesh.feature.radar.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.ui.components.buttons.ButtonVariant
import com.example.testresqmesh.core.ui.components.buttons.ResQButton
import com.example.testresqmesh.core.ui.components.feedback.ResQStatusChip
import com.example.testresqmesh.core.ui.components.feedback.ResQStatusTone
import com.example.testresqmesh.core.ui.components.layout.ResQGlassSurface
import com.example.testresqmesh.core.ui.theme.ResQSize
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.feature.comms.ui.components.UserAvatar

/**
 * Modernized tactical peer inspector and connection timeline screen.
 * Displays step-by-step multi-hop transmission path, node verification, and mesh metrics.
 */
@Composable
fun ResponderTrackerScreen(
    nodeName: String,
    onBack: () -> Unit,
    onChat: () -> Unit
) {
    val displayName = remember(nodeName) {
        NodeIdentity.displayNameOf(nodeName).ifBlank { nodeName }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulseTimeline")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseProgress"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(ResQSize.MinimumTouchTarget)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(Spacing.Small))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Node Route & Path",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Tactical Mesh Signal Intelligence",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Hero Target Node Profile Card
        ResQGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            contentPadding = PaddingValues(Spacing.Large),
            shadowElevation = 10.dp
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Target Avatar with tactical glow ring
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    )
                    UserAvatar(name = nodeName, size = 56.dp)
                }

                Spacer(Modifier.height(Spacing.Small))

                Text(
                    text = displayName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(Spacing.ExtraSmall))

                ResQStatusChip(
                    label = "VERIFIED MESH ENDPOINT",
                    tone = ResQStatusTone.Success,
                    icon = Icons.Outlined.Shield
                )
            }
        }

        // Step-by-Step Connection Timeline (The Mesh Path)
        ResQGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            contentPadding = PaddingValues(Spacing.Large),
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Transmission Timeline",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "1-2 Hops Max",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(Spacing.Large))

                // Timeline Step 1: Origin Device (You)
                TimelineNodeItem(
                    nodeLabel = "Host Node (You)",
                    subLabel = "Origin Transmitter · BLE 2.4 GHz",
                    icon = Icons.Outlined.Smartphone,
                    accentColor = ResQTheme.colors.success,
                    isDone = true
                )

                // Timeline Connector Line with dynamic pulse
                TimelineConnector(pulseProgress = pulseProgress)

                // Timeline Step 2: Mesh Relay Layer
                TimelineNodeItem(
                    nodeLabel = "Tactical Mesh Relay",
                    subLabel = "Peer Flooding Hop · Ad-Hoc Dynamic Route",
                    icon = Icons.Outlined.Hub,
                    accentColor = MaterialTheme.colorScheme.primary,
                    isDone = true
                )

                // Timeline Connector Line
                TimelineConnector(pulseProgress = (pulseProgress + 0.5f) % 1f)

                // Timeline Step 3: Destination Node
                TimelineNodeItem(
                    nodeLabel = displayName,
                    subLabel = "Destination Peer · Ready for Delivery",
                    icon = Icons.Outlined.Radio,
                    accentColor = ResQTheme.colors.glowPrimary,
                    isDone = true
                )
            }
        }

        // Tactical Route Metrics Grid (2x2)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            MetricCard(
                title = "ROUTING MODE",
                value = "Relayed Hop",
                icon = Icons.Outlined.Route,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "SECURITY",
                value = "ECDSA Signed",
                icon = Icons.Outlined.Lock,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            MetricCard(
                title = "NETWORK PHY",
                value = "BLE Adv Mesh",
                icon = Icons.Outlined.WifiTethering,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "LATENCY EST",
                value = "< 250 ms",
                icon = Icons.Outlined.Speed,
                modifier = Modifier.weight(1f)
            )
        }

        // Location Info Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.padding(Spacing.Medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.Medium))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Location Sharing",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "GPS beacons are attached only in verified emergency alerts or direct map shares.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.Small))

        // Action Buttons
        ResQButton(
            onClick = onChat,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null)
            Spacer(Modifier.width(Spacing.Small))
            Text("Message Peer", fontWeight = FontWeight.Bold)
        }

        ResQButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            variant = ButtonVariant.Outline
        ) {
            Text("Back to Radar", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(Spacing.Medium))
    }
}

@Composable
private fun TimelineNodeItem(
    nodeLabel: String,
    subLabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    isDone: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = CircleShape,
            color = accentColor.copy(alpha = 0.15f),
            border = BorderStroke(1.5.dp, accentColor)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = nodeLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isDone) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = ResQTheme.colors.success,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun TimelineConnector(pulseProgress: Float) {
    Box(
        modifier = Modifier
            .padding(start = 18.dp)
            .height(28.dp)
            .width(2.dp)
    ) {
        val primaryColor = MaterialTheme.colorScheme.primary
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawLine(
                color = primaryColor.copy(alpha = 0.25f),
                start = Offset(size.width / 2f, 0f),
                end = Offset(size.width / 2f, size.height),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            )
            // Flowing signal particle pulse
            val pulseY = size.height * pulseProgress
            drawCircle(
                color = primaryColor,
                radius = 2.5.dp.toPx(),
                center = Offset(size.width / 2f, pulseY)
            )
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
        }
    }
}

