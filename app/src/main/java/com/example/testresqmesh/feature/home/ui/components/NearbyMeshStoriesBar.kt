package com.example.testresqmesh.feature.home.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.theme.ModernMint
import com.example.testresqmesh.core.ui.theme.ModernSky
import com.example.testresqmesh.core.ui.model.NodeItemData
import com.example.testresqmesh.core.ui.model.NodeKind

@Composable
internal fun NearbyMeshStoriesBar(
    nodes: List<NodeItemData>,
    directNodeNames: List<String> = emptyList(),
    isNodeActive: Boolean,
    connectionStatus: String,
    onPeerClick: (String) -> Unit,
    onNetworkClick: () -> Unit
) {
    val activePeers = remember(nodes, directNodeNames) {
        val classified = nodes.filter { !it.isBlocked && it.kind != NodeKind.OFFLINE && it.kind != NodeKind.BLOCKED_OFFLINE }
        if (classified.isNotEmpty()) {
            classified
        } else {
            directNodeNames.map { name ->
                NodeItemData(
                    endpointId = name,
                    name = name,
                    status = "Direct Link",
                    kind = NodeKind.DIRECT,
                    isConnected = true,
                    label = name
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Nearby Mesh Friends",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (activePeers.isNotEmpty()) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = activePeers.size.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Text(
                text = "View all",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onNetworkClick)
            )
        }

        if (activePeers.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNetworkClick),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WifiTethering,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = connectionStatus,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isNodeActive) "Recovery runs automatically when Bluetooth and permissions are available." else "Tap to check mesh permissions & status.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                activePeers.forEach { peer ->
                    val isDirect = peer.kind == NodeKind.DIRECT
                    val ringColor = if (isDirect) ModernMint else ModernSky
                    val initial = peer.name.firstOrNull()?.uppercase() ?: "P"

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { onPeerClick(peer.name) }
                            .padding(vertical = 4.dp)
                    ) {
                        Box {
                            Surface(
                                modifier = Modifier.size(56.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(2.5.dp, ringColor),
                                shadowElevation = 3.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = initial,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(13.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(ringColor)
                                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = peer.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(68.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}


/**
 * Community Incidents Banner styled like a modern social notification card.
 */
