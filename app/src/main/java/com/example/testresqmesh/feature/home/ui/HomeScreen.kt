package com.example.testresqmesh.feature.home.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.R
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
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    setupViewModel: SetupViewModel,
    radarViewModel: RadarViewModel,
    onMessagesClick: () -> Unit,
    onNetworkClick: () -> Unit,
    onProfileClick: () -> Unit,
    onVoiceClick: (() -> Unit)? = null
) {
    val connectionState by setupViewModel.uiState.collectAsState()
    val radarState by radarViewModel.uiState.collectAsState()
    val summary = remember(radarState) { homeNetworkSummary(radarState) }
    val context = LocalContext.current
    val myDeviceName = remember(context) {
        val prefs = context.getSharedPreferences("resqmesh_prefs", android.content.Context.MODE_PRIVATE)
        val customName = prefs.getString("custom_name", android.os.Build.MODEL) ?: android.os.Build.MODEL
        val tag = prefs.getString("node_tag", "NODE") ?: "NODE"
        val nodeId = prefs.getString("node_id", "") ?: ""
        if (nodeId.isEmpty()) "$customName [$tag]" else "$customName [$tag]#$nodeId"
    }
    val nodes = remember(radarState) { classifyRadarNodes(radarState) }
    val directNodeNames = remember(nodes) { nodes.filter { it.kind == NodeKind.DIRECT }.map { it.name } }

    HomeScreenContent(
        isNodeActive = connectionState.isOnline,
        summary = summary,
        radarState = radarState,
        myDeviceName = myDeviceName,
        directNodeNames = directNodeNames,
        onMessagesClick = onMessagesClick,
        onNetworkClick = onNetworkClick,
        onProfileClick = onProfileClick,
        onVoiceClick = onVoiceClick
    )
}

@Composable
fun HomeScreenContent(
    isNodeActive: Boolean,
    summary: HomeNetworkSummary,
    radarState: RadarUiState,
    myDeviceName: String,
    directNodeNames: List<String>,
    onMessagesClick: () -> Unit,
    onNetworkClick: () -> Unit,
    onProfileClick: () -> Unit,
    onVoiceClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val readinessTitle = stringResource(
        if (isNodeActive) R.string.home_ready_title else R.string.home_setup_title
    )
    val readinessDescription = stringResource(
        if (isNodeActive) R.string.home_ready_description else R.string.home_setup_description
    )

    var tickerIndex by remember { mutableStateOf(0) }
    val tickerMessages = if (isNodeActive) {
        listOf("SYSTEM NOMINAL", "ENCRYPTION ACTIVE", "MESH SECURE", "RADIO: 2.4GHz BLE")
    } else {
        listOf("SCANNING FOR PEERS...", "CHECKING RADIO...", "WAITING FOR SIGNAL")
    }

    LaunchedEffect(isNodeActive) {
        while (true) {
            delay(2600)
            tickerIndex = (tickerIndex + 1) % tickerMessages.size
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.Large)
            .padding(top = Spacing.Large, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.Large)
    ) {
        // 1. Tactical Call-Sign Header
        TacticalHeader(
            myDeviceName = myDeviceName,
            isOnline = isNodeActive,
            onProfileClick = onProfileClick
        )

        // 2. High-Tech Telemetry Ticker Strip
        TacticalTelemetryTicker(
            isOnline = isNodeActive,
            tickerText = tickerMessages[tickerIndex]
        )

        // 3. Central Mission Readiness Command Panel
        TacticalMissionSignalPanel(
            isNodeActive = isNodeActive,
            summary = summary,
            title = readinessTitle,
            description = readinessDescription
        )

        // 4. Quick Tactical Operations Grid (Side-by-side action cards)
        TacticalOperationsGrid(
            onMessagesClick = onMessagesClick,
            onVoiceClick = { onVoiceClick?.invoke() ?: onMessagesClick() }
        )

        // 5. Live Interactive Tactical Mesh Topology Visualizer
        TacticalTopologyPanel(
            summary = summary,
            radarState = radarState,
            myDeviceName = myDeviceName,
            directNodeNames = directNodeNames,
            onNetworkClick = onNetworkClick
        )

        // 6. Tactical Safety Beacon Status Footer
        TacticalSafetyBeaconFooter()
    }
}

@Composable
private fun TacticalHeader(
    myDeviceName: String,
    isOnline: Boolean,
    onProfileClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isOnline) ResQTheme.colors.success.copy(alpha = 0.15f) else ResQTheme.colors.warning.copy(alpha = 0.15f),
                    border = BorderStroke(
                        1.dp,
                        if (isOnline) ResQTheme.colors.success.copy(alpha = 0.5f) else ResQTheme.colors.warning.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isOnline) ResQTheme.colors.success else ResQTheme.colors.warning)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isOnline) "MESH ACTIVE" else "STANDBY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                letterSpacing = 1.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            fontWeight = FontWeight.Bold,
                            color = if (isOnline) ResQTheme.colors.success else ResQTheme.colors.warning
                        )
                    }
                }
                Text(
                    text = "RESQMESH OPS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        letterSpacing = 1.2.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = myDeviceName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Profile Avatar with tactical ring
        Surface(
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                2.dp,
                if (isOnline) ResQTheme.colors.success.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            ),
            shadowElevation = 6.dp,
            onClick = onProfileClick
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.PersonOutline,
                    contentDescription = stringResource(R.string.home_profile_action),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun TacticalTelemetryTicker(
    isOnline: Boolean,
    tickerText: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = Spacing.Medium, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "tickerPulse"
            )

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        (if (isOnline) ResQTheme.colors.success else ResQTheme.colors.warning)
                            .copy(alpha = pulseAlpha)
                    )
            )
            Spacer(Modifier.width(8.dp))
            AnimatedContent(
                targetState = tickerText,
                transitionSpec = { fadeIn().togetherWith(fadeOut()) },
                modifier = Modifier.weight(1f),
                label = "telemetryTicker"
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.8.sp
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Security badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "AES-GCM",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun TacticalMissionSignalPanel(
    isNodeActive: Boolean,
    summary: HomeNetworkSummary,
    title: String,
    description: String,
) {
    val containerColor = if (isNodeActive) MaterialTheme.colorScheme.primaryContainer else ResQTheme.colors.warningContainer
    val contentColor = if (isNodeActive) MaterialTheme.colorScheme.onPrimaryContainer else ResQTheme.colors.onWarningContainer
    val dotColor = if (isNodeActive) ResQTheme.colors.success else ResQTheme.colors.warning

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.15f)),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                            .semantics { contentDescription = title }
                    )
                    Spacer(Modifier.width(Spacing.Small))
                    Text(
                        text = stringResource(if (isNodeActive) R.string.mission_online else R.string.mission_check),
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                        fontWeight = FontWeight.Black
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = contentColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = if (summary.directPeers > 0) "OPTIMAL LINK" else if (isNodeActive) "STANDBY" else "OFFLINE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        color = contentColor
                    )
                }
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.85f)
                )
            }

            // 3 Tactical Metric Pods
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TacticalMetricPod(
                    label = stringResource(R.string.mission_metric_direct),
                    value = summary.directPeers,
                    color = ResQTheme.colors.success,
                    contentColor = contentColor
                )
                TacticalMetricPod(
                    label = stringResource(R.string.mission_metric_relay),
                    value = summary.relayedPeers,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = contentColor
                )
                TacticalMetricPod(
                    label = stringResource(R.string.mission_metric_nearby),
                    value = summary.nearbyPeers,
                    color = ResQTheme.colors.warning,
                    contentColor = contentColor
                )
            }
        }
    }
}

@Composable
private fun TacticalMetricPod(
    label: String,
    value: Int,
    color: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = contentColor.copy(alpha = 0.08f),
        modifier = Modifier.width(96.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(46.dp)
            ) {
                CircularProgressIndicator(
                    progress = { (value.toFloat() / 10f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(),
                    color = color,
                    trackColor = color.copy(alpha = 0.2f),
                    strokeWidth = 4.5.dp,
                )
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = contentColor
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp
                ),
                fontWeight = FontWeight.Bold,
                color = contentColor.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun TacticalOperationsGrid(
    onMessagesClick: () -> Unit,
    onVoiceClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        // Card 1: Encrypted Messages
        ResQGlassSurface(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onMessagesClick),
            shape = RoundedCornerShape(18.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.Medium),
            shadowElevation = 6.dp
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HomeCardIcon(
                        icon = Icons.Outlined.ChatBubbleOutline,
                        color = MaterialTheme.colorScheme.primaryContainer,
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
                    text = stringResource(R.string.mission_message_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.mission_message_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Card 2: Tactical Voice Comms (PTT)
        ResQGlassSurface(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onVoiceClick),
            shape = RoundedCornerShape(18.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.Medium),
            shadowElevation = 6.dp
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HomeCardIcon(
                        icon = Icons.Outlined.GraphicEq,
                        color = Color(0xFF0D9488).copy(alpha = 0.2f),
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TacticalTopologyPanel(
    summary: HomeNetworkSummary,
    radarState: RadarUiState,
    myDeviceName: String,
    directNodeNames: List<String>,
    onNetworkClick: () -> Unit
) {
    val hasDirectConnection = summary.directPeers >= 1

    ResQGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onNetworkClick),
        shape = RoundedCornerShape(22.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.Medium),
        shadowElevation = 8.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            // Header Row
            Row(verticalAlignment = Alignment.CenterVertically) {
                HomeCardIcon(
                    icon = Icons.Outlined.Hub,
                    color = if (hasDirectConnection) ResQTheme.colors.success else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (hasDirectConnection) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    isGlowing = hasDirectConnection
                )
                Spacer(Modifier.width(Spacing.Medium))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.mission_topology_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = summary.label() + " · Tap to inspect",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Live Tactical Mesh Topology Visualizer Viewport
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                NetworkGraphVisualizer(
                    topology = radarState.topology,
                    myDeviceName = myDeviceName,
                    connectedNodes = directNodeNames,
                    showEmptyScanPrompt = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                )
            }

            // Active Connected Peer Chips Strip
            if (directNodeNames.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                            shape = RoundedCornerShape(8.dp),
                            color = ResQTheme.colors.success.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, ResQTheme.colors.success.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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

@Composable
private fun TacticalSafetyBeaconFooter() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
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
            .size(44.dp)
            .then(
                if (isGlowing) Modifier.shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(14.dp),
                    ambientColor = color.copy(alpha = 0.5f),
                    spotColor = color.copy(alpha = 0.6f)
                ) else Modifier
            ),
        shape = RoundedCornerShape(14.dp),
        color = color,
        border = if (isGlowing) BorderStroke(1.5.dp, Color.White.copy(alpha = 0.4f)) else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
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
