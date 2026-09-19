package com.example.testresqmesh.feature.home.ui

import androidx.annotation.StringRes
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.R
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.ui.components.layout.ResQGlassSurface
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import androidx.compose.ui.platform.LocalContext
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
    onMessagesClick: () -> Unit,
    onNetworkClick: () -> Unit,
    onProfileClick: () -> Unit
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
        onProfileClick = onProfileClick
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
    modifier: Modifier = Modifier
) {
    val readinessTitle = stringResource(
        if (isNodeActive) R.string.home_ready_title else R.string.home_setup_title
    )
    val readinessDescription = stringResource(
        if (isNodeActive) R.string.home_ready_description else R.string.home_setup_description
    )

    var tickerIndex by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
    val tickerMessages = if (isNodeActive) listOf("SYSTEM NOMINAL", "ENCRYPTION ACTIVE", "MESH SECURE") else listOf("SCANNING FOR PEERS...", "CHECKING RADIO...", "WAITING FOR SIGNAL")
    
    androidx.compose.runtime.LaunchedEffect(isNodeActive) {
        while (true) {
            kotlinx.coroutines.delay(2500)
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.mission_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = stringResource(R.string.mission_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                onClick = onProfileClick
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.PersonOutline,
                        contentDescription = stringResource(R.string.home_profile_action),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(25.dp)
                    )
                }
            }
        }
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(modifier = Modifier.padding(horizontal = Spacing.Medium, vertical = Spacing.Small), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (isNodeActive) ResQTheme.colors.success else ResQTheme.colors.warning))
                Spacer(Modifier.width(Spacing.Small))
                androidx.compose.animation.AnimatedContent(
                    targetState = tickerMessages[tickerIndex],
                    transitionSpec = { androidx.compose.animation.fadeIn().togetherWith(androidx.compose.animation.fadeOut()) },
                    label = "ticker"
                ) { text ->
                    Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        MissionSignalPanel(isNodeActive = isNodeActive, summary = summary, title = readinessTitle, description = readinessDescription)

        ResQGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onMessagesClick),
            shape = RoundedCornerShape(28.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.Medium),
            shadowElevation = 10.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HomeCardIcon(Icons.Outlined.ChatBubbleOutline, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Spacing.Medium))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.mission_message_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.mission_message_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }

        ResQGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNetworkClick),
            shape = RoundedCornerShape(28.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.Medium),
            shadowElevation = 10.dp
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HomeCardIcon(
                        icon = Icons.Outlined.Hub,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.width(Spacing.Medium))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.home_network_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(Spacing.ExtraSmall))
                        Text(
                            text = summary.label() + " · Tap for network details",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Live Tactical Mesh Topology Display
                NetworkGraphVisualizer(
                    topology = radarState.topology,
                    myDeviceName = myDeviceName,
                    connectedNodes = directNodeNames,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                )
            }
        }

        Text(
            text = stringResource(R.string.mission_sos_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.Small)
        )
    }
}

@Composable
private fun MissionSignalPanel(
    isNodeActive: Boolean,
    summary: HomeNetworkSummary,
    title: String,
    description: String,
) {
    val container = if (isNodeActive) MaterialTheme.colorScheme.primaryContainer else ResQTheme.colors.warningContainer
    val content = if (isNodeActive) MaterialTheme.colorScheme.onPrimaryContainer else ResQTheme.colors.onWarningContainer
    val dot = if (isNodeActive) ResQTheme.colors.success else ResQTheme.colors.warning

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = container,
        contentColor = content
    ) {
        Column(modifier = Modifier.padding(Spacing.Large), verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(dot).semantics { contentDescription = title })
                Spacer(Modifier.width(Spacing.Small))
                Text(
                    stringResource(if (isNodeActive) R.string.mission_online else R.string.mission_check),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black
                )
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodyMedium)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MissionMetric(stringResource(R.string.mission_metric_direct), summary.directPeers, content)
                MissionMetric(stringResource(R.string.mission_metric_relay), summary.relayedPeers, content)
                MissionMetric(stringResource(R.string.mission_metric_nearby), summary.nearbyPeers, content)
            }
        }
    }
}

@Composable
private fun MissionMetric(label: String, value: Int, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(54.dp)) {
            androidx.compose.material3.CircularProgressIndicator(
                progress = { (value.toFloat() / 10f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxSize(),
                color = color,
                trackColor = color.copy(alpha = 0.2f),
                strokeWidth = 6.dp,
            )
            Text(value.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = color)
        }
        Spacer(Modifier.height(Spacing.Small))
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color.copy(alpha = 0.8f))
    }
}

@Composable
private fun HomeCardIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    contentColor: Color
) {
    Surface(
        modifier = Modifier.size(48.dp),
        shape = RoundedCornerShape(16.dp),
        color = color
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = contentColor)
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
            summary = HomeNetworkSummary(directPeers = 1, relayedPeers = 0, nearbyPeers = 2, checkingPeers = 0),
            radarState = RadarUiState(),
            myDeviceName = "My Phone",
            directNodeNames = listOf("Peer A"),
            onMessagesClick = {},
            onNetworkClick = {},
            onProfileClick = {}
        )
    }
}
