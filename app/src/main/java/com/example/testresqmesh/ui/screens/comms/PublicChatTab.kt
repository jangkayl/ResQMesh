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
    
    // Sort messages by timestamp descending (newest first)
    val sortedMessages = uiState.publicMessages.sortedByDescending { it.timestamp }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(sortedMessages) { msg ->
            InboxMessageItem(
                name = msg.senderName,
                message = msg.text,
                timestamp = formatTimestamp(msg.timestamp),
                hops = "MESH",
                onClick = { onChatSelected(msg.senderName) }
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
