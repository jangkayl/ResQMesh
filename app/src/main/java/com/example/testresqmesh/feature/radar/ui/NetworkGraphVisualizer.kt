package com.example.testresqmesh.feature.radar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.theme.InboxAccentBlue
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun NetworkGraphVisualizer(
    topology: Map<String, Set<String>>,
    myDeviceName: String,
    connectedNodes: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(Color(0xFF0F172A), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        if (topology.isEmpty() && topology.values.flatten().isEmpty() && connectedNodes.isEmpty()) {
            Text(
                "No mesh connections active.",
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.Center)
            )
            return@Box
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
        
        // Also check if anyone claims to be connected to centerNode
        topology.forEach { (node, edges) ->
            if (edges.contains(centerNode)) {
                ring1.add(node)
            }
        }

        val ring2 = mutableSetOf<String>() // Indirect connections
        val ring3 = mutableSetOf<String>()
        
        allNodes.forEach { node ->
            if (node != centerNode && !ring1.contains(node)) {
                // Is it connected to ring1?
                val connectedToRing1 = ring1.any { r1 ->
                    topology[r1]?.contains(node) == true || topology[node]?.contains(r1) == true
                }
                if (connectedToRing1) {
                    ring2.add(node)
                } else {
                    ring3.add(node)
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = minOf(size.width, size.height) / 2f - 20.dp.toPx()
            
            val ring1Radius = maxRadius * 0.4f
            val ring2Radius = maxRadius * 0.7f
            val ring3Radius = maxRadius * 1.0f

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
            
            // Draw implicit direct connections
            connectedNodes.forEach { target ->
                val source = centerNode
                val edge = if (source < target) Pair(source, target) else Pair(target, source)
                if (!drawnEdges.contains(edge)) {
                    drawnEdges.add(edge)
                    val pos1 = nodePositions[source]
                    val pos2 = nodePositions[target]
                    if (pos1 != null && pos2 != null) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.3f),
                            start = pos1,
                            end = pos2,
                            strokeWidth = 3f
                        )
                    }
                }
            }

            topology.forEach { (source, targets) ->
                targets.forEach { target ->
                    val edge = if (source < target) Pair(source, target) else Pair(target, source)
                    if (!drawnEdges.contains(edge)) {
                        drawnEdges.add(edge)
                        val pos1 = nodePositions[source]
                        val pos2 = nodePositions[target]
                        if (pos1 != null && pos2 != null) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.3f),
                                start = pos1,
                                end = pos2,
                                strokeWidth = 3f
                            )
                        }
                    }
                }
            }

            // Draw Nodes
            nodePositions.forEach { (node, pos) ->
                val color = when {
                    node == centerNode -> Color(0xFF10B981) // Green
                    ring1.contains(node) -> InboxAccentBlue
                    else -> Color.Gray
                }
                val radius = if (node == centerNode) 12.dp.toPx() else 8.dp.toPx()
                
                drawCircle(
                    color = color,
                    radius = radius,
                    center = pos
                )
                
                drawCircle(
                    color = Color.White,
                    radius = radius,
                    center = pos,
                    style = Stroke(width = 2f)
                )
            }
        }
    }
}
