package com.example.testresqmesh.feature.radar.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.components.feedback.ResQEmptyState
import com.example.testresqmesh.core.ui.components.layout.ResQGlassSurface
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.model.NodeItemData
import com.example.testresqmesh.core.ui.model.NodeKind

@Composable
internal fun NetworkList(
    nodes: List<NodeItemData>,
    onRefresh: () -> Unit,
    onNodeClick: (NodeItemData) -> Unit,
    onTopologyClick: () -> Unit
) {
    val groups = listOf(
        "Direct Links" to nodes.filter { !it.isBlocked && (it.kind == NodeKind.DIRECT || it.kind == NodeKind.UNRESPONSIVE) },
        "Multi-Hop Mesh Relays" to nodes.filter { !it.isBlocked && (it.kind == NodeKind.RELAY || it.kind == NodeKind.HOPPED) },
        "Nearby & Discovered" to nodes.filter { !it.isBlocked && it.kind !in setOf(NodeKind.DIRECT, NodeKind.UNRESPONSIVE, NodeKind.RELAY, NodeKind.HOPPED) },
        "Blocked Devices (Direct Link Denied)" to nodes.filter { it.isBlocked }
    ).filter { it.second.isNotEmpty() }

    val haptics = LocalHapticFeedback.current
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.Medium, top = Spacing.Large, end = Spacing.Medium, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small)
    ) {
        // 1. Tactical Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "MESH DISCOVERY & ROUTING",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "People & Paths",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Live decentralized peer topology & multi-hop reachability.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = if (isLight) MaterialTheme.colorScheme.surfaceVariant else Color(0xFF141824),
                    border = BorderStroke(1.dp, if (isLight) MaterialTheme.colorScheme.outlineVariant else Color(0xFF263045)),
                    shadowElevation = if (isLight) 2.dp else 8.dp,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onRefresh()
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Refresh network",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(4.dp)) }

        // 2. Tactical Mesh Field Summary Card
        item {
            ResQGlassSurface(
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(Spacing.Medium),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Hub,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E676))
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "MESH FIELD ACTIVE",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = networkSummaryLabel(nodes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Dynamic ad-hoc Bluetooth & Wi-Fi routing",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        onClick = onTopologyClick
                    ) {
                        Text(
                            text = "TOPOLOGY",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // 3. Node Groups
        if (nodes.isEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                ResQEmptyState(
                    "No Peers In Range",
                    "Keep ResQMesh active. Nearby devices running ResQMesh will automatically interconnect without internet.",
                    icon = Icons.Outlined.Hub
                )
            }
        } else {
            groups.forEach { (title, group) ->
                item(key = "header_$title") {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = title.uppercase(),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "(${group.size})",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                items(group, key = { "${it.endpointId}-${it.name}" }) { node ->
                    TacticalOperatorCard(node = node, onClick = { onNodeClick(node) })
                }
            }
        }
    }
}

/**
 * Modern Tactical Operator Card representing a reachable mesh peer.
 */
