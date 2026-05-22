package com.example.testresqmesh.feature.comms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.theme.InboxBackground
import com.example.testresqmesh.core.ui.theme.InboxTextSecondary
import com.example.testresqmesh.core.ui.theme.WarningAmber
import com.example.testresqmesh.core.utils.MediaHelper
import com.example.testresqmesh.feature.comms.ui.components.ChatBubble
import com.example.testresqmesh.feature.comms.ui.components.ChatInput
import com.example.testresqmesh.feature.comms.ui.components.EndOfMeshIndicator
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel

@Composable
fun PublicChatTab(
    viewModel: CommunicationViewModel,
    mediaHelper: MediaHelper,
    onChatSelected: (String) -> Unit // Keeping this in case we want to tap avatars later
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    var inputText by remember { mutableStateOf("") }
    var pendingImage by remember { mutableStateOf<String?>(null) }
    var isRecording by remember { mutableStateOf(false) }

    // Sort messages by timestamp descending (newest first). 
    // reverseLayout = true will anchor newest to the bottom.
    val sortedMessages = uiState.publicMessages.sortedByDescending { it.timestamp }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(InboxBackground)
    ) {
        // Sticky Header Banner
        Surface(
            color = WarningAmber.copy(alpha = 0.15f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📢", fontSize = 24.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "PUBLIC BROADCAST CHANNEL", 
                        style = MaterialTheme.typography.labelLarge,
                        color = WarningAmber,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Messages here are visible to all connected nodes. Do not share sensitive private data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InboxTextSecondary,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        // Chat History
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(sortedMessages) { msg ->
                ChatBubble(message = msg, mediaHelper = mediaHelper)
            }

            item {
                EndOfMeshIndicator()
            }
        }

        // Chat Input Box
        ChatInput(
            inputText = inputText,
            onTextChange = { inputText = it },
            pendingImage = pendingImage,
            onImageSelected = { pendingImage = it },
            onClearImage = { pendingImage = null },
            isRecording = isRecording,
            onToggleRecord = { isRecording = !isRecording },
            onSend = {
                viewModel.sendPublicMessage(
                    text = inputText.trim(),
                    imageBase64 = pendingImage,
                    audioBase64 = null
                )
                // Clear input after sending
                inputText = ""
                pendingImage = null
                focusManager.clearFocus()
            },
            onSendLocation = {
                viewModel.broadcastLocation(context = context, isPrivate = false)
            },
            mediaHelper = mediaHelper
        )
    }
}

