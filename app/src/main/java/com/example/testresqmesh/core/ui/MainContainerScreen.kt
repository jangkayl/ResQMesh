package com.example.testresqmesh.core.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.testresqmesh.feature.comms.ui.ActiveChatScreen
import com.example.testresqmesh.feature.sos.ui.ActiveSOSMonitoringScreen
import com.example.testresqmesh.feature.sos.ui.SosMapScreen
import com.example.testresqmesh.feature.sos.ui.FullScreenSosAlarm
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel
import com.example.testresqmesh.feature.radar.viewmodel.RadarViewModel
import com.example.testresqmesh.feature.setup.viewmodel.SetupViewModel
import com.example.testresqmesh.core.utils.MediaHelper

@Composable
fun MainContainerScreen(
    setupViewModel: SetupViewModel,
    radarViewModel: RadarViewModel,
    commsViewModel: CommunicationViewModel,
    mediaHelper: MediaHelper
) {
    var activeChatNode by remember { mutableStateOf<String?>(null) }
    var mapSosAlert by remember { mutableStateOf<com.example.testresqmesh.core.model.ChatMessage?>(null) }
    
    val context = LocalContext.current
    val incomingSosAlert by commsViewModel.incomingSosAlert.collectAsState()
    val activeSosMessageId by commsViewModel.activeSosMessageId.collectAsState()

    DisposableEffect(Unit) {
        commsViewModel.startLocationTracking(context)
        onDispose {
            commsViewModel.stopLocationTracking()
        }
    }

    if (incomingSosAlert != null) {
        FullScreenSosAlarm(
            alertMessage = incomingSosAlert!!,
            onDismiss = { commsViewModel.clearSosAlert() },
            onViewMap = { mapSosAlert = incomingSosAlert }
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

    // The entire old Scaffold / BottomNavBar is completely replaced by this revolutionary design
    MeshCanvasScreen(
        setupViewModel = setupViewModel,
        radarViewModel = radarViewModel,
        commsViewModel = commsViewModel,
        mediaHelper = mediaHelper,
        onActiveChatSet = { activeChatNode = it }
    )
}
