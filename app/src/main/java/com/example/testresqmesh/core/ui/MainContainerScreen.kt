package com.example.testresqmesh.core.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.feature.comms.ui.ActiveChatScreen
import com.example.testresqmesh.feature.comms.ui.ChatContainerScreen
import com.example.testresqmesh.feature.radar.ui.RadarScreen
import com.example.testresqmesh.feature.radar.ui.ResponderTrackerScreen
import com.example.testresqmesh.feature.sos.ui.SOSBroadcastScreen
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

    val items = listOf(NavItem.Radar, NavItem.Messages, NavItem.SOS, NavItem.Settings)

    if (isSOSActive) {
        SOSBroadcastScreen(onCancel = { isSOSActive = false })
        BackHandler { isSOSActive = false }
        return
    }

    if (activeChatNode != null) {
        ActiveChatScreen(
            name = activeChatNode!!, 
            viewModel = commsViewModel,
            onBack = { activeChatNode = null }
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
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp
            ) {
                items.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentItem == item,
                        onClick = { 
                            if (item == NavItem.SOS) {
                                isSOSActive = true
                            } else {
                                currentItem = item 
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            unselectedTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            indicatorColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentItem,
            modifier = Modifier.padding(innerPadding),
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
