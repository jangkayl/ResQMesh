package com.example.testresqmesh.feature.radar.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun VisualHolographicPathMap(
    path: List<String>,
    targetLabel: String,
    accentColor: Color
) {
    val displayPath = remember(path, targetLabel) {
        if (path.isEmpty()) listOf("You", targetLabel) else path
    }

    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val pulseColor = if (isLight) MaterialTheme.colorScheme.primary else Color.White

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
            .background(if (isLight) MaterialTheme.colorScheme.surfaceVariant else Color(0xFF090C12))
            .border(BorderStroke(1.dp, if (isLight) MaterialTheme.colorScheme.outlineVariant else Color(0xFF182030)), RoundedCornerShape(16.dp))
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
                color = if (isLight) Color(0xFFCBD5E1) else Color(0xFF1F2B40),
                start = Offset(startX, cy),
                end = Offset(endX, cy),
                strokeWidth = 3.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
                cap = StrokeCap.Round
            )

            // Active Glowing Circuit Line
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(if (isLight) Color(0xFF2E7D32) else Color(0xFF00E676), accentColor)
                ),
                start = Offset(startX, cy),
                end = Offset(endX, cy),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Traveling Glowing Data Pulse Packet
            val pulseX = startX + (endX - startX) * pulseProgress
            drawCircle(
                color = pulseColor,
                radius = 4.dp.toPx(),
                center = Offset(pulseX, cy)
            )
            drawCircle(
                color = accentColor.copy(alpha = if (isLight) 0.3f else 0.5f),
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
                    isStart -> if (isLight) Color(0xFF2E7D32) else Color(0xFF00E676)
                    isEnd -> accentColor
                    else -> if (isLight) Color(0xFFD97706) else Color(0xFFFFB300)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = if (isLight) MaterialTheme.colorScheme.surface else Color(0xFF0B1019),
                        border = BorderStroke(2.dp, nodeTint),
                        shadowElevation = if (isLight) 2.dp else 6.dp
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
                        color = if (isStart || isEnd) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
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
