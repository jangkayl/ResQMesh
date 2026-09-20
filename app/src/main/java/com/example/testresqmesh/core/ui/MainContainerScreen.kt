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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.core.ui.theme.AppAppearance
import com.example.testresqmesh.core.ui.theme.ResQMotion
import com.example.testresqmesh.core.ui.components.debug.DebugTerminal
import com.example.testresqmesh.core.utils.AppLogger
import androidx.compose.ui.platform.LocalContext
import com.example.testresqmesh.core.utils.NotificationHelper

@Composable
fun MainContainerScreen(
    setupViewModel: SetupViewModel,
    radarViewModel: RadarViewModel,
    commsViewModel: CommunicationViewModel,
    walkieTalkieViewModel: com.example.testresqmesh.feature.comms.viewmodel.WalkieTalkieViewModel,
    mediaHelper: MediaHelper,
    appearance: AppAppearance,
    onAppearanceSelected: (AppAppearance) -> Unit,
    initialChatNode: String? = null,
    onClearInitialChatNode: (() -> Unit)? = null,
    initialViewMap: Boolean = false,
    initialSosSender: String? = null,
    initialSosText: String? = null,
    onClearInitialViewMap: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val notificationHelper = remember(context) { NotificationHelper(context) }
    var currentDestination by remember { mutableStateOf(ResQDestination.Mission) }
    
    // Sub-navigation state for prototype
    var activeChatNode by remember { mutableStateOf<String?>(null) }
    var trackingNode by remember { mutableStateOf<String?>(null) }
    var isSOSActive by remember { mutableStateOf(false) }
    var mapSosAlert by remember { mutableStateOf<com.example.testresqmesh.core.model.ChatMessage?>(null) }
    var showProfile by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var showOfflineMaps by remember { mutableStateOf(false) }
    var showNetworkDetails by remember { mutableStateOf(false) }
    var isCommunityConversationOpen by remember { mutableStateOf(false) }

    // Legacy Radar screen remains in source intentionally. Offline maps are managed via Profile/Settings.
    
    val incomingSosAlert by commsViewModel.incomingSosAlert.collectAsState()
    val activeSosMessageId by commsViewModel.activeSosMessageId.collectAsState()
    val locationStatus by commsViewModel.locationStatus.collectAsState()

    // Deep link handling: direct navigation to active chat
    LaunchedEffect(initialChatNode) {
        if (!initialChatNode.isNullOrBlank()) {
            activeChatNode = initialChatNode
            currentDestination = ResQDestination.Messages
            notificationHelper.clearPrivateMessagesFor(initialChatNode)
            onClearInitialChatNode?.invoke()
        }
    }

    // Deep link handling: direct navigation to tactical SOS map
    LaunchedEffect(initialViewMap) {
        if (initialViewMap) {
            mapSosAlert = com.example.testresqmesh.core.model.ChatMessage(
                id = "deep_link_sos_${System.currentTimeMillis()}",
                senderName = initialSosSender ?: "EMERGENCY BEACON",
                text = initialSosText ?: "🚨 CRITICAL SOS ALERT",
                imageBase64 = null,
                audioBase64 = null,
                locationLat = null,
                locationLng = null,
                isMine = false,
                isPrivate = false
            )
            onClearInitialViewMap?.invoke()
        }
    }

    // Clear notifications when entering a private conversation
    LaunchedEffect(activeChatNode) {
        activeChatNode?.let { node ->
            notificationHelper.clearPrivateMessagesFor(node)
        }
    }

    DisposableEffect(Unit) {
        // Start passive location tracking when the node is active
        commsViewModel.startLocationTracking()
        onDispose {
            // Cleanup when the node is shut down or the app closes
            commsViewModel.stopLocationTracking()
        }
    }

    var showDeveloperRadar by remember { mutableStateOf(false) }
    val isDeveloperMode by setupViewModel.isDeveloperModeEnabled.collectAsState()

    LaunchedEffect(Unit) {
        setupViewModel.initDeveloperMode(context)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            incomingSosAlert != null -> {
                FullScreenSosAlarm(
                    alertMessage = incomingSosAlert!!,
                    onDismiss = { commsViewModel.clearSosAlert() },
                    onViewMap = { 
                        val alert = incomingSosAlert
                        commsViewModel.clearSosAlert()
                        mapSosAlert = alert
                    }
                )
                BackHandler { commsViewModel.clearSosAlert() }
            }
            mapSosAlert != null -> {
                SosMapScreen(
                    alertMessage = mapSosAlert!!,
                    onBack = { mapSosAlert = null }
                )
                BackHandler { mapSosAlert = null }
            }
            activeSosMessageId != null -> {
                ActiveSOSMonitoringScreen(
                    commsViewModel = commsViewModel,
                    onResolve = { commsViewModel.cancelEmergencySOS() }
                )
                BackHandler { }
            }
            isSOSActive -> {
                SOSBroadcastScreen(
                    onCancel = { isSOSActive = false },
                    onSosTriggered = { type ->
                        commsViewModel.sendEmergencySOS(type)
                        isSOSActive = false
                    }
                )
                BackHandler { isSOSActive = false }
            }
            showDeveloperRadar -> {
                ResQAuroraBackground(modifier = Modifier.fillMaxSize()) {
                    com.example.testresqmesh.feature.radar.ui.RadarScreen(viewModel = radarViewModel)
                }
                BackHandler { showDeveloperRadar = false }
            }
            activeChatNode != null -> {
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
            }
            trackingNode != null -> {
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
            }
            showAdvanced -> {
                ResQAuroraBackground(modifier = Modifier.fillMaxSize()) {
                    AdvancedScreen(walkieTalkieViewModel = walkieTalkieViewModel, onBack = { showAdvanced = false })
                }
                BackHandler { showAdvanced = false }
            }
            showOfflineMaps -> {
                ResQAuroraBackground(modifier = Modifier.fillMaxSize()) {
                    com.example.testresqmesh.feature.profile.ui.OfflineMapSettingsScreen(
                        onBack = { showOfflineMaps = false }
                    )
                }
                BackHandler { showOfflineMaps = false }
            }
            showProfile -> {
                ResQAuroraBackground(modifier = Modifier.fillMaxSize()) {
                    ProfileScreen(
                        viewModel = setupViewModel,
                        appearance = appearance,
                        onAppearanceSelected = onAppearanceSelected,
                        onAdvanced = { showAdvanced = true },
                        onOfflineMaps = { showOfflineMaps = true },
                        onBack = { showProfile = false }
                    )
                }
                BackHandler { showProfile = false }
            }
            showNetworkDetails -> {
                ResQAuroraBackground(modifier = Modifier.fillMaxSize()) {
                    NetworkScreen(
                        viewModel = radarViewModel,
                        onMessagePeer = { peer ->
                            showNetworkDetails = false
                            activeChatNode = peer
                        }
                    )
                }
                BackHandler { showNetworkDetails = false }
            }
            else -> {
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
                                locationStatus = locationStatus,
                                onMessagesClick = { currentDestination = ResQDestination.Messages },
                                onNetworkClick = { showNetworkDetails = true },
                                onProfileClick = { showProfile = true },
                                onVoiceClick = { currentDestination = ResQDestination.Voice }
                            )
                            ResQDestination.Messages -> ChatContainerScreen(
                                viewModel = commsViewModel, 
                                mediaHelper = mediaHelper, 
                                onChatSelected = { activeChatNode = it },
                                onCommunityConversationChanged = { isCommunityConversationOpen = it },
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
                                        isPrivate = false
                                    )
                                }
                            )
                            ResQDestination.Voice -> WalkieTalkieScreen(
                                commsViewModel = commsViewModel,
                                walkieTalkieViewModel = walkieTalkieViewModel,
                                mediaHelper = mediaHelper
                            )
                        }
                    }
                }
            }
        }

        if (isDeveloperMode && incomingSosAlert == null && mapSosAlert == null && activeSosMessageId == null && !isSOSActive) {
            FloatingDeveloperBadge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 8.dp, end = 12.dp),
                onClick = {
                    AppLogger.toggleTerminal()
                },
                onLongClick = {
                    showDeveloperRadar = !showDeveloperRadar
                }
            )
        }

        DebugTerminal(
            onOpenRadar = {
                showDeveloperRadar = true
                AppLogger.hideTerminal()
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FloatingDeveloperBadge(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .size(38.dp)
            .border(1.5.dp, Color(0xFF00FF00), CircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = CircleShape,
        color = Color(0xEE121814),
        shadowElevation = 8.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = "Developer Debug Mode",
                tint = Color(0xFF00FF00),
                modifier = Modifier.size(19.dp)
            )
        }
    }
}
