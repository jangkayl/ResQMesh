package com.example.testresqmesh.ui.screens.comms

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.testresqmesh.ui.components.chat.EndOfMeshIndicator
import com.example.testresqmesh.ui.components.chat.InboxMessageItem
import com.example.testresqmesh.ui.viewmodel.ChatViewModel
import com.example.testresqmesh.utils.MediaHelper

@Composable
fun PublicChatTab(viewModel: ChatViewModel, mediaHelper: MediaHelper) {
    // For the Broadcast tab, we'll show public channels or SOS alerts
    // For now, styling it identical to the mockup's list.
    
    val mockBroadcasts = listOf(
        InboxItemData("GLOBAL_SOS", "Critical alert: Earthquake detected in Sector 7", "1m ago", "SATURATED"),
        InboxItemData("RESCUE_TEAM_B", "Moving to extraction point Alpha", "10m ago", "2 HOPS"),
        InboxItemData("WEATHER_NODE", "Severe storm warning for next 2 hours", "45m ago", "4 HOPS")
    )

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(mockBroadcasts) { item ->
            InboxMessageItem(
                name = item.name,
                message = item.message,
                timestamp = item.timestamp,
                hops = item.hops,
                isEncrypted = false // Public broadcasts aren't encrypted for all
            )
        }

        // Real public messages if any
        items(viewModel.publicMessages.reversed()) { msg ->
            InboxMessageItem(
                name = msg.senderName,
                message = msg.text,
                timestamp = "now",
                hops = "MESH"
            )
        }

        item {
            EndOfMeshIndicator()
        }
    }
}
