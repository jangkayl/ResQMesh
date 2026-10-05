package com.example.testresqmesh.app.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import com.example.testresqmesh.feature.comms.ui.ChatContainerScreen
import com.example.testresqmesh.feature.comms.ui.WalkieTalkieScreen
import com.example.testresqmesh.feature.home.ui.HomeScreen
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel
import com.example.testresqmesh.feature.radar.viewmodel.RadarViewModel
import com.example.testresqmesh.feature.setup.viewmodel.SetupViewModel
import com.example.testresqmesh.core.utils.MediaHelper
import com.example.testresqmesh.core.ui.components.layout.ResQDestination
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MainDestinationContent(
    targetScreen: ResQDestination,
    setupViewModel: SetupViewModel,
    radarViewModel: RadarViewModel,
    commsViewModel: CommunicationViewModel,
    walkieTalkieViewModel: com.example.testresqmesh.feature.comms.viewmodel.WalkieTalkieViewModel,
    mediaHelper: MediaHelper,
    locationStatus: com.example.testresqmesh.core.location.LocationStatus,
    activeIncidentCount: Int,
    criticalIncidentCount: Int,
    onMessages: () -> Unit,
    onNetwork: () -> Unit,
    onPeer: (String) -> Unit,
    onProfile: () -> Unit,
    onVoice: () -> Unit,
    onIncidents: () -> Unit,
    onChat: (String) -> Unit,
    onCommunity: () -> Unit,
    onMap: (Double, Double, String, String) -> Unit,
    onHistory: () -> Unit,
    onChat1: (String) -> Unit,
    onCommunity1: () -> Unit
) {
    when (targetScreen) {
        ResQDestination.Mission -> HomeScreen(
            setupViewModel = setupViewModel,
            radarViewModel = radarViewModel,
            commsViewModel = commsViewModel,
            locationStatus = locationStatus,
            onMessagesClick = onMessages,
            onNetworkClick = onNetwork,
            onPeerClick = onPeer,
            onProfileClick = onProfile,
            onVoiceClick = onVoice,
            onIncidentsClick = onIncidents,
            onChatSelected = onChat,
            onCommunityClick = onCommunity,
            activeIncidentCount = activeIncidentCount,
            criticalIncidentCount = criticalIncidentCount
        )
        ResQDestination.Messages -> ChatContainerScreen(
            viewModel = commsViewModel,
            mediaHelper = mediaHelper,
            onChatSelected = onChat1,
            onCommunityClick = onCommunity1,
            onViewMap = onMap
        )
        ResQDestination.Voice -> WalkieTalkieScreen(
            commsViewModel = commsViewModel,
            walkieTalkieViewModel = walkieTalkieViewModel,
            mediaHelper = mediaHelper,
            onHistory = onHistory
        )
    }
}
