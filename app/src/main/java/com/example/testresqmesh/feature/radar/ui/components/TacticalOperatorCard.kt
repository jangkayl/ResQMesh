package com.example.testresqmesh.feature.radar.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.components.feedback.ResQStatusChip
import com.example.testresqmesh.core.ui.model.NodeItemData
import com.example.testresqmesh.core.ui.model.NodeKind

@Composable
internal fun TacticalOperatorCard(node: NodeItemData, onClick: () -> Unit) {
    val status = networkStatus(node.kind)
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val accentColor = when {
        node.isBlocked -> Color(0xFFFF334B)
        node.kind == NodeKind.DIRECT -> Color(0xFF00E676)
        node.kind in setOf(NodeKind.RELAY, NodeKind.HOPPED) -> Color(0xFF00E5FF)
        node.kind in setOf(NodeKind.HANDSHAKING, NodeKind.SYNCING, NodeKind.UNRESPONSIVE) -> Color(0xFFFFB300)
        node.kind == NodeKind.DISCOVERED -> if (isLight) Color(0xFF64748B) else Color(0xFF8A99AD)
        else -> if (isLight) Color(0xFF94A3B8) else Color(0xFF536074)
    }

    val cardBg = if (isLight) MaterialTheme.colorScheme.surface else Color(0xFF10141E)
    val cardBorder = if (isLight) MaterialTheme.colorScheme.outlineVariant else Color(0xFF1E2638)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        shadowElevation = if (isLight) 2.dp else 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Glowing Left Status Accent Bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(42.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )

            Spacer(Modifier.width(12.dp))

            // Operator Avatar with Status Indicator
            Box(contentAlignment = Alignment.BottomEnd) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = accentColor.copy(alpha = if (isLight) 0.16f else 0.12f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = node.label.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = accentColor
                        )
                    }
                }

                // Mini connection dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(cardBg)
                        .padding(1.5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            // Peer Label & Monospace Metadata
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = node.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (node.isBlocked) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFF334B).copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = "BLOCKED",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFF334B),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(3.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    ResQStatusChip(status, networkStatusTone(node.kind))
                    if (node.endpointId.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "EP: ${node.endpointId.take(4).uppercase()}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Right Chevron
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Details",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Modernized Network Peer Details view featuring the Visual Holographic Known Mesh Path.
 */
