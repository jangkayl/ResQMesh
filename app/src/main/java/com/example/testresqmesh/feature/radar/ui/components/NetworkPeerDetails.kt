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
import androidx.compose.material.icons.automirrored.outlined.AltRoute
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.components.feedback.ResQStatusChip
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.model.NodeItemData
import com.example.testresqmesh.core.ui.model.NodeKind

@Composable
internal fun NetworkPeerDetails(
    node: NodeItemData,
    knownPath: List<String>,
    onBack: () -> Unit,
    onDisconnect: () -> Unit,
    onConnect: () -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
    onMessage: () -> Unit
) {
    var confirmBlock by remember { mutableStateOf(false) }
    val canMessage = !com.example.testresqmesh.core.model.NodeIdentity.isPlaceholder(node.name)
    val haptics = LocalHapticFeedback.current

    val accentColor = when {
        node.isBlocked -> Color(0xFFFF334B)
        node.kind == NodeKind.DIRECT -> Color(0xFF00E676)
        node.kind in setOf(NodeKind.RELAY, NodeKind.HOPPED) -> Color(0xFF00E5FF)
        else -> Color(0xFFFFB300)
    }

    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        // Top Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = if (isLight) MaterialTheme.colorScheme.surfaceVariant else Color(0xFF141926),
                border = BorderStroke(1.dp, if (isLight) MaterialTheme.colorScheme.outlineVariant else Color(0xFF242E44)),
                onClick = onBack
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.label,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = com.example.testresqmesh.core.model.NodeIdentity.idOf(node.name)?.let { "DEVICE ID: $it" }
                        ?: if (node.endpointId.isNotBlank()) "ENDPOINT: ${node.endpointId.uppercase()}" else "IDENTITY UNAVAILABLE",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
            ResQStatusChip(networkStatus(node.kind), networkStatusTone(node.kind))
        }

        // 1. Visual Holographic Known Mesh Path Card
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = if (isLight) MaterialTheme.colorScheme.surface else Color(0xFF0E121C),
            border = BorderStroke(1.2.dp, if (isLight) MaterialTheme.colorScheme.outlineVariant else Color(0xFF202A3E)),
            shadowElevation = if (isLight) 2.dp else 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.AltRoute,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "KNOWN MESH PATH",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            color = accentColor,
                            letterSpacing = 1.sp
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isLight) MaterialTheme.colorScheme.surfaceVariant else Color.White.copy(alpha = 0.06f)
                    ) {
                        Text(
                            text = if (knownPath.size <= 2) "DIRECT (0 HOPS)" else "${knownPath.size - 2} RELAY HOP(S)",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLight) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 2D Visual Route Map Canvas
                VisualHolographicPathMap(
                    path = knownPath,
                    targetLabel = node.label,
                    accentColor = accentColor
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = if (knownPath.isEmpty()) {
                        "Opportunistic multi-hop routing will discover path evidence when packets are transmitted."
                    } else {
                        "Packets relay peer-to-peer across hardware radios without requiring internet or cellular connectivity."
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )
            }
        }

        // 2. Hardware Diagnostics Strip
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (isLight) MaterialTheme.colorScheme.surface else Color(0xFF111520),
            border = BorderStroke(1.dp, if (isLight) MaterialTheme.colorScheme.outlineVariant else Color(0xFF1D2536)),
            shadowElevation = if (isLight) 2.dp else 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "LINK ENCRYPTION",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "AES-GCM VERIFIED",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF00E676)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "PROPAGATION MODE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (node.kind == NodeKind.DIRECT) "PEER-TO-PEER" else "MULTI-HOP FLOOD",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = accentColor
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // 3. Peer Action Buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (canMessage) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 8.dp,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onMessage()
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Chat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (node.isBlocked) "MESSAGE VIA MESH HOP" else "SEND MESSAGE",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (node.kind == NodeKind.DIRECT || node.kind == NodeKind.UNRESPONSIVE) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = if (isLight) Color(0xFFFFEBEE) else Color(0xFF1D1418),
                        border = BorderStroke(1.dp, if (isLight) Color(0xFFFFCDD2) else Color(0xFF4D222A)),
                        onClick = onDisconnect
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "DISCONNECT",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (isLight) Color(0xFFD32F2F) else Color(0xFFFF5252)
                            )
                        }
                    }
                }

                if (node.endpointId.isNotEmpty() && (node.kind == NodeKind.DISCOVERED || node.kind == NodeKind.SYNCING)) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = if (isLight) Color(0xFFE8F5E9) else Color(0xFF121E19),
                        border = BorderStroke(1.dp, if (isLight) Color(0xFFA5D6A7) else Color(0xFF1C4533)),
                        onClick = onConnect
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "CONNECT",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (isLight) Color(0xFF2E7D32) else Color(0xFF00E676)
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = if (node.isBlocked) {
                        if (isLight) MaterialTheme.colorScheme.surfaceVariant else Color(0xFF1A1F2C)
                    } else {
                        if (isLight) Color(0xFFFFEBEE) else Color(0xFF1A1417)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (node.isBlocked) {
                            if (isLight) MaterialTheme.colorScheme.outlineVariant else Color(0xFF2C354C)
                        } else {
                            if (isLight) Color(0xFFFFCDD2) else Color(0xFF402428)
                        }
                    ),
                    onClick = {
                        if (node.isBlocked) onUnblock() else confirmBlock = true
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (node.isBlocked) "UNBLOCK PEER" else "BLOCK PEER",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (node.isBlocked) {
                                if (isLight) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.8f)
                            } else {
                                if (isLight) Color(0xFFD32F2F) else Color(0xFFFF5252)
                            }
                        )
                    }
                }
            }
        }
    }

    if (confirmBlock) {
        AlertDialog(
            onDismissRequest = { confirmBlock = false },
            title = { Text("Block ${node.label}?") },
            text = { Text("This denies a direct link on both phones after acknowledgement. Each phone must unblock locally before direct contact can resume. Relayed messages may still be available.") },
            confirmButton = { TextButton(onClick = { confirmBlock = false; onBlock() }) { Text("Block", color = Color(0xFFFF5252)) } },
            dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text("Cancel") } }
        )
    }
}

/**
 * Interactive 2D Visual Map animating the mesh hops and data flow.
 */
