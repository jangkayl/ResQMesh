package com.example.testresqmesh.feature.comms.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.R
import com.example.testresqmesh.core.ui.components.feedback.ResQEmptyState
import com.example.testresqmesh.core.ui.components.layout.ResQAuroraBackground
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.utils.MediaHelper
import com.example.testresqmesh.feature.comms.ui.components.ChatBubble
import com.example.testresqmesh.feature.comms.ui.components.ChatInput
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel

@Composable
fun PublicChatTab(
    viewModel: CommunicationViewModel,
    mediaHelper: MediaHelper,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val channelId by viewModel.currentChannelId.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var pendingImage by remember { mutableStateOf<String?>(null) }
    var pendingAudio by remember { mutableStateOf<String?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val voiceNoteText = stringResource(R.string.private_chat_voice_note)
    val photoText = stringResource(R.string.private_chat_photo)
    val sortedMessages = remember(uiState.publicMessages) {
        uiState.publicMessages.sortedByDescending { it.timestamp }
    }

    ResQAuroraBackground(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                CommunityHeader(
                    channelId = channelId,
                    onBack = onBack,
                    onChannelSelected = viewModel::setChannel
                )
            },
            bottomBar = {
                ChatInput(
                    inputText = inputText,
                    onTextChange = { inputText = it },
                    pendingImage = pendingImage,
                    onImageSelected = { pendingImage = it },
                    onClearImage = { pendingImage = null },
                    pendingAudio = pendingAudio,
                    onClearAudio = { pendingAudio = null },
                    isRecording = isRecording,
                    onToggleRecord = {
                        if (isRecording) {
                            isRecording = false
                            mediaHelper.stopRecording()?.let { pendingAudio = it }
                        } else {
                            isRecording = mediaHelper.startRecording()
                        }
                    },
                    onSend = {
                        val message = when {
                            inputText.isNotBlank() -> inputText.trim()
                            pendingAudio != null -> voiceNoteText
                            pendingImage != null -> photoText
                            else -> ""
                        }
                        if (message.isNotBlank() || pendingImage != null || pendingAudio != null) {
                            viewModel.sendPublicMessage(message, pendingImage, pendingAudio)
                            inputText = ""
                            pendingImage = null
                            pendingAudio = null
                        }
                    },
                    onSendLocation = {
                        viewModel.broadcastLocation(
                            context = context,
                            isPrivate = false
                        )
                    },
                    mediaHelper = mediaHelper
                )
            }
        ) { innerPadding ->
            if (sortedMessages.isEmpty()) {
                ResQEmptyState(
                    title = stringResource(R.string.community_empty_title),
                    message = stringResource(R.string.community_empty_description),
                    icon = Icons.Outlined.Campaign,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(
                        start = Spacing.Large,
                        top = Spacing.Small,
                        end = Spacing.Large,
                        bottom = Spacing.Large
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                    reverseLayout = true
                ) {
                    items(sortedMessages, key = { it.id }) { message ->
                        if (!message.isMine && !message.seenBy.contains("Me")) {
                            LaunchedEffect(message.id) {
                                viewModel.markMessageAsSeen(message.id, isPrivate = false)
                            }
                        }
                        ChatBubble(message = message, mediaHelper = mediaHelper)
                    }
                }
            }
        }
    }
}

@Composable
internal fun CommunityHeader(
    channelId: String,
    onBack: () -> Unit,
    onChannelSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Small, vertical = Spacing.Small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.community_back_action)
                )
            }
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Campaign,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.width(Spacing.Small))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.community_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.community_description),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                TextButton(onClick = { expanded = true }) {
                    Text(
                        text = stringResource(R.string.messages_channel_short, channelId),
                        fontWeight = FontWeight.Bold
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    (1..5).forEach { channel ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.messages_channel_option, channel)) },
                            onClick = {
                                onChannelSelected(channel.toString())
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
