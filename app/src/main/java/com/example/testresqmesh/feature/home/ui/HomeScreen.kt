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
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.ChatBubbleOutline
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import com.example.testresqmesh.feature.radar.ui.NetworkGraphVisualizer
import com.example.testresqmesh.feature.radar.ui.NodeKind
import com.example.testresqmesh.feature.radar.ui.classifyRadarNodes
import com.example.testresqmesh.feature.radar.viewmodel.RadarViewModel
import com.example.testresqmesh.feature.setup.viewmodel.SetupViewModel
import com.example.testresqmesh.ui.state.RadarUiState

@Composable
fun HomeScreen(
    setupViewModel: SetupViewModel,
    radarViewModel: RadarViewModel,
    locationStatus: LocationStatus = LocationStatus.READY,
    onMessagesClick: () -> Unit,
    onNetworkClick: () -> Unit,
    onPeerClick: (String) -> Unit,
    onProfileClick: () -> Unit,
    onVoiceClick: (() -> Unit)? = null,
    onIncidentsClick: (() -> Unit)? = null
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
        myDeviceName = myDeviceName,
        directNodeNames = directNodeNames,
        locationStatus = locationStatus,
        onMessagesClick = onMessagesClick,
        onNetworkClick = onNetworkClick,
        onPeerClick = onPeerClick,
        onProfileClick = onProfileClick,
        onVoiceClick = onVoiceClick,
        onIncidentsClick = onIncidentsClick
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
    modifier: Modifier = Modifier
) {
    val readinessTitle = stringResource(
        if (isNodeActive) R.string.home_ready_title else R.string.home_setup_title
    )
    val readinessDescription = stringResource(
        if (isNodeActive) R.string.home_ready_description else R.string.home_setup_description
    )

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.Large)
            .padding(top = Spacing.Large, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        // 1. Tactical Callsign Header with Live RF Status Beacon
        TacticalIdentityHeader(
            myDeviceName = myDeviceName,
            isNodeActive = isNodeActive,
            summary = summary,
            onProfileClick = onProfileClick
        )

        // 2. Mission Command & Location Telemetry Panel
        MissionCommandTelemetryCard(
            isNodeActive = isNodeActive,
            title = readinessTitle,
            description = readinessDescription,
            locationStatus = locationStatus
        )

        // 3. Tactical Mesh Density Pods (Direct, Relay, Nearby)
        TacticalMeshMetricsRow(
            summary = summary,
            onNetworkClick = onNetworkClick
        )

        // 4. Live Tactical Mesh Radar & Linked Peers Viewport
        LiveMeshRadarCard(
            summary = summary,
            radarState = radarState,
            myDeviceName = myDeviceName,
            directNodeNames = directNodeNames,
            onNetworkClick = onNetworkClick,
            onPeerClick = onPeerClick
        )

        // 5. Operations Launchpad (Messages, Walkie-Talkie & Incidents)
        TacticalOperationsLaunchpad(
            onMessagesClick = onMessagesClick,
            onVoiceClick = onVoiceClick,
            onIncidentsClick = onIncidentsClick
        )

        // 6. Tactical Safety Beacon Status Footer
        TacticalSafetyBeaconFooter()
    }
}

/**
 * Header displaying the node callsign, deterministic ID pill, live radio beacon indicator,
 * and quick access to profile settings.
 */
@Composable
private fun TacticalIdentityHeader(
    myDeviceName: String,
    isNodeActive: Boolean,
    summary: HomeNetworkSummary,
    onProfileClick: () -> Unit
) {
    val displayName = NodeIdentity.displayNameOf(myDeviceName)
    val nodeId = remember(myDeviceName) {
        if (myDeviceName.contains('#')) "#" + myDeviceName.substringAfter('#') else ""
    }
    val (meshStatus, meshStatusColor) = when {
        !isNodeActive -> "MESH OFFLINE" to ResQTheme.colors.warning
        summary.directPeers > 0 -> "DIRECT LINK READY" to ResQTheme.colors.success
        summary.relayedPeers > 0 -> "PEER REACHABLE" to ResQTheme.colors.success
        summary.checkingPeers > 0 -> "CHECKING CONNECTION" to ResQTheme.colors.warning
        else -> "SEARCHING FOR PEERS" to ResQTheme.colors.warning
    }

    val infiniteTransition = rememberInfiniteTransition(label = "RfBeaconPulse")
    val beaconAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beaconAlpha"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = Spacing.Medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Live RF Status Beacon Dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .graphicsLayer { alpha = if (isNodeActive) beaconAlpha else 0.4f }
                        .clip(CircleShape)
                        .background(meshStatusColor)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = meshStatus,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    ),
                    fontWeight = FontWeight.Bold,
                    color = meshStatusColor
                )
                if (nodeId.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = nodeId,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            Text(
                text = displayName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Profile / Civilian Identity Button
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
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
 * Unified Mission Command & Telemetry Card combining network readiness state
 * and satellite GPS positioning into a cohesive operational banner.
 */
@Composable
private fun MissionCommandTelemetryCard(
    isNodeActive: Boolean,
    title: String,
    description: String,
    locationStatus: LocationStatus
) {
    val statusColor = if (isNodeActive) ResQTheme.colors.success else ResQTheme.colors.warning
    val statusBg = if (isNodeActive) ResQTheme.colors.success.copy(alpha = 0.12f) else ResQTheme.colors.warning.copy(alpha = 0.12f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
        ) {
            // Readiness Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusBg,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isNodeActive) Icons.Outlined.WifiTethering else Icons.Outlined.CellTower,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(Modifier.width(Spacing.Medium))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Integrated GPS Telemetry Pill
            TacticalGpsTelemetryPill(locationStatus = locationStatus)
        }
    }
}

/**
 * Compact, high-visibility satellite GPS telemetry chip embedded inside the command card.
 */
@Composable
private fun TacticalGpsTelemetryPill(
    locationStatus: LocationStatus
) {
    val infiniteTransition = rememberInfiniteTransition(label = "GpsPillPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val (statusColor, titleText, descText) = when (locationStatus) {
        LocationStatus.ACQUIRING -> Triple(
            ResQTheme.colors.warning,
            "Finding your location",
            "Your phone is looking for a location to use with SOS."
        )
        LocationStatus.READY -> Triple(
            ResQTheme.colors.success,
            "Location found",
            "A last known location is available. SOS will request an update."
        )
        LocationStatus.ERROR_DENIED -> Triple(
            MaterialTheme.colorScheme.error,
            "Location permission needed",
            "Allow location access to include your position with SOS."
        )
        LocationStatus.ERROR_DISABLED -> Triple(
            ResQTheme.colors.warning,
            "Phone location is off",
            "Turn on Location in your phone settings for SOS."
        )
        LocationStatus.IDLE -> Triple(
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Location not checked yet",
            "Your phone will check for a location when ready."
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Medium, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                if (locationStatus == LocationStatus.ACQUIRING) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer { alpha = pulseAlpha },
                        strokeWidth = 2.dp,
                        color = statusColor
                    )
                } else if (locationStatus == LocationStatus.READY) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.NearMe,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

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
 * Clean, tactile metric pods for mesh density (Direct 🟢, Relay 🔵, Nearby 🟡).
 */
@Composable
private fun TacticalMeshMetricsRow(
    summary: HomeNetworkSummary,
    onNetworkClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
    ) {
        TacticalMetricTile(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.mission_metric_direct),
            value = summary.directPeers,
            accentColor = ResQTheme.colors.success,
            subtitle = "Direct peers",
            onClick = onNetworkClick
        )
        TacticalMetricTile(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.mission_metric_relay),
            value = summary.relayedPeers,
            accentColor = MaterialTheme.colorScheme.primary,
            subtitle = "Multi-hop",
            onClick = onNetworkClick
        )
        TacticalMetricTile(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.mission_metric_nearby),
            value = summary.nearbyPeers,
            accentColor = ResQTheme.colors.warning,
            subtitle = "Unconnected",
            onClick = onNetworkClick
        )
    }
}

@Composable
private fun TacticalMetricTile(
    modifier: Modifier = Modifier,
    label: String,
    value: Int,
    accentColor: Color,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Monospace
                ),
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Dedicated Live Mesh Radar Viewport & Linked Peers Shelf.
 */
@Composable
private fun LiveMeshRadarCard(
    summary: HomeNetworkSummary,
    radarState: RadarUiState,
    myDeviceName: String,
    directNodeNames: List<String>,
    onNetworkClick: () -> Unit,
    onPeerClick: (String) -> Unit
) {
    val hasDirectConnection = summary.directPeers >= 1

    ResQGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(Spacing.Medium),
        shadowElevation = 6.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            // Header Row (Clickable for Network details)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNetworkClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Hub,
                    contentDescription = null,
                    tint = if (hasDirectConnection) ResQTheme.colors.success else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(Modifier.width(Spacing.Small))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_network_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = summary.label() + " · Tap to inspect",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Live Tactical Mesh Topology Visualizer Viewport (Clickable for People & Paths)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onNetworkClick),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            ) {
                NetworkGraphVisualizer(
                    nodes = classifyRadarNodes(radarState),
                    myDeviceName = myDeviceName,
                    showEmptyScanPrompt = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                )
            }

            // Active Linked Peers Shelf
            if (directNodeNames.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.mission_active_nodes) + ":",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    directNodeNames.forEach { peerName ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ResQTheme.colors.success.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, ResQTheme.colors.success.copy(alpha = 0.35f)),
                            modifier = Modifier.clickable { onPeerClick(peerName) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(ResQTheme.colors.success)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = peerName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WifiTethering,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.mission_no_nodes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/**
 * Tactical Operations Launchpad providing rapid shortcuts to local messages,
 * walkie-talkie voice comms, and active emergency incident tracking.
 */
@Composable
private fun TacticalOperationsLaunchpad(
    onMessagesClick: () -> Unit,
    onVoiceClick: (() -> Unit)?,
    onIncidentsClick: (() -> Unit)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
        ) {
            // Card 1: Local Messages
            ResQGlassSurface(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onMessagesClick),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(Spacing.Medium),
                shadowElevation = 4.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HomeCardIcon(
                            icon = Icons.Outlined.ChatBubbleOutline,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.height(Spacing.Medium))
                    Text(
                        text = stringResource(R.string.messages_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Public and private BLE messages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Card 2: Tactical Voice Comms (PTT)
            if (onVoiceClick != null) {
                ResQGlassSurface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onVoiceClick),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(Spacing.Medium),
                    shadowElevation = 4.dp
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HomeCardIcon(
                                icon = Icons.Outlined.GraphicEq,
                                color = Color(0xFF0D9488).copy(alpha = 0.15f),
                                contentColor = Color(0xFF0D9488)
                            )
                            Icon(
                                imageVector = Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = Color(0xFF0D9488),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.height(Spacing.Medium))
                        Text(
                            text = stringResource(R.string.mission_voice_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.mission_voice_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Emergency Incidents Banner
        if (onIncidentsClick != null) {
            TacticalIncidentsBanner(onIncidentsClick = onIncidentsClick)
        }
    }
}

@Composable
private fun TacticalIncidentsBanner(
    onIncidentsClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onIncidentsClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "EMERGENCY INCIDENTS",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Track, coordinate, & resolve SOS lifecycle",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                    )
                }
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

/** Explains where to find the SOS action without implying delivery readiness. */
@Composable
private fun TacticalSafetyBeaconFooter() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Shield,
                contentDescription = null,
                tint = ResQTheme.colors.success,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = stringResource(R.string.mission_beacon_ready),
                    style = MaterialTheme.typography.labelSmall,
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

@Composable
private fun HomeCardIcon(
    icon: ImageVector,
    color: Color,
    contentColor: Color,
    isGlowing: Boolean = false
) {
    Surface(
        modifier = Modifier
            .size(42.dp)
            .then(
                if (isGlowing) Modifier.shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(12.dp),
                    ambientColor = color.copy(alpha = 0.5f),
                    spotColor = color.copy(alpha = 0.6f)
                ) else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        color = color,
        border = BorderStroke(1.dp, if (isGlowing) Color.White.copy(alpha = 0.4f) else contentColor.copy(alpha = 0.25f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
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
            myDeviceName = "ALPHA-NODE [TEAM1]#4021",
            directNodeNames = listOf("Bravo-2", "Charlie-HQ"),
            onMessagesClick = {},
            onNetworkClick = {},
            onProfileClick = {},
            onVoiceClick = {}
        )
    }
}
