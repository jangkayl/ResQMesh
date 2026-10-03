package com.example.testresqmesh.feature.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.feature.comms.model.ConversationPreview
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.example.testresqmesh.core.location.LocationStatus
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import com.example.testresqmesh.core.ui.model.NodeItemData
import com.example.testresqmesh.core.ui.model.NodeKind
import com.example.testresqmesh.core.ui.peers.classifyRadarNodes
import com.example.testresqmesh.feature.radar.viewmodel.RadarViewModel
import com.example.testresqmesh.feature.setup.viewmodel.SetupViewModel
import com.example.testresqmesh.core.ui.model.RadarUiState
import com.example.testresqmesh.feature.home.model.HomeNetworkSummary
import com.example.testresqmesh.feature.home.model.homeNetworkSummary
import com.example.testresqmesh.feature.home.ui.components.CommunityIncidentsBanner
import com.example.testresqmesh.feature.home.ui.components.ModernGpsStatusCard
import com.example.testresqmesh.feature.home.ui.components.ModernMeshTopologyCard
import com.example.testresqmesh.feature.home.ui.components.ModernSafetyFooter
import com.example.testresqmesh.feature.home.ui.components.ModernUserHeader
import com.example.testresqmesh.feature.home.ui.components.NearbyMeshStoriesBar

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
        connectionStatus = connectionState.connectionStatus,
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
    nodes: List<NodeItemData> = emptyList(),
    connectionStatus: String = if (isNodeActive) "Searching for nearby devices" else "Mesh is offline"
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.Large)
            .padding(top = Spacing.Large, bottom = Spacing.Large),
        verticalArrangement = Arrangement.spacedBy(Spacing.Large)
    ) {
        // 1. Modern Social Header (Avatar, Greeting, Live Mesh Presence Pill)
        ModernUserHeader(
            myDeviceName = myDeviceName,
            isNodeActive = isNodeActive,
            summary = summary,
            connectionStatus = connectionStatus,
            onProfileClick = onProfileClick
        )

        // 2. "Nearby Mesh Friends" Stories/Presence Bar
        NearbyMeshStoriesBar(
            nodes = if (nodes.isNotEmpty()) nodes else classifyRadarNodes(radarState),
            directNodeNames = directNodeNames,
            isNodeActive = isNodeActive,
            connectionStatus = connectionStatus,
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
