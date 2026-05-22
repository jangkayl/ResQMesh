package com.example.testresqmesh.feature.comms.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.example.testresqmesh.feature.comms.ui.components.EndOfMeshIndicator
import com.example.testresqmesh.feature.comms.ui.components.InboxMessageItem
import com.example.testresqmesh.core.utils.MediaHelper

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel

@Composable
fun PrivateChatTab(
    viewModel: CommunicationViewModel, 
    mediaHelper: MediaHelper,
    onChatSelected: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Group messages by sender for the inbox view
    val conversationItems = uiState.privateMessages.map { (id, messages) ->
        val lastMsg = messages.lastOrNull()
        
        // Prioritize connected device name, then look for a message not from "Me"
        val peerName = uiState.connectedDevices.find { it.endpointId == id }?.name 
            ?: messages.firstOrNull { it.senderName != "Me" }?.senderName 
            ?: id

        InboxItemData(
            id = id,
            name = peerName,
            message = lastMsg?.text ?: "Open channel",
            timestamp = lastMsg?.timestamp ?: 0L,
            hops = "DIRECT",
            hasNotification = false
        )
    }

    // Real connected devices that don't have messages yet
    val connectedWithoutMessages = uiState.connectedDevices.filter { device ->
        uiState.privateMessages.keys.none { it == device.endpointId }
    }.map { device ->
        InboxItemData(
            id = device.endpointId,
            name = device.name,
            message = "Tap to establish secure link",
            timestamp = 0L, // Newest connected but no messages yet
            hops = "DIRECT",
            hasNotification = false
        )
    }

    // Merge and sort: Newest messages (or newest connections) at top
    val allItems = (conversationItems + connectedWithoutMessages).sortedWith(
        compareByDescending<InboxItemData> { it.timestamp }
            .thenByDescending { it.id } // Tie-break with ID if needed
    )

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(allItems) { item ->
            val messages = uiState.privateMessages[item.id]
            messages?.lastOrNull()?.let { lastMsg ->
                if (!lastMsg.isMine && !lastMsg.seenBy.contains("Me")) {
                    LaunchedEffect(lastMsg.id) {
                        viewModel.markMessageAsSeen(lastMsg.id, isPrivate = true, targetId = item.id)
                    }
                }
            }

            InboxMessageItem(
                name = item.name,
                message = item.message,
                timestamp = if (item.timestamp == 0L) "now" else formatTimestamp(item.timestamp),
                hops = item.hops,
                hasNotification = item.hasNotification,
                onClick = { onChatSelected(item.id) }
            )
        }

        item {
            EndOfMeshIndicator()
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60 -> "now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        else -> "${days}d ago"
    }
}

data class InboxItemData(
    val id: String,
    val name: String,
    val message: String,
    val timestamp: Long,
    val hops: String,
    val hasNotification: Boolean = false
)
