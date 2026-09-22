package com.example.testresqmesh.feature.radar.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.core.ui.theme.InboxAccentBlue
import com.example.testresqmesh.core.ui.theme.ResQTheme

@Composable
fun NetworkGraphVisualizer(
    nodes: List<NodeItemData>,
    myDeviceName: String,
    modifier: Modifier = Modifier,
    showEmptyScanPrompt: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val radarAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarAngle"
    )
    val ringPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringPulse"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(8.dp)
    ) {
        val graphNodes = nodes.filter { it.kind != NodeKind.OFFLINE && it.kind != NodeKind.BLOCKED_OFFLINE }
        if (showEmptyScanPrompt && graphNodes.isEmpty()) {
            Text(
                "Scanning tactical mesh...",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        val centerNode = myDeviceName
        // Consume the same canonical peer rows as the list. Raw topology strings previously made
        // truncated aliases and leased disconnected fragments appear as unexplained extra dots.
        val ring1 = graphNodes.filter { it.kind == NodeKind.DIRECT }.map { it.name }.toSet()
        val ring2 = graphNodes.filter {
            it.kind == NodeKind.UNRESPONSIVE || it.kind == NodeKind.HANDSHAKING || it.kind == NodeKind.RELAY
        }.map { it.name }.toSet()
        val ring3 = graphNodes.filter {
            it.kind == NodeKind.HOPPED || it.kind == NodeKind.DISCOVERED || it.kind == NodeKind.SYNCING
        }.map { it.name }.toSet()

        val primaryColor = ResQTheme.colors.glowPrimary
        val successColor = ResQTheme.colors.success
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = minOf(size.width, size.height) / 2f - 24.dp.toPx()
            
            val ring1Radius = maxRadius * 0.45f
            val ring2Radius = maxRadius * 0.75f
            val ring3Radius = maxRadius * 1.0f

            // Draw animated tactical rings
            drawCircle(color = primaryColor.copy(alpha = 0.1f + (0.05f * ringPulse)), radius = ring1Radius, center = center, style = Stroke(width = 2f))
            drawCircle(color = primaryColor.copy(alpha = 0.05f + (0.02f * ringPulse)), radius = ring2Radius, center = center, style = Stroke(width = 1f))
            
            // Draw Sweeping Radar Line
            rotate(degrees = radarAngle, pivot = center) {
                val sweepGradient = Brush.sweepGradient(
                    colors = listOf(Color.Transparent, primaryColor.copy(alpha = 0.3f), primaryColor.copy(alpha = 0.8f)),
                    center = center
                )
                drawArc(
                    brush = sweepGradient,
                    startAngle = 0f,
                    sweepAngle = 60f,
                    useCenter = true,
                    topLeft = Offset(center.x - maxRadius, center.y - maxRadius),
                    size = Size(maxRadius * 2, maxRadius * 2)
                )
            }

            val nodePositions = mutableMapOf<String, Offset>()
            nodePositions[centerNode] = center

            fun positionRing(nodes: Set<String>, radius: Float) {
                val list = nodes.toList()
                val angleStep = (2 * Math.PI) / (list.size.coerceAtLeast(1))
                list.forEachIndexed { index, node ->
                    val angle = index * angleStep
                    val x = center.x + radius * Math.cos(angle).toFloat()
                    val y = center.y + radius * Math.sin(angle).toFloat()
                    nodePositions[node] = Offset(x, y)
                }
            }

            positionRing(ring1, ring1Radius)
            positionRing(ring2, ring2Radius)
            positionRing(ring3, ring3Radius)

            ring1.forEach { target ->
                nodePositions[target]?.let { targetPosition ->
                    drawLine(color = primaryColor.copy(alpha = 0.2f), start = center, end = targetPosition, strokeWidth = 3f)
                }
            }

            // Draw Nodes
            nodePositions.forEach { (node, pos) ->
                val (color, sizeDp) = when {
                    node == centerNode -> Pair(successColor, 14.dp.toPx())
                    ring1.contains(node) -> Pair(primaryColor, 10.dp.toPx())
                    else -> Pair(Color.Gray, 6.dp.toPx())
                }
                
                // Glow effect for active nodes
                if (node == centerNode || ring1.contains(node)) {
                    drawCircle(color = color.copy(alpha = 0.3f), radius = sizeDp * 1.5f, center = pos)
                }
                
                drawCircle(color = color, radius = sizeDp, center = pos)
            }
        }
    }
}
