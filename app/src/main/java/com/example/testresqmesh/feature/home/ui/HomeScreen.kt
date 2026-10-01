package com.example.testresqmesh.feature.home.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.feature.comms.ui.ConversationPreview
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.R
import com.example.testresqmesh.core.location.LocationStatus
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.ui.components.layout.ResQGlassSurface
import com.example.testresqmesh.core.ui.theme.ModernMint
import com.example.testresqmesh.core.ui.theme.ModernSky
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import com.example.testresqmesh.feature.radar.ui.NetworkGraphVisualizer
import com.example.testresqmesh.feature.radar.ui.NodeItemData
import com.example.testresqmesh.feature.radar.ui.NodeKind
import com.example.testresqmesh.feature.radar.ui.classifyRadarNodes
import com.example.testresqmesh.feature.radar.viewmodel.RadarViewModel
import com.example.testresqmesh.feature.setup.viewmodel.SetupViewModel
import com.example.testresqmesh.ui.state.RadarUiState

@Composable
fun HomeScreen(
    setupViewModel: SetupViewModel,
    radarViewModel: RadarViewModel,
    commsViewModel: CommunicationViewModel? = null,
    locationStatus: LocationStatus = LocationStatus.READY,
    onMessagesClick: () -> Unit,
    onNetworkClick: () -> Unit,
    onPeerClick: (String) -> Unit,
    onProfileClick: () -> Unit,
    onVoiceClick: (() -> Unit)? = null,
    onIncidentsClick: (() -> Unit)? = null,
    onChatSelected: ((String) -> Unit)? = null,
    onCommunityClick: (() -> Unit)? = null,
    activeIncidentCount: Int = 0,
    criticalIncidentCount: Int = 0
) {
    val connectionState by setupViewModel.uiState.collectAsState()
    val radarState by radarViewModel.uiState.collectAsState()
    val summary = remember(radarState) { homeNetworkSummary(radarState) }
    val context = LocalContext.current
    val myDeviceName = remember(context) {
        val prefs = context.getSharedPreferences("resqmesh_prefs", android.content.Context.MODE_PRIVATE)
        val customName = prefs.getString("custom_name", android.os.Build.MODEL) ?: android.os.Build.MODEL
        val tag = prefs.getString("node_tag", "") ?: ""
        val nodeId = prefs.getString("node_id", "") ?: ""
        if (nodeId.isEmpty()) customName else NodeIdentity.qualifiedName(customName, tag, nodeId)
    }
    val nodes = remember(radarState) { classifyRadarNodes(radarState) }
    val directNodeNames = remember(nodes) { nodes.filter { it.kind == NodeKind.DIRECT }.map { it.name } }

    HomeScreenContent(
        isNodeActive = connectionState.isOnline,
        summary = summary,
        radarState = radarState,
        nodes = nodes,
        myDeviceName = myDeviceName,
        directNodeNames = directNodeNames,
        locationStatus = locationStatus,
        onMessagesClick = onMessagesClick,
        onNetworkClick = onNetworkClick,
        onPeerClick = onPeerClick,
        onProfileClick = onProfileClick,
        onVoiceClick = onVoiceClick,
        onIncidentsClick = onIncidentsClick,
        onChatSelected = onChatSelected,
        onCommunityClick = onCommunityClick,
        activeIncidentCount = activeIncidentCount,
        criticalIncidentCount = criticalIncidentCount
    )
}

@Composable
fun HomeScreenContent(
    isNodeActive: Boolean,
    summary: HomeNetworkSummary,
    radarState: RadarUiState,
    myDeviceName: String,
    directNodeNames: List<String>,
    locationStatus: LocationStatus = LocationStatus.READY,
    onMessagesClick: () -> Unit,
    onNetworkClick: () -> Unit,
    onPeerClick: (String) -> Unit = { onNetworkClick() },
    onProfileClick: () -> Unit,
    onVoiceClick: (() -> Unit)? = null,
    onIncidentsClick: (() -> Unit)? = null,
    onChatSelected: ((String) -> Unit)? = null,
    onCommunityClick: (() -> Unit)? = null,
    channelId: String = "1",
    communityPreview: ChatMessage? = null,
    conversations: List<ConversationPreview> = emptyList(),
    onChannelSelected: (String) -> Unit = {},
    activeIncidentCount: Int = 0,
    criticalIncidentCount: Int = 0,
    modifier: Modifier = Modifier,
    nodes: List<NodeItemData> = emptyList()
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.Large)
            .padding(top = Spacing.Large, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.Large)
    ) {
        // 1. Modern Social Header (Avatar, Greeting, Live Mesh Presence Pill)
        ModernUserHeader(
            myDeviceName = myDeviceName,
            isNodeActive = isNodeActive,
            summary = summary,
            onProfileClick = onProfileClick
        )

        // 2. "Nearby Mesh Friends" Stories/Presence Bar
        NearbyMeshStoriesBar(
            nodes = if (nodes.isNotEmpty()) nodes else classifyRadarNodes(radarState),
            directNodeNames = directNodeNames,
            isNodeActive = isNodeActive,
            onPeerClick = { peerName ->
                onChatSelected?.invoke(peerName) ?: onPeerClick(peerName)
            },
            onNetworkClick = onNetworkClick
        )

        // 3. Community Incident Triage Banner ("EMERGENCY INCIDENTS")
        if (onIncidentsClick != null) {
            CommunityIncidentsBanner(
                activeIncidentCount = activeIncidentCount,
                criticalIncidentCount = criticalIncidentCount,
                onIncidentsClick = onIncidentsClick
            )
        }

        // 6. Modern Mesh Network Summary Card ("Your network")
        ModernMeshTopologyCard(
            summary = summary,
            radarState = radarState,
            myDeviceName = myDeviceName,
            directNodeNames = directNodeNames,
            onNetworkClick = onNetworkClick,
            onPeerClick = { peerName ->
                onChatSelected?.invoke(peerName) ?: onPeerClick(peerName)
            }
        )

        // 7. Integrated Satellite GPS Status Pill ("Location found")
        ModernGpsStatusCard(locationStatus = locationStatus)

        // 8. Reassuring Safety Guidance Footer
        ModernSafetyFooter()
    }
}

/**
 * Modern consumer-grade user header with avatar, name, and clean presence status pill.
 */
@Composable
private fun ModernUserHeader(
    myDeviceName: String,
    isNodeActive: Boolean,
    summary: HomeNetworkSummary,
    onProfileClick: () -> Unit
) {
    val displayName = NodeIdentity.displayNameOf(myDeviceName)
    val nodeId = remember(myDeviceName) {
        if (myDeviceName.contains('#')) "#" + myDeviceName.substringAfter('#') else ""
    }
    val initial = displayName.firstOrNull()?.uppercase() ?: "U"

    val readinessTitle = stringResource(
        if (isNodeActive) R.string.home_ready_title else R.string.home_setup_title
    )
    val meshStatusColor = if (isNodeActive) {
        if (summary.directPeers > 0) ResQTheme.colors.success else ModernSky
    } else {
        ResQTheme.colors.warning
    }

    val infiniteTransition = rememberInfiniteTransition(label = "BeaconPulse")
    val beaconAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beaconAlpha"
    )

    // Profile & Presence Row
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            // User Avatar Circle
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(onClick = onProfileClick),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                shadowElevation = 3.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Hello, $displayName",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (nodeId.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = nodeId,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(3.dp))

                // Live Mesh Presence Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = meshStatusColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, meshStatusColor.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .graphicsLayer { alpha = if (isNodeActive) beaconAlpha else 0.4f }
                                .clip(CircleShape)
                                .background(meshStatusColor)
                        )
                        Text(
                            text = readinessTitle,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = meshStatusColor
                        )
                    }
                }
            }
        }

        // Profile Button
        Surface(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            shadowElevation = 2.dp,
            onClick = onProfileClick
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.PersonOutline,
                    contentDescription = stringResource(R.string.home_profile_action),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Modern "Nearby Mesh Friends" horizontal stories/presence bar.
 */
@Composable
private fun NearbyMeshStoriesBar(
    nodes: List<NodeItemData>,
    directNodeNames: List<String> = emptyList(),
    isNodeActive: Boolean,
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
                            text = if (isNodeActive) "Discovering nearby phones..." else "Mesh is offline",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isNodeActive) "Bluetooth mesh scanning is running in the background." else "Tap to check mesh permissions & status.",
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
@Composable
private fun CommunityIncidentsBanner(
    activeIncidentCount: Int,
    criticalIncidentCount: Int,
    onIncidentsClick: () -> Unit
) {
    val hasActive = activeIncidentCount > 0
    val isCritical = criticalIncidentCount > 0

    val bannerBg = if (hasActive) {
        ResQTheme.colors.sosContainer.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val bannerBorderColor = if (hasActive) {
        if (isCritical) ResQTheme.colors.sos else ResQTheme.colors.sos.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    }
    val iconBg = if (hasActive) {
        ResQTheme.colors.sos
    } else {
        ModernMint.copy(alpha = 0.12f)
    }
    val iconTint = if (hasActive) {
        Color.White
    } else {
        ModernMint
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onIncidentsClick),
        shape = RoundedCornerShape(18.dp),
        color = bannerBg,
        border = BorderStroke(
            width = if (isCritical) 1.5.dp else 1.dp,
            color = bannerBorderColor
        ),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = iconBg,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (hasActive) Icons.Outlined.WarningAmber else Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "EMERGENCY INCIDENTS",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (hasActive) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ResQTheme.colors.sos
                            ) {
                                Text(
                                    text = if (isCritical) "URGENT" else "$activeIncidentCount ACTIVE",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Black),
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (hasActive) "$activeIncidentCount emergency request${if (activeIncidentCount > 1) "s" else ""} nearby. Tap to triage." else "Community Mutual Aid • All clear in your area.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Modern Mesh Topology Summary Card ("Your network") with expandable 2D radar.
 */
@Composable
private fun ModernMeshTopologyCard(
    summary: HomeNetworkSummary,
    radarState: RadarUiState,
    myDeviceName: String,
    directNodeNames: List<String>,
    onNetworkClick: () -> Unit,
    onPeerClick: (String) -> Unit
) {
    val hasDirect = summary.directPeers >= 1
    val hubColor = if (hasDirect) ModernMint else MaterialTheme.colorScheme.primary
    val hubBg = if (hasDirect) ModernMint.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNetworkClick),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = hubBg,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Hub,
                                contentDescription = null,
                                tint = hubColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.home_network_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = summary.label(),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Inspect Map",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Quick Metrics Row (Direct, Relayed, Scanned)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NetworkMetricChip(
                    modifier = Modifier.weight(1f),
                    label = "Direct",
                    value = "${summary.directPeers}",
                    color = ModernMint
                )
                NetworkMetricChip(
                    modifier = Modifier.weight(1f),
                    label = "Relayed",
                    value = "${summary.relayedPeers}",
                    color = ModernSky
                )
                NetworkMetricChip(
                    modifier = Modifier.weight(1f),
                    label = "Scanned",
                    value = "${summary.nearbyPeers}",
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 2D Node Radar visualizer always shown
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onNetworkClick),
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                NetworkGraphVisualizer(
                    nodes = classifyRadarNodes(radarState),
                    myDeviceName = myDeviceName,
                    showEmptyScanPrompt = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
            }
        }
    }
}

@Composable
private fun NetworkMetricChip(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    color: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Modern satellite GPS chip.
 */
@Composable
private fun ModernGpsStatusCard(locationStatus: LocationStatus) {
    val (statusColor, titleText, descText) = when (locationStatus) {
        LocationStatus.ACQUIRING -> Triple(ResQTheme.colors.warning, "Acquiring GPS location...", "Looking for satellite signal for emergency SOS.")
        LocationStatus.READY -> Triple(ModernMint, "Location found", "A last known location is available. SOS will request an update.")
        LocationStatus.ERROR_DENIED -> Triple(MaterialTheme.colorScheme.error, "Location Permission Needed", "Enable location permissions so rescuers can find you.")
        LocationStatus.ERROR_DISABLED -> Triple(ResQTheme.colors.warning, "Phone GPS is Disabled", "Turn on GPS in device settings for SOS positioning.")
        LocationStatus.IDLE -> Triple(MaterialTheme.colorScheme.onSurfaceVariant, "Location Idle", "Position will be acquired when sending SOS.")
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (locationStatus == LocationStatus.ACQUIRING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = statusColor
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.NearMe,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = descText,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Clean, reassuring safety guidance footer.
 */
@Composable
private fun ModernSafetyFooter() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.Medium, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Shield,
                contentDescription = null,
                tint = ModernMint,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = stringResource(R.string.mission_beacon_ready),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.mission_sos_note),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

data class HomeNetworkSummary(
    val directPeers: Int,
    val relayedPeers: Int,
    val nearbyPeers: Int,
    val checkingPeers: Int
) {
    @StringRes
    fun labelResource(): Int = when {
        directPeers > 0 -> R.string.home_network_direct
        relayedPeers > 0 -> R.string.home_network_relayed
        nearbyPeers > 0 -> R.string.home_network_nearby
        checkingPeers > 0 -> R.string.home_network_checking
        else -> R.string.home_network_empty
    }

    @Composable
    fun label(): String = stringResource(labelResource(), primaryCount())

    private fun primaryCount(): Int = when {
        directPeers > 0 -> directPeers
        relayedPeers > 0 -> relayedPeers
        nearbyPeers > 0 -> nearbyPeers
        else -> checkingPeers
    }
}

internal fun homeNetworkSummary(state: RadarUiState): HomeNetworkSummary {
    fun isActiveDirect(device: ConnectedDevice): Boolean =
        device.isPayloadReady && device.isPeerResponsive &&
            !device.isProvisional && !NodeIdentity.isPlaceholder(device.name)

    fun isChecking(device: ConnectedDevice): Boolean =
        device.isPayloadReady && !device.isPeerResponsive &&
            !device.isProvisional && !NodeIdentity.isPlaceholder(device.name)

    val directNames = state.connectedDevices.filter(::isActiveDirect).map { it.name }
    val checkingNames = state.connectedDevices.filter(::isChecking).map { it.name }
    val relayedNames = state.knownNodes
        .filter { !it.isDirect && !NodeIdentity.isPlaceholder(it.name) }
        .map { it.name }

    val nearby = state.scannedDevices.count { scanned ->
        !NodeIdentity.isPlaceholder(scanned.name) &&
            directNames.none { NodeIdentity.matches(it, scanned.name) } &&
            checkingNames.none { NodeIdentity.matches(it, scanned.name) } &&
            relayedNames.none { NodeIdentity.matches(it, scanned.name) }
    }

    return HomeNetworkSummary(
        directPeers = directNames.distinctBy(NodeIdentity::key).size,
        relayedPeers = relayedNames.distinctBy(NodeIdentity::key).size,
        nearbyPeers = nearby,
        checkingPeers = checkingNames.distinctBy(NodeIdentity::key).size
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenPreview() {
    TestResQMeshTheme {
        HomeScreenContent(
            isNodeActive = true,
            summary = HomeNetworkSummary(directPeers = 2, relayedPeers = 1, nearbyPeers = 3, checkingPeers = 0),
            radarState = RadarUiState(),
            myDeviceName = "Alex Rivera [TEAM]#4021",
            directNodeNames = listOf("Chloe Davis", "Ben Carter"),
            onMessagesClick = {},
            onNetworkClick = {},
            onProfileClick = {},
            onVoiceClick = {}
        )
    }
}
