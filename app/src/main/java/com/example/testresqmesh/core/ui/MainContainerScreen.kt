package com.example.testresqmesh.core.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import com.example.testresqmesh.feature.comms.ui.ActiveChatScreen
import com.example.testresqmesh.feature.comms.ui.ChatContainerScreen
import com.example.testresqmesh.feature.comms.ui.WalkieTalkieScreen
import com.example.testresqmesh.feature.radar.ui.NetworkScreen
import com.example.testresqmesh.feature.radar.ui.ResponderTrackerScreen
import com.example.testresqmesh.feature.home.ui.HomeScreen
import com.example.testresqmesh.feature.sos.ui.SOSBroadcastScreen
import com.example.testresqmesh.feature.sos.ui.FullScreenSosAlarm
import com.example.testresqmesh.feature.sos.ui.ActiveSOSMonitoringScreen
import com.example.testresqmesh.feature.sos.ui.SosMapScreen
import com.example.testresqmesh.feature.profile.ui.ProfileScreen
import com.example.testresqmesh.feature.profile.ui.AdvancedScreen
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel
import com.example.testresqmesh.feature.radar.viewmodel.RadarViewModel
import com.example.testresqmesh.feature.setup.viewmodel.SetupViewModel
import com.example.testresqmesh.core.utils.MediaHelper
import com.example.testresqmesh.core.ui.components.layout.ResQAppShell
import com.example.testresqmesh.core.ui.components.layout.ResQAuroraBackground
import com.example.testresqmesh.core.ui.components.layout.ResQDestination
import com.example.testresqmesh.core.ui.theme.AppAppearance
import com.example.testresqmesh.core.ui.theme.ResQMotion

@Composable
fun MainContainerScreen(
    setupViewModel: SetupViewModel,
    radarViewModel: RadarViewModel,
    commsViewModel: CommunicationViewModel,
    walkieTalkieViewModel: com.example.testresqmesh.feature.comms.viewmodel.WalkieTalkieViewModel,
    mediaHelper: MediaHelper,
    appearance: AppAppearance,
    onAppearanceSelected: (AppAppearance) -> Unit
) {
    var currentDestination by remember { mutableStateOf(ResQDestination.Mission) }
    
    // Sub-navigation state for prototype
    var activeChatNode by remember { mutableStateOf<String?>(null) }
    var trackingNode by remember { mutableStateOf<String?>(null) }
    var isSOSActive by remember { mutableStateOf(false) }
    var mapSosAlert by remember { mutableStateOf<com.example.testresqmesh.core.model.ChatMessage?>(null) }
    var showProfile by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var isCommunityConversationOpen by remember { mutableStateOf(false) }

    // OfflineMapPromptModal and the legacy Radar screen remain in source intentionally. Their
    // entry points are hidden while the new shell is evaluated and can be restored later.
    
    val incomingSosAlert by commsViewModel.incomingSosAlert.collectAsState()
    val activeSosMessageId by commsViewModel.activeSosMessageId.collectAsState()

    DisposableEffect(Unit) {
        // Start passive location tracking when the node is active
        commsViewModel.startLocationTracking()
        onDispose {
            // Cleanup when the node is shut down or the app closes
            commsViewModel.stopLocationTracking()
        }
    }

    if (incomingSosAlert != null) {
        FullScreenSosAlarm(
            alertMessage = incomingSosAlert!!,
            onDismiss = { commsViewModel.clearSosAlert() },
            onViewMap = { 
                mapSosAlert = incomingSosAlert
            }
        )
        BackHandler { commsViewModel.clearSosAlert() }
        return
    }

    if (mapSosAlert != null) {
        SosMapScreen(
            alertMessage = mapSosAlert!!,
            onBack = { mapSosAlert = null }
        )
        BackHandler { mapSosAlert = null }
        return
    }

    if (activeSosMessageId != null) {
        ActiveSOSMonitoringScreen(
            commsViewModel = commsViewModel,
            onResolve = { commsViewModel.cancelEmergencySOS() }
        )
        BackHandler { }
        return
    }

    if (isSOSActive) {
        SOSBroadcastScreen(
            onCancel = { isSOSActive = false },
            onSosTriggered = { type ->
                commsViewModel.sendEmergencySOS(type)
                isSOSActive = false
            }
        )
        BackHandler { isSOSActive = false }
        return
    }

    if (activeChatNode != null) {
        ResQAuroraBackground(modifier = Modifier.fillMaxSize()) {
            ActiveChatScreen(
                name = activeChatNode!!,
                viewModel = commsViewModel,
                mediaHelper = mediaHelper,
                onBack = { activeChatNode = null },
                onViewMap = { lat, lng, sender, text ->
                    mapSosAlert = com.example.testresqmesh.core.model.ChatMessage(
                        id = "view_map_${System.currentTimeMillis()}",
                        senderName = sender,
                        text = text,
                        imageBase64 = null,
                        audioBase64 = null,
                        locationLat = lat,
                        locationLng = lng,
                        isMine = false,
                        isPrivate = true
                    )
                }
            )
        }
        BackHandler { activeChatNode = null }
        return
    }

    if (trackingNode != null) {
        ResQAuroraBackground(modifier = Modifier.fillMaxSize()) {
            ResponderTrackerScreen(
                nodeName = trackingNode!!,
                onBack = { trackingNode = null },
                onChat = {
                    activeChatNode = trackingNode
                    trackingNode = null
                }
            )
        }
        BackHandler { trackingNode = null }
        return
    }

    if (showAdvanced) {
        ResQAuroraBackground(modifier = Modifier.fillMaxSize()) {
            AdvancedScreen(walkieTalkieViewModel = walkieTalkieViewModel, onBack = { showAdvanced = false })
        }
        BackHandler { showAdvanced = false }
        return
    }

    if (showProfile) {
        ResQAuroraBackground(modifier = Modifier.fillMaxSize()) {
            ProfileScreen(
                viewModel = setupViewModel,
                appearance = appearance,
                onAppearanceSelected = onAppearanceSelected,
                onAdvanced = { showAdvanced = true },
                onBack = { showProfile = false }
            )
        }
        BackHandler { showProfile = false }
        return
    }

    ResQAppShell(
        selectedDestination = currentDestination,
        onDestinationSelected = { currentDestination = it },
        onSosActivated = { isSOSActive = true },
        showNavigation = !isCommunityConversationOpen
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentDestination,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
            transitionSpec = {
                fadeIn(animationSpec = tween(ResQMotion.ScreenMillis, delayMillis = 60)) togetherWith
                fadeOut(animationSpec = tween(ResQMotion.PressMillis))
            },
            label = "ScreenTransition"
        ) { targetScreen ->
            when (targetScreen) {
                ResQDestination.Mission -> HomeScreen(
                    setupViewModel = setupViewModel,
                    radarViewModel = radarViewModel,
                    onMessagesClick = { currentDestination = ResQDestination.Messages },
                    onNetworkClick = { currentDestination = ResQDestination.Mesh },
                    onProfileClick = { showProfile = true }
                )
                ResQDestination.Messages -> ChatContainerScreen(
                    viewModel = commsViewModel, 
                    mediaHelper = mediaHelper, 
                    onChatSelected = { activeChatNode = it },
                    onCommunityConversationChanged = { isCommunityConversationOpen = it }
                )
                ResQDestination.Voice -> WalkieTalkieScreen(
                    commsViewModel = commsViewModel,
                    walkieTalkieViewModel = walkieTalkieViewModel,
                    mediaHelper = mediaHelper
                )
                ResQDestination.Mesh -> NetworkScreen(
                    viewModel = radarViewModel,
                    onMessagePeer = { peer -> activeChatNode = peer }
                )
            }
        }
    }
}
