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
fun PrivateChatTab(
    viewModel: CommunicationViewModel, 
    mediaHelper: MediaHelper,
    onChatSelected: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Group messages by sender for the inbox view
    val latestMessages = uiState.privateMessages.map { (id, messages) ->
        val lastMsg = messages.lastOrNull()
        InboxItemData(
            id = id,
            name = lastMsg?.senderName ?: "Unknown",
            message = lastMsg?.text ?: "Open channel",
            timestamp = "now",
            hops = "DIRECT",
            hasNotification = false
        )
    }

    val mockItems = listOf(
        InboxItemData("mock1", "0x7f...a1", "Supplies located at sector 4. Bringing", "2m ago", "2 HOPS", hasNotification = true),
        InboxItemData("mock2", "Alpha-9", "Confirming your location. Stay at the", "15m ago", "DIRECT"),
        InboxItemData("mock3", "0x2d...e9", "Is the channel still clear of interference?", "1h ago", "3 HOPS"),
        InboxItemData("mock4", "Nexus_Prime", "Relay established through substation 4.", "4h ago", "5 HOPS")
    )

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Real messages first
        items(latestMessages) { item ->
            InboxMessageItem(
                name = item.name,
                message = item.message,
                timestamp = item.timestamp,
                hops = item.hops,
                hasNotification = item.hasNotification,
                onClick = { onChatSelected(item.id) } // Pass the ID for opening chat
            )
        }

        // Mock items from the image
        items(mockItems) { item ->
            InboxMessageItem(
                name = item.name,
                message = item.message,
                timestamp = item.timestamp,
                hops = item.hops,
                hasNotification = item.hasNotification,
                onClick = { onChatSelected(item.name) }
            )
        }

        item {
            EndOfMeshIndicator()
        }
    }
}

data class InboxItemData(
    val id: String,
    val name: String,
    val message: String,
    val timestamp: String,
    val hops: String,
    val hasNotification: Boolean = false
)
