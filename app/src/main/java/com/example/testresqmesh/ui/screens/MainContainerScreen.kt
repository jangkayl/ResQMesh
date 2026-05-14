package com.example.testresqmesh.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.ui.screens.comms.ActiveChatScreen
import com.example.testresqmesh.ui.screens.comms.ChatContainerScreen
import com.example.testresqmesh.ui.screens.radar.RadarScreen
import com.example.testresqmesh.ui.screens.radar.ResponderTrackerScreen
import com.example.testresqmesh.ui.screens.sos.SOSBroadcastScreen
import com.example.testresqmesh.ui.screens.profile.ProfileScreen
import com.example.testresqmesh.ui.viewmodel.ChatViewModel
import com.example.testresqmesh.utils.MediaHelper

sealed class NavItem(val route: String, val icon: ImageVector, val label: String) {
    object Radar : NavItem("radar", Icons.Default.Adjust, "Radar")
    object Messages : NavItem("messages", Icons.Default.ChatBubble, "Messages")
    object SOS : NavItem("sos", Icons.Default.Notifications, "SOS")
    object Settings : NavItem("settings", Icons.Default.Settings, "Settings")
}

@Composable
fun MainContainerScreen(viewModel: ChatViewModel, mediaHelper: MediaHelper) {
    var currentItem by remember { mutableStateOf<NavItem>(NavItem.Messages) }
    
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
        ActiveChatScreen(name = activeChatNode!!, onBack = { activeChatNode = null })
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
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentItem) {
                NavItem.Radar -> RadarScreen(viewModel) // Note: In a real app, clicking a node would set trackingNode
                NavItem.Messages -> ChatContainerScreen(viewModel, mediaHelper, onChatSelected = { activeChatNode = it }) 
                NavItem.Settings -> ProfileScreen(viewModel)
                else -> {}
            }
        }
    }
}
