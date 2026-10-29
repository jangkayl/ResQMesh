package edu.cit.resqmesh.app.navigation

import edu.cit.resqmesh.app.navigation.MainDestinationContent
import edu.cit.resqmesh.app.navigation.overlays.MainApplicationOverlays
import edu.cit.resqmesh.core.ui.components.layout.ActiveSosReminder
import edu.cit.resqmesh.core.ui.components.layout.SosReminderHost
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import edu.cit.resqmesh.feature.comms.ui.ActiveChatScreen
import edu.cit.resqmesh.feature.comms.ui.PublicChatTab
import edu.cit.resqmesh.feature.radar.ui.NetworkScreen
import edu.cit.resqmesh.feature.sos.ui.SOSBroadcastScreen
import edu.cit.resqmesh.feature.sos.ui.SosMapScreen
import edu.cit.resqmesh.feature.profile.ui.ProfileScreen
import edu.cit.resqmesh.feature.profile.ui.AboutScreen
import edu.cit.resqmesh.feature.profile.ui.PermissionsSettingsScreen
import edu.cit.resqmesh.feature.profile.viewmodel.AboutViewModel
import edu.cit.resqmesh.feature.comms.viewmodel.CommunicationViewModel
import edu.cit.resqmesh.feature.radar.viewmodel.RadarViewModel
import edu.cit.resqmesh.feature.setup.viewmodel.SetupViewModel
import edu.cit.resqmesh.feature.setup.ui.FirstLaunchGuideScreen
import edu.cit.resqmesh.core.audio.MediaHelper
import edu.cit.resqmesh.core.ui.components.layout.ResQAppShell
import edu.cit.resqmesh.core.ui.components.layout.ResQAuroraBackground
import edu.cit.resqmesh.core.ui.components.layout.ResQDestination
import androidx.compose.foundation.layout.Box
import edu.cit.resqmesh.core.ui.theme.AppAppearance
import edu.cit.resqmesh.core.ui.theme.ResQMotion
import edu.cit.resqmesh.core.logging.AppLogger
import androidx.compose.ui.platform.LocalContext
import edu.cit.resqmesh.core.notification.NotificationHelper

@Composable
fun MainContainerScreen(
    setupViewModel: SetupViewModel,
    radarViewModel: RadarViewModel,
    commsViewModel: CommunicationViewModel,
    walkieTalkieViewModel: edu.cit.resqmesh.feature.comms.viewmodel.WalkieTalkieViewModel,
    mediaHelper: MediaHelper,
    appearance: AppAppearance,
    onAppearanceSelected: (AppAppearance) -> Unit,
    initialChatNode: String? = null,
    onClearInitialChatNode: (() -> Unit)? = null,
    initialViewMap: Boolean = false,
    initialSosSender: String? = null,
    initialSosText: String? = null,
    initialSosId: String? = null,
    onClearInitialViewMap: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val publicSendSnackbar = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(commsViewModel) {
        commsViewModel.publicSendFeedback.collect { publicSendSnackbar.showSnackbar(it) }
    }
    val notificationHelper = remember(context) { NotificationHelper(context) }
    var currentDestination by remember { mutableStateOf(ResQDestination.Mission) }

    // Sub-navigation state for prototype
    var activeChatNode by remember { mutableStateOf<String?>(null) }
    var isSOSActive by remember { mutableStateOf(false) }
    var showSosHub by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var sosThreadId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var showRadioHistory by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var mapSosAlert by remember { mutableStateOf<edu.cit.resqmesh.core.model.ChatMessage?>(null) }
    var showProfile by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    var showPermissions by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showOfflineMaps by remember { mutableStateOf(false) }
    var showNetworkDetails by remember { mutableStateOf(false) }
    var selectedNetworkPeerKey by remember { mutableStateOf<String?>(null) }
    var showIncidents by remember { mutableStateOf(false) }
    var isCommunityConversationOpen by remember { mutableStateOf(false) }

    val aboutViewModel: AboutViewModel = org.koin.androidx.compose.koinViewModel()
    val incidentViewModel: edu.cit.resqmesh.feature.incident.viewmodel.IncidentViewModel = org.koin.androidx.compose.koinViewModel()
    val incidentMetrics by incidentViewModel.metrics.collectAsState()

    val incomingSosAlert by commsViewModel.incomingSosAlert.collectAsState()
    val activeSosMessageId by commsViewModel.activeSosMessageId.collectAsState()
    val locationStatus by commsViewModel.locationStatus.collectAsState()

    LaunchedEffect(incomingSosAlert) {
        incomingSosAlert?.let { alert ->
            sosThreadId = alert.sosId
        }
    }

    val currentSosThreadId = sosThreadId ?: incomingSosAlert?.sosId

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
            if (!initialSosId.isNullOrBlank()) sosThreadId = initialSosId else showSosHub = true
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

    val isDeveloperMode by setupViewModel.isDeveloperModeEnabled.collectAsState()

    LaunchedEffect(Unit) {
        setupViewModel.initDeveloperMode(context)
    }

    val showOwnReminder = activeSosMessageId != null && incomingSosAlert == null &&
        mapSosAlert == null && currentSosThreadId == null && !isSOSActive && !showSosHub
    val showTopReminder = showOwnReminder && (activeChatNode != null || showRadioHistory ||
        isCommunityConversationOpen ||
        showPermissions || showAbout || showGuide || showProfile || showNetworkDetails ||
        showIncidents || showOfflineMaps)
    val openOwnSos = { sosThreadId = activeSosMessageId }
    Box(modifier = Modifier.fillMaxSize()) {
        SosReminderHost(showTopReminder, openOwnSos) {
            when {
                currentSosThreadId != null -> {
                    edu.cit.resqmesh.feature.sos.ui.SosThreadScreen(
                        vm = commsViewModel,
                        media = mediaHelper,
                        id = currentSosThreadId,
                        onBack = {
                            commsViewModel.clearSosAlert()
                            sosThreadId = null
                        },
                        onMap = { alert ->
                            commsViewModel.clearSosAlert()
                            sosThreadId = null
                            mapSosAlert = alert
                        }
                    )
                    BackHandler {
                        commsViewModel.clearSosAlert()
                        sosThreadId = null
                    }
                }
                showOfflineMaps -> {
                    ResQAuroraBackground(modifier = Modifier.fillMaxSize()) {
                        edu.cit.resqmesh.feature.profile.ui.OfflineMapSettingsScreen(
                            onBack = { showOfflineMaps = false },
                            returnToSosAlert = mapSosAlert != null
                        )
                    }
                    BackHandler { showOfflineMaps = false }
                }
                mapSosAlert != null -> {
                    SosMapScreen(
                        alertMessage = mapSosAlert!!,
                        onBack = { mapSosAlert = null },
                        onInstallMap = { showOfflineMaps = true }
                    )
                    BackHandler { mapSosAlert = null }
                }
                isSOSActive -> {
                    val cancelSosBroadcast = {
                        isSOSActive = false
                        showSosHub = true
                    }
                    SOSBroadcastScreen(
                        onCancel = cancelSosBroadcast,
                        onSosTriggered = { type ->
                            commsViewModel.sendEmergencySOS(type) { id -> sosThreadId = id; showSosHub = false }
                            isSOSActive = false
                        }
                    )
                    BackHandler(onBack = cancelSosBroadcast)
                }
                showSosHub -> {
                    edu.cit.resqmesh.feature.sos.ui.SosHubScreen(commsViewModel, mediaHelper,
                        onBack = { showSosHub = false },
                        onCreate = { isSOSActive = true; showSosHub = true },
                        onOpen = { sosThreadId = it })
                }
                showRadioHistory -> {
                    edu.cit.resqmesh.feature.comms.ui.RadioHistoryScreen(commsViewModel, mediaHelper,
                        onBack = { showRadioHistory = false })
                }
                isCommunityConversationOpen -> {
                    PublicChatTab(
                        viewModel = commsViewModel,
                        mediaHelper = mediaHelper,
                        onBack = { isCommunityConversationOpen = false },
                        onChatSelected = { user ->
                            isCommunityConversationOpen = false
                            activeChatNode = user
                        },
                        onViewMap = { lat, lng, sender, text ->
                            mapSosAlert = edu.cit.resqmesh.core.model.ChatMessage(
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
                    BackHandler { isCommunityConversationOpen = false }
                }
                activeChatNode != null -> {
                    ResQAuroraBackground(modifier = Modifier.fillMaxSize()) {
                        ActiveChatScreen(
                            name = activeChatNode!!,
                            viewModel = commsViewModel,
                            mediaHelper = mediaHelper,
                            onBack = { activeChatNode = null },
                            onViewMap = { lat, lng, sender, text ->
                                mapSosAlert = edu.cit.resqmesh.core.model.ChatMessage(
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
                showPermissions -> {
                    ResQAuroraBackground(modifier = Modifier.fillMaxSize()) {
                        PermissionsSettingsScreen(
                            onBack = { showPermissions = false }
                        )
                    }
                    BackHandler { showPermissions = false }
                }
                showAbout -> {
                    ResQAuroraBackground(modifier = Modifier.fillMaxSize()) {
                        AboutScreen(
                            viewModel = aboutViewModel,
                            onBack = { showAbout = false }
                        )
                    }
                    BackHandler { showAbout = false }
                }
                showGuide -> {
                    FirstLaunchGuideScreen(
                        isReplay = true,
                        onDone = { showGuide = false },
                        onClose = { showGuide = false }
                    )
                }
                showProfile -> {
                    ResQAuroraBackground(modifier = Modifier.fillMaxSize()) {
                        ProfileScreen(
                            viewModel = setupViewModel,
                            appearance = appearance,
                            onAppearanceSelected = onAppearanceSelected,
                            onPermissions = { showPermissions = true },
                            onOfflineMaps = { showOfflineMaps = true },
                            onHelp = { showGuide = true },
                            onAbout = { showAbout = true },
                            onBack = { showProfile = false }
                        )
                    }
                    BackHandler { showProfile = false }
                }
                showNetworkDetails -> {
                    ResQAuroraBackground(modifier = Modifier.fillMaxSize()) {
                        NetworkScreen(
                            viewModel = radarViewModel,
                            initialSelectedNodeKey = selectedNetworkPeerKey,
                            onMessagePeer = { peer ->
                                showNetworkDetails = false
                                selectedNetworkPeerKey = null
                                activeChatNode = peer
                            }
                        )
                    }
                    BackHandler {
                        showNetworkDetails = false
                        selectedNetworkPeerKey = null
                    }
                }
                showIncidents -> {
                    val incidentRadarState by radarViewModel.uiState.collectAsState()
                    ResQAuroraBackground(modifier = Modifier.fillMaxSize()) {
                        edu.cit.resqmesh.feature.incident.ui.IncidentListScreen(
                            viewModel = incidentViewModel,
                            radarState = incidentRadarState,
                            onBack = { showIncidents = false },
                            onDirectChat = { peerName -> activeChatNode = peerName },
                            onViewLocation = { lat, lng, reporter, description ->
                                mapSosAlert = edu.cit.resqmesh.core.model.ChatMessage(
                                    id = "incident_map_${System.currentTimeMillis()}",
                                    senderName = reporter,
                                    text = description,
                                    imageBase64 = null,
                                    audioBase64 = null,
                                    locationLat = lat,
                                    locationLng = lng,
                                    isMine = false,
                                    isPrivate = false
                                )
                            }
                        )
                    }
                    BackHandler { showIncidents = false }
                }
                else -> {
                    ResQAppShell(
                        selectedDestination = currentDestination,
                        onDestinationSelected = { currentDestination = it },
                        onSosActivated = { showSosHub = true },
                        showNavigation = true,
                        navigationReminder = if (showOwnReminder) {
                            { ActiveSosReminder(onClick = openOwnSos, modifier = Modifier.fillMaxWidth()) }
                        } else null
                    ) { innerPadding ->
                        AnimatedContent(
                            targetState = currentDestination,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .consumeWindowInsets(innerPadding)
                                .imePadding(),
                            transitionSpec = {
                                fadeIn(animationSpec = tween(ResQMotion.ScreenMillis, delayMillis = 60)) togetherWith
                                fadeOut(animationSpec = tween(ResQMotion.PressMillis))
                            },
                            label = "ScreenTransition"
                        ) { targetScreen ->
                            MainDestinationContent(targetScreen, setupViewModel, radarViewModel, commsViewModel, walkieTalkieViewModel, mediaHelper, locationStatus, incidentMetrics.totalActive, incidentMetrics.criticalCount,
                                onMessages = { currentDestination = ResQDestination.Messages },
                                onNetwork = {
                                        selectedNetworkPeerKey = null
                                        showNetworkDetails = true
                                    },
                                onPeer = { peerName ->
                                        activeChatNode = peerName
                                    },
                                onProfile = { showProfile = true },
                                onVoice = { currentDestination = ResQDestination.Voice },
                                onIncidents = { showIncidents = true },
                                onChat = { peerName ->
                                        activeChatNode = peerName
                                    },
                                onChat1 = { activeChatNode = it },
                                onCommunity = {
                                        isCommunityConversationOpen = true
                                    },
                                onCommunity1 = { isCommunityConversationOpen = true },
                                onMap = { lat, lng, sender, text ->
                                        mapSosAlert = edu.cit.resqmesh.core.model.ChatMessage(
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
                                    },
                                onHistory = { showRadioHistory = true })
                        }
                    }
                }
            }

        }

        MainApplicationOverlays(isDeveloperMode, incomingSosAlert != null, mapSosAlert != null, activeSosMessageId != null, isSOSActive, publicSendSnackbar,
            onToggleTerminal = { AppLogger.toggleTerminal() })
    }
}
