package com.example.testresqmesh.feature.comms.ui

import android.Manifest
import android.content.pm.PackageManager
import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.model.NodeIdentity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Tune
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
import androidx.core.content.ContextCompat
import com.example.testresqmesh.R
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import com.example.testresqmesh.core.ui.components.feedback.ResQEmptyState
import com.example.testresqmesh.core.ui.components.layout.ResQAuroraBackground
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.utils.MediaHelper
import com.example.testresqmesh.feature.comms.ui.components.ChatBubble
import com.example.testresqmesh.feature.comms.ui.components.ChatInput
import com.example.testresqmesh.feature.comms.ui.components.SeenByBottomSheet
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicChatTab(
    viewModel: CommunicationViewModel,
    mediaHelper: MediaHelper,
    onBack: () -> Unit,
    onChatSelected: (String) -> Unit = {},
    onViewMap: (Double, Double, String, String) -> Unit = { _, _, _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()
    val channelId by viewModel.currentChannelId.collectAsState()
    val isAcquiringLocation by viewModel.isAcquiringLocation.collectAsState()

    BackHandler {
        onBack()
    }
    var inputText by remember { mutableStateOf("") }
    var pendingImage by remember { mutableStateOf<String?>(null) }
    var pendingAudio by remember { mutableStateOf<String?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var replyingToMessage by remember { mutableStateOf<ChatMessage?>(null) }
    val context = LocalContext.current
    val voiceNoteText = stringResource(R.string.private_chat_voice_note)
    val photoText = stringResource(R.string.private_chat_photo)
    val sortedMessages = remember(uiState.publicMessages) {
        uiState.publicMessages.sortedByDescending { it.timestamp }
    }
    val listState = rememberLazyListState()
    val latestMessageId = sortedMessages.firstOrNull()?.id

    LaunchedEffect(latestMessageId) {
        if (latestMessageId != null) listState.scrollToItem(0)
    }

    var displayLimit by remember { mutableStateOf(50) }
    val displayedMessages = remember(sortedMessages, displayLimit) {
        sortedMessages.take(displayLimit)
    }

    var activeSeenReaders by remember { mutableStateOf<List<String>?>(null) }
    val seenSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (activeSeenReaders != null) {
        SeenByBottomSheet(
            readers = activeSeenReaders!!,
            sheetState = seenSheetState,
            onDismissRequest = { activeSeenReaders = null },
            onUserClick = { selectedUser ->
                activeSeenReaders = null
                onChatSelected(selectedUser)
            }
        )
    }

    // Infinite scrolling: load next 50 messages when scrolled near the top of the list
    LaunchedEffect(listState.firstVisibleItemIndex, displayedMessages.size, sortedMessages.size) {
        if (displayedMessages.size < sortedMessages.size) {
            val lastVisibleIndex = listState.firstVisibleItemIndex + listState.layoutInfo.visibleItemsInfo.size
            if (lastVisibleIndex >= displayedMessages.size - 5) {
                displayLimit += 50
            }
        }
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
                    replyingTo = replyingToMessage,
                    onCancelReply = { replyingToMessage = null },
                    onSend = {
                        val rawMessage = when {
                            inputText.isNotBlank() -> inputText.trim()
                            pendingAudio != null -> voiceNoteText
                            pendingImage != null -> photoText
                            else -> ""
                        }
                        val currentReply = replyingToMessage
                        val finalMessage = if (currentReply != null && rawMessage.isNotBlank()) {
                            val quoteSender = NodeIdentity.displayNameOf(currentReply.senderName).ifBlank { currentReply.senderName }
                            val quoteSnippet = when {
                                currentReply.locationLat != null && currentReply.locationLng != null -> {
                                    "📍 Shared Location (%.4f, %.4f)".format(Locale.US, currentReply.locationLat, currentReply.locationLng)
                                }
                                currentReply.imageBase64 != null -> photoText
                                currentReply.audioBase64 != null -> voiceNoteText
                                else -> currentReply.text.take(60).ifBlank { "Attachment" }
                            }
                            "> $quoteSender: $quoteSnippet\n$rawMessage"
                        } else {
                            rawMessage
                        }
                        if (finalMessage.isNotBlank() || pendingImage != null || pendingAudio != null) {
                            viewModel.sendPublicMessage(finalMessage, pendingImage, pendingAudio)
                            inputText = ""
                            pendingImage = null
                            pendingAudio = null
                            replyingToMessage = null
                        }
                    },
                    onSendLocation = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        ) {
                            viewModel.broadcastLocation(
                                context = context,
                                isPrivate = false
                            )
                        }
                    },
                    mediaHelper = mediaHelper,
                    isAcquiringLocation = isAcquiringLocation
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
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(
                        start = Spacing.Medium,
                        top = Spacing.Small,
                        end = Spacing.Medium,
                        bottom = Spacing.Medium
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Small, Alignment.Bottom),
                    reverseLayout = true
                ) {
                    itemsIndexed(displayedMessages, key = { _, message -> message.id }) { index, message ->
                        if (!message.isMine && !message.seenBy.contains("Me")) {
                            LaunchedEffect(message.id) {
                                viewModel.markMessageAsSeen(message.id, isPrivate = false)
                            }
                        }
                        val isLatestInBlock = (index == 0 || displayedMessages[index - 1].senderName != message.senderName)
                        ChatBubble(
                            message = message,
                            mediaHelper = mediaHelper,
                            showAvatar = isLatestInBlock,
                            showSenderName = isLatestInBlock,
                            onUserClick = { onChatSelected(it) },
                            onShowSeenBy = { readers -> activeSeenReaders = readers },
                            onReplyClick = { replyingToMessage = it },
                            onViewMap = onViewMap
                        )
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
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.community_back_action)
                )
            }
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Campaign,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.community_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Public broadcast · Channel $channelId",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    onClick = { expanded = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CH $channelId",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
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
