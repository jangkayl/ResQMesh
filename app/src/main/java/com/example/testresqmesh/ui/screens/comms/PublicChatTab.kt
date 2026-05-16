package com.example.testresqmesh.ui.screens.comms

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.testresqmesh.ui.components.chat.EndOfMeshIndicator
import com.example.testresqmesh.ui.components.chat.InboxMessageItem
import com.example.testresqmesh.utils.MediaHelper

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.testresqmesh.viewmodel.CommunicationViewModel

@Composable
fun PublicChatTab(
    viewModel: CommunicationViewModel, 
    mediaHelper: MediaHelper,
    onChatSelected: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    val mockBroadcasts = listOf(
        InboxItemData("mock1", "GLOBAL_SOS", "Critical alert: Earthquake detected in Sector 7", "1m ago", "SATURATED"),
        InboxItemData("mock2", "RESCUE_TEAM_B", "Moving to extraction point Alpha", "10m ago", "2 HOPS"),
        InboxItemData("mock3", "WEATHER_NODE", "Severe storm warning for next 2 hours", "45m ago", "4 HOPS")
    )

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Real public messages first
        items(uiState.publicMessages.reversed()) { msg ->
            InboxMessageItem(
                name = msg.senderName,
                message = msg.text,
                timestamp = "now",
                hops = "MESH",
                onClick = { onChatSelected(msg.senderName) }
            )
        }

        items(mockBroadcasts) { item ->
            InboxMessageItem(
                name = item.name,
                message = item.message,
                timestamp = item.timestamp,
                hops = item.hops,
                isEncrypted = false,
                onClick = { onChatSelected(item.name) }
            )
        }

        item {
            EndOfMeshIndicator()
        }
    }
}
