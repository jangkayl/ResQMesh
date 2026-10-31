package edu.cit.resqmesh.feature.comms.ui

import edu.cit.resqmesh.feature.comms.ui.privatechat.PrivateChatHeader as PrivateChatHeaderSection
import edu.cit.resqmesh.feature.comms.ui.privatechat.PrivateMessageBubble
import edu.cit.resqmesh.feature.comms.ui.privatechat.deliveryFeedback as deliveryFeedbackSection
import edu.cit.resqmesh.feature.comms.ui.privatechat.deliveryLabel as deliveryLabelSection
import edu.cit.resqmesh.feature.comms.ui.privatechat.messageTime as messageTimeSection
import edu.cit.resqmesh.feature.comms.ui.privatechat.DeleteConversationDialog
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import edu.cit.resqmesh.feature.comms.ui.components.ChatInput
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
import edu.cit.resqmesh.R
import edu.cit.resqmesh.core.model.ChatMessage
import edu.cit.resqmesh.core.model.NodeIdentity
import edu.cit.resqmesh.core.ui.components.feedback.ResQEmptyState
import edu.cit.resqmesh.core.ui.components.layout.ResQAuroraBackground
import edu.cit.resqmesh.core.ui.theme.Spacing
import edu.cit.resqmesh.core.ui.theme.TestResQMeshTheme
import edu.cit.resqmesh.core.audio.MediaHelper
import edu.cit.resqmesh.feature.comms.viewmodel.CommunicationViewModel
import edu.cit.resqmesh.core.security.input.TextField
import edu.cit.resqmesh.core.security.input.TextPolicy
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
    val sendBudget by viewModel.chatBudgetState.collectAsState()
    val imageBudget by viewModel.imageBudgetState.collectAsState()
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
    var pendingImageQuality by remember { mutableStateOf(edu.cit.resqmesh.core.audio.ImageQuality.STANDARD) }
    var pendingAudio by remember { mutableStateOf<String?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var replyingToMessage by remember { mutableStateOf<ChatMessage?>(null) }
    // The quote is bounded (short snippet) and its length is reserved in the composer limit,
    // so the final text still fits TextField.MESSAGE after the prefix is added.
    val replyQuotePrefix = remember(replyingToMessage, displayName, photoText, voiceNoteText) {
        replyingToMessage?.let { quotePrefix(it, displayName, photoText, voiceNoteText) }.orEmpty()
    }
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

    edu.cit.resqmesh.feature.comms.ui.components.VoiceNoteLimitEffect(mediaHelper, isRecording) {
        isRecording = false
        mediaHelper.stopRecording()?.let { pendingAudio = it }
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
            title = { Text("This person's security code changed") },
            text = { Text("This could be a new phone, or someone else. Check with them in another way first. Then send your message again.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.acceptPendingPublicKeyChange(conversationName)
                    showKeyChangeDialog = false
                }) { Text("Yes, trust it") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.rejectPendingPublicKeyChange(conversationName)
                    showKeyChangeDialog = false
                }) { Text("No, keep old code") }
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
                    onImageSelected = { image, quality -> pendingImage = image; pendingImageQuality = quality },
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
                    maxInputLength = TextField.MESSAGE.maxLength - replyQuotePrefix.length,
                    onSend = {
                        val text = (drafts[conversationName] ?: drafts[name].orEmpty()).trim()
                        val rawMessage = when {
                            text.isNotBlank() -> text
                            pendingAudio != null -> voiceNoteText
                            pendingImage != null -> photoText
                            else -> ""
                        }
                        if (rawMessage.isBlank() && pendingImage == null && pendingAudio == null) return@ChatInput
                        val finalMessage = if (replyQuotePrefix.isNotEmpty() && rawMessage.isNotBlank()) {
                            replyQuotePrefix + rawMessage
                        } else {
                            rawMessage
                        }
                        val sent = viewModel.sendPrivateMessage(
                            targetName = conversationName,
                            text = finalMessage,
                            imageBase64 = pendingImage,
                            audioBase64 = pendingAudio,
                            imageQuality = pendingImageQuality
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
                    isAcquiringLocation = isAcquiringLocation,
                    sendBudget = sendBudget,
                    imageBudget = imageBudget
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


private const val QUOTE_SNIPPET_MAX = 60

/** Bounded "> sender: snippet" quote line (with trailing newline) prepended to a private reply. */
private fun quotePrefix(replyTo: ChatMessage, peerName: String, photoText: String, voiceNoteText: String): String {
    val sender = if (replyTo.isMine) "Me" else peerName
    val lat = replyTo.locationLat
    val lng = replyTo.locationLng
    val snippet = when {
        lat != null && lng != null -> "📍 Shared Location (%.4f, %.4f)".format(Locale.US, lat, lng)
        replyTo.imageBase64 != null -> photoText
        replyTo.audioBase64 != null -> voiceNoteText
        else -> TextPolicy.clampLength(replyTo.text, QUOTE_SNIPPET_MAX).ifBlank { "Attachment" }
    }
    return "> $sender: $snippet\n"
}
