package edu.cit.resqmesh.app.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import edu.cit.resqmesh.feature.comms.ui.ChatContainerScreen
import edu.cit.resqmesh.feature.comms.ui.WalkieTalkieScreen
import edu.cit.resqmesh.feature.home.ui.HomeScreen
import edu.cit.resqmesh.feature.comms.viewmodel.CommunicationViewModel
import edu.cit.resqmesh.feature.radar.viewmodel.RadarViewModel
import edu.cit.resqmesh.feature.setup.viewmodel.SetupViewModel
import edu.cit.resqmesh.core.audio.MediaHelper
import edu.cit.resqmesh.core.ui.components.layout.ResQDestination
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MainDestinationContent(
    targetScreen: ResQDestination,
    setupViewModel: SetupViewModel,
    radarViewModel: RadarViewModel,
    commsViewModel: CommunicationViewModel,
    walkieTalkieViewModel: edu.cit.resqmesh.feature.comms.viewmodel.WalkieTalkieViewModel,
    mediaHelper: MediaHelper,
    locationStatus: edu.cit.resqmesh.core.location.LocationStatus,
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
