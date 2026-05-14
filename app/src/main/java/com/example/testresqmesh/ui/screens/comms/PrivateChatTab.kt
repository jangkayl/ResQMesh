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
fun PrivateChatTab(
    viewModel: ChatViewModel, 
    mediaHelper: MediaHelper,
    onChatSelected: (String) -> Unit
) {
    // For the mockup reproduction, we'll use a mix of real and mock data
    // to ensure it matches the image perfectly.
    
    val mockItems = listOf(
        InboxItemData("0x7f...a1", "Supplies located at sector 4. Bringing", "2m ago", "2 HOPS", hasNotification = true),
        InboxItemData("Alpha-9", "Confirming your location. Stay at the", "15m ago", "DIRECT"),
        InboxItemData("0x2d...e9", "Is the channel still clear of interference?", "1h ago", "3 HOPS"),
        InboxItemData("Nexus_Prime", "Relay established through substation 4.", "4h ago", "5 HOPS")
    )

    LazyColumn(modifier = Modifier.fillMaxSize()) {
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

        // Real connected devices if any
        items(viewModel.connectedDevices) { device ->
            InboxMessageItem(
                name = device.name,
                message = "Tap to open private channel",
                timestamp = "now",
                hops = "DIRECT",
                onClick = { onChatSelected(device.name) }
            )
        }

        item {
            EndOfMeshIndicator()
        }
    }
}

data class InboxItemData(
    val name: String,
    val message: String,
    val timestamp: String,
    val hops: String,
    val hasNotification: Boolean = false
)
