package com.example.testresqmesh.core.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.font.FontWeight
import com.example.testresqmesh.core.ui.theme.*
import com.example.testresqmesh.feature.comms.ui.ActiveChatScreen
import com.example.testresqmesh.feature.comms.ui.ChatContainerScreen
import com.example.testresqmesh.feature.radar.ui.RadarScreen
import com.example.testresqmesh.feature.radar.ui.ResponderTrackerScreen
import com.example.testresqmesh.feature.sos.ui.SOSBroadcastScreen
import com.example.testresqmesh.feature.sos.ui.FullScreenSosAlarm
import com.example.testresqmesh.feature.sos.ui.ActiveSOSMonitoringScreen
import com.example.testresqmesh.feature.sos.ui.SosMapScreen
import com.example.testresqmesh.feature.sos.ui.OfflineMapPromptModal
import com.example.testresqmesh.feature.sos.utils.MapDownloadManager
import com.example.testresqmesh.feature.profile.ui.ProfileScreen
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel
import com.example.testresqmesh.feature.radar.viewmodel.RadarViewModel
import com.example.testresqmesh.feature.setup.viewmodel.SetupViewModel
import com.example.testresqmesh.core.utils.MediaHelper

sealed class NavItem(val route: String, val icon: ImageVector, val label: String) {
    object Radar : NavItem("radar", Icons.Default.Adjust, "Radar")
    object Messages : NavItem("messages", Icons.Default.ChatBubble, "Messages")
    object SOS : NavItem("sos", Icons.Default.Notifications, "SOS")
    object Settings : NavItem("settings", Icons.Default.Settings, "Settings")
}

@Composable
fun MainContainerScreen(
    setupViewModel: SetupViewModel,
    radarViewModel: RadarViewModel,
    commsViewModel: CommunicationViewModel,
    mediaHelper: MediaHelper
) {
    var currentItem by remember { mutableStateOf<NavItem>(NavItem.Radar) }
    
    // Sub-navigation state for prototype
    var activeChatNode by remember { mutableStateOf<String?>(null) }
    var trackingNode by remember { mutableStateOf<String?>(null) }
    var isSOSActive by remember { mutableStateOf(false) }
    var mapSosAlert by remember { mutableStateOf<com.example.testresqmesh.core.model.ChatMessage?>(null) }
    
    val context = LocalContext.current
    val mapDownloadManager = remember { MapDownloadManager(context) }
    var showMapDownloadPrompt by remember { mutableStateOf(!mapDownloadManager.isMapDownloaded()) }
    
    val incomingSosAlert by commsViewModel.incomingSosAlert.collectAsState()
    val activeSosMessageId by commsViewModel.activeSosMessageId.collectAsState()

    DisposableEffect(Unit) {
        // Start passive location tracking when the node is active
        commsViewModel.startLocationTracking(context)
        onDispose {
            // Cleanup when the node is shut down or the app closes
            commsViewModel.stopLocationTracking()
        }
    }

    val items = listOf(NavItem.Radar, NavItem.Messages, NavItem.SOS, NavItem.Settings)

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
        val context = LocalContext.current
        SOSBroadcastScreen(
            onCancel = { isSOSActive = false },
            onSosTriggered = { type ->
                commsViewModel.sendEmergencySOS(context, type)
                isSOSActive = false
            }
        )
        BackHandler { isSOSActive = false }
        return
    }

    if (showMapDownloadPrompt) {
        OfflineMapPromptModal(
            downloadManager = mapDownloadManager,
            onDismiss = { showMapDownloadPrompt = false }
        )
    }

    if (activeChatNode != null) {
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
        BackHandler { activeChatNode = null }
        return
    }

    if (trackingNode != null) {
        ResponderTrackerScreen(
            nodeName = trackingNode!!, 
            onBack = { trackingNode = null },
            onChat = { 
                activeChatNode = trackingNode
                trackingNode = null 
            }
        )
        BackHandler { trackingNode = null }
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars,
        bottomBar = {
            NavigationBar(
                containerColor = WarmWhite,
                tonalElevation = 8.dp
            ) {
                items.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) },
                        selected = currentItem == item,
                        onClick = { 
                            if (item == NavItem.SOS) {
                                isSOSActive = true
                            } else {
                                currentItem = item 
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryRed,
                            selectedTextColor = PrimaryRed,
                            unselectedIconColor = MediumGray,
                            unselectedTextColor = MediumGray,
                            indicatorColor = PrimaryRed.copy(alpha = 0.1f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentItem,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
            transitionSpec = {
                fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith
                fadeOut(animationSpec = tween(90))
            },
            label = "ScreenTransition"
        ) { targetScreen ->
            when (targetScreen) {
                NavItem.Radar -> RadarScreen(radarViewModel)
                NavItem.Messages -> ChatContainerScreen(
                    viewModel = commsViewModel, 
                    mediaHelper = mediaHelper, 
                    onChatSelected = { activeChatNode = it }
                ) 
                NavItem.Settings -> ProfileScreen(setupViewModel)
                else -> {}
            }
        }
    }
}
