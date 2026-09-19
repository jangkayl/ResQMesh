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
    topology: Map<String, Set<String>>,
    myDeviceName: String,
    connectedNodes: List<String> = emptyList(),
    modifier: Modifier = Modifier
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
        if (topology.isEmpty() && topology.values.flatten().isEmpty() && connectedNodes.isEmpty()) {
            Text(
                "Scanning tactical mesh...",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Build node layers for radial layout
        val allNodes = mutableSetOf<String>()
        allNodes.add(myDeviceName)
        topology.keys.forEach { allNodes.add(it) }
        topology.values.flatten().forEach { allNodes.add(it) }
        connectedNodes.forEach { allNodes.add(it) }

        val centerNode = myDeviceName
        val ring1 = mutableSetOf<String>() // Direct connections
        
        topology[centerNode]?.let { ring1.addAll(it) }
        connectedNodes.forEach { ring1.add(it) }
        
        topology.forEach { (node, edges) ->
            if (edges.contains(centerNode)) ring1.add(node)
        }

        val ring2 = mutableSetOf<String>() // Indirect connections
        val ring3 = mutableSetOf<String>()
        
        allNodes.forEach { node ->
            if (node != centerNode && !ring1.contains(node)) {
                val connectedToRing1 = ring1.any { r1 ->
                    topology[r1]?.contains(node) == true || topology[node]?.contains(r1) == true
                }
                if (connectedToRing1) ring2.add(node) else ring3.add(node)
            }
        }

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

            // Draw Edges
            val drawnEdges = mutableSetOf<Pair<String, String>>()
            
            val drawEdge = { source: String, target: String ->
                val edge = if (source < target) Pair(source, target) else Pair(target, source)
                if (!drawnEdges.contains(edge)) {
                    drawnEdges.add(edge)
                    val pos1 = nodePositions[source]
                    val pos2 = nodePositions[target]
                    if (pos1 != null && pos2 != null) {
                        drawLine(color = primaryColor.copy(alpha = 0.2f), start = pos1, end = pos2, strokeWidth = 3f)
                    }
                }
            }
            
            connectedNodes.forEach { target -> drawEdge(centerNode, target) }
            topology.forEach { (source, targets) -> targets.forEach { target -> drawEdge(source, target) } }

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
