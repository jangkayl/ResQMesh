package edu.cit.resqmesh.feature.home.ui.components

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
import edu.cit.resqmesh.core.ui.theme.ModernMint
import edu.cit.resqmesh.core.ui.theme.RelayOrange
import edu.cit.resqmesh.core.ui.theme.ResQTheme
import edu.cit.resqmesh.core.ui.model.NodeItemData
import edu.cit.resqmesh.core.ui.model.NodeKind

/**
 * Peers shown on Home: recently offline peers stay visible after active ones, and blocked peers are
 * shown last (drawn red, never counted as reachable).
 */
internal fun homeStoryPeers(nodes: List<NodeItemData>): List<NodeItemData> =
    nodes.sortedBy {
        when {
            it.isBlocked -> 2
            it.kind == NodeKind.OFFLINE -> 1
            else -> 0
        }
    }

/** Ring tone for a Home peer, matching the Network screen's link states. */
internal enum class HomeRingTone { Blocked, Direct, Relayed, Checking, Nearby, Offline }

/**
 * Only a live direct link is green and only a routed multi-hop peer is orange. Checking/connecting
 * links are amber, and a peer that is merely visible or has dropped off the mesh is gray, so a
 * relayed peer that goes offline never keeps the relay color.
 */
internal fun homeRingTone(peer: NodeItemData): HomeRingTone = when {
    peer.isBlocked -> HomeRingTone.Blocked
    else -> when (peer.kind) {
        NodeKind.DIRECT -> HomeRingTone.Direct
        NodeKind.RELAY, NodeKind.HOPPED -> HomeRingTone.Relayed
        NodeKind.UNRESPONSIVE, NodeKind.HANDSHAKING, NodeKind.SYNCING -> HomeRingTone.Checking
        NodeKind.DISCOVERED -> HomeRingTone.Nearby
        NodeKind.OFFLINE, NodeKind.BLOCKED_OFFLINE -> HomeRingTone.Offline
    }
}

@Composable
private fun homeRingColor(tone: HomeRingTone) = when (tone) {
    HomeRingTone.Blocked -> ResQTheme.colors.sos
    HomeRingTone.Direct -> ModernMint
    HomeRingTone.Relayed -> RelayOrange
    HomeRingTone.Checking -> ResQTheme.colors.warning
    HomeRingTone.Nearby, HomeRingTone.Offline -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
internal fun NearbyMeshStoriesBar(
    nodes: List<NodeItemData>,
    directNodeNames: List<String> = emptyList(),
    isNodeActive: Boolean,
    connectionStatus: String,
    onPeerClick: (String) -> Unit,
    onNetworkClick: () -> Unit
) {
    val storyPeers = remember(nodes, directNodeNames) {
        val classified = homeStoryPeers(nodes)
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
    val activePeerCount = storyPeers.count { it.kind != NodeKind.OFFLINE && !it.isBlocked }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "People Nearby",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (activePeerCount > 0) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = activePeerCount.toString(),
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

        if (storyPeers.isEmpty()) {
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
                            text = if (isNodeActive) "We will keep trying once Bluetooth is on." else "Tap to check mesh permissions & status.",
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
                storyPeers.forEach { peer ->
                    val tone = homeRingTone(peer)
                    val isOffline = tone == HomeRingTone.Offline
                    val ringColor = homeRingColor(tone)
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
                                        color = if (isOffline || peer.isBlocked) {
                                            ringColor
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
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
