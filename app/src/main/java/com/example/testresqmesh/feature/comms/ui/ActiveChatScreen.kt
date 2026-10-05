package com.example.testresqmesh.feature.comms.ui

import com.example.testresqmesh.feature.comms.ui.privatechat.PrivateChatHeader as PrivateChatHeaderSection
import com.example.testresqmesh.feature.comms.ui.privatechat.PrivateMessageBubble
import com.example.testresqmesh.feature.comms.ui.privatechat.deliveryFeedback as deliveryFeedbackSection
import com.example.testresqmesh.feature.comms.ui.privatechat.deliveryLabel as deliveryLabelSection
import com.example.testresqmesh.feature.comms.ui.privatechat.messageTime as messageTimeSection
import com.example.testresqmesh.feature.comms.ui.privatechat.DeleteConversationDialog
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.example.testresqmesh.feature.comms.ui.components.ChatInput
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.example.testresqmesh.R
import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.ui.components.feedback.ResQEmptyState
import com.example.testresqmesh.core.ui.components.layout.ResQAuroraBackground
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import com.example.testresqmesh.core.utils.MediaHelper
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel
import java.util.Locale

@Composable
fun ActiveChatScreen(
    name: String,
    viewModel: CommunicationViewModel,
    mediaHelper: MediaHelper,
    onBack: () -> Unit,
    onViewMap: (Double, Double, String, String) -> Unit = { _, _, _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()
    val drafts by viewModel.privateDrafts.collectAsState()
    val isAcquiringLocation by viewModel.isAcquiringLocation.collectAsState()
    val conversationEntry = uiState.privateMessages.entries.firstOrNull {
        NodeIdentity.key(it.key) == NodeIdentity.key(name)
    }
    val conversationName = conversationEntry?.key ?: name
    val messages = conversationEntry?.value.orEmpty()
    val sortedMessages = remember(messages) { messages.sortedByDescending { it.timestamp } }
    val candidate = remember(uiState, name) {
        recipientCandidates(uiState).firstOrNull { NodeIdentity.matches(it.name, name) }
    }
    val displayName = remember(conversationName, uiState.peerNames) {
        NodeIdentity.displayNameOf(NodeIdentity.currentName(conversationName, uiState.peerNames)).ifBlank { conversationName }
    }
    val context = LocalContext.current
    val voiceNoteText = stringResource(R.string.private_chat_voice_note)
    val photoText = stringResource(R.string.private_chat_photo)
    val listState = rememberLazyListState()
    val latestMessageId = sortedMessages.firstOrNull()?.id
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingImage by remember { mutableStateOf<String?>(null) }
    var pendingAudio by remember { mutableStateOf<String?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var replyingToMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showKeyChangeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.privateSendErrors.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(viewModel, name) {
        viewModel.pendingKeyVerification.collect { peer ->
            if (NodeIdentity.matches(peer, name)) showKeyChangeDialog = true
        }
    }

    LaunchedEffect(latestMessageId) {
        if (latestMessageId != null) listState.scrollToItem(0)
    }

    if (showDeleteDialog) {
        DeleteConversationDialog(
            name = displayName,
            onDismiss = { showDeleteDialog = false },
            onDelete = {
                viewModel.deleteConversationWith(conversationName)
                onBack()
            }
        )
    }
    if (showKeyChangeDialog) {
        AlertDialog(
            onDismissRequest = {
                viewModel.rejectPendingPublicKeyChange(conversationName)
                showKeyChangeDialog = false
            },
            title = { Text("Recipient key changed") },
            text = { Text("This device advertised a different encryption key. Accept it only after verifying the recipient through a separate channel, then resend your message.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.acceptPendingPublicKeyChange(conversationName)
                    showKeyChangeDialog = false
                }) { Text("Accept new key") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.rejectPendingPublicKeyChange(conversationName)
                    showKeyChangeDialog = false
                }) { Text("Keep existing key") }
            }
        )
    }

    ResQAuroraBackground(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                PrivateChatHeader(
                    name = displayName,
                    availability = candidate?.availability ?: RecipientAvailability.Offline,
                    onBack = onBack,
                    onDelete = { showDeleteDialog = true }
                )
            },
            bottomBar = {
                ChatInput(
                    inputText = drafts[conversationName] ?: drafts[name].orEmpty(),
                    onTextChange = { viewModel.updatePrivateDraft(conversationName, it) },
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
                        val text = (drafts[conversationName] ?: drafts[name].orEmpty()).trim()
                        val rawMessage = when {
                            text.isNotBlank() -> text
                            pendingAudio != null -> voiceNoteText
                            pendingImage != null -> photoText
                            else -> ""
                        }
                        if (rawMessage.isBlank() && pendingImage == null && pendingAudio == null) return@ChatInput
                        val finalMessage = if (replyingToMessage != null && rawMessage.isNotBlank()) {
                            val quoteSender = if (replyingToMessage!!.isMine) "Me" else displayName
                            val quoteSnippet = when {
                                replyingToMessage!!.locationLat != null && replyingToMessage!!.locationLng != null -> {
                                    "📍 Shared Location (%.4f, %.4f)".format(Locale.US, replyingToMessage!!.locationLat, replyingToMessage!!.locationLng)
                                }
                                replyingToMessage!!.imageBase64 != null -> photoText
                                replyingToMessage!!.audioBase64 != null -> voiceNoteText
                                else -> replyingToMessage!!.text.take(60).ifBlank { "Attachment" }
                            }
                            "> $quoteSender: $quoteSnippet\n$rawMessage"
                        } else {
                            rawMessage
                        }
                        val sent = viewModel.sendPrivateMessage(
                            targetName = conversationName,
                            text = finalMessage,
                            imageBase64 = pendingImage,
                            audioBase64 = pendingAudio
                        )
                        if (sent) {
                            viewModel.clearPrivateDraft(conversationName)
                            if (conversationName != name) viewModel.clearPrivateDraft(name)
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
                                isPrivate = true,
                                targetName = conversationName
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
                    title = stringResource(R.string.private_chat_empty_title),
                    message = stringResource(R.string.private_chat_empty_description),
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
                    items(sortedMessages, key = { it.id }) { message ->
                        if (!message.isMine && !message.seenBy.contains("Me")) {
                            LaunchedEffect(message.id) {
                                viewModel.markMessageAsSeen(message.id, isPrivate = true, targetId = name)
                            }
                        }
                        PrivateMessageBubble(
                            message = message,
                            mediaHelper = mediaHelper,
                            onViewMap = { latitude, longitude ->
                                onViewMap(latitude, longitude, message.senderName, message.text)
                            },
                            onReplyClick = { replyingToMessage = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PrivateChatHeader(
    name: String,
    availability: RecipientAvailability,
    onBack: () -> Unit,
    onDelete: () -> Unit
) = PrivateChatHeaderSection(name, availability, onBack, onDelete)

internal fun deliveryFeedback(message: ChatMessage): DeliveryFeedback = deliveryFeedbackSection(message)

@Composable
internal fun deliveryLabel(message: ChatMessage): String = deliveryLabelSection(message)

internal fun messageTime(timestamp: Long): String = messageTimeSection(timestamp)

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PrivateChatEmptyPreview() {
    TestResQMeshTheme {
        ResQAuroraBackground(Modifier.fillMaxSize()) {
            ResQEmptyState(
                title = "No messages yet",
                message = "Say hello to start.",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
