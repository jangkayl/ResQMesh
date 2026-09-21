package com.example.testresqmesh.feature.comms.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import com.example.testresqmesh.core.ui.components.dialogs.ResQConfirmationDialog
import com.example.testresqmesh.feature.comms.ui.components.ChatInput
import com.example.testresqmesh.feature.comms.ui.components.TacticalLocationCard
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.testresqmesh.R
import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.ui.components.feedback.ResQEmptyState
import com.example.testresqmesh.core.ui.components.layout.ResQAuroraBackground
import com.example.testresqmesh.core.ui.components.layout.ResQGlassSurface
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import com.example.testresqmesh.core.utils.MediaHelper
import com.example.testresqmesh.feature.comms.ui.components.FullscreenImageViewer
import com.example.testresqmesh.feature.comms.ui.components.ModernVoicePlayer
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel
import java.text.SimpleDateFormat
import java.util.Date
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
    val messages = uiState.privateMessages[name].orEmpty()
    val sortedMessages = remember(messages) { messages.sortedByDescending { it.timestamp } }
    val candidate = remember(uiState, name) {
        recipientCandidates(uiState).firstOrNull { NodeIdentity.matches(it.name, name) }
    }
    val displayName = remember(name) { NodeIdentity.displayNameOf(name).ifBlank { name } }
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
                viewModel.deleteConversationWith(name)
                onBack()
            }
        )
    }
    if (showKeyChangeDialog) {
        AlertDialog(
            onDismissRequest = {
                viewModel.rejectPendingPublicKeyChange(name)
                showKeyChangeDialog = false
            },
            title = { Text("Recipient key changed") },
            text = { Text("This device advertised a different encryption key. Accept it only after verifying the recipient through a separate channel, then resend your message.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.acceptPendingPublicKeyChange(name)
                    showKeyChangeDialog = false
                }) { Text("Accept new key") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.rejectPendingPublicKeyChange(name)
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
                    inputText = drafts[name].orEmpty(),
                    onTextChange = { viewModel.updatePrivateDraft(name, it) },
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
                        val text = drafts[name].orEmpty().trim()
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
                            targetName = name,
                            text = finalMessage,
                            imageBase64 = pendingImage,
                            audioBase64 = pendingAudio
                        )
                        if (sent) {
                            viewModel.clearPrivateDraft(name)
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
                                targetName = name
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
) {
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
                    contentDescription = stringResource(R.string.private_chat_back_action)
                )
            }
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = name.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(Modifier.width(Spacing.Small))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = availability.label(),
                    style = MaterialTheme.typography.labelMedium,
                    color = availability.color(),
                    fontWeight = FontWeight.SemiBold
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.private_chat_delete_action),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PrivateMessageBubble(
    message: ChatMessage,
    mediaHelper: MediaHelper,
    onViewMap: (Double, Double) -> Unit,
    onReplyClick: ((ChatMessage) -> Unit)? = null
) {
    val mine = message.isMine
    val bubbleColor = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val contentColor = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val shape = if (mine) {
        RoundedCornerShape(10.dp, 10.dp, 3.dp, 10.dp)
    } else {
        RoundedCornerShape(10.dp, 10.dp, 10.dp, 3.dp)
    }

    var fullScreenImage by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    if (fullScreenImage != null) {
        FullscreenImageViewer(
            imageBase64 = fullScreenImage!!,
            mediaHelper = mediaHelper,
            onDismiss = { fullScreenImage = null }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start
    ) {
        Box {
            Surface(
                modifier = Modifier
                    .widthIn(max = if (message.locationLat != null || message.imageBase64 != null) 300.dp else 260.dp)
                    .clickable { showMenu = true },
                shape = shape,
                color = bubbleColor,
                contentColor = contentColor,
                shadowElevation = if (mine) 3.dp else 4.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    message.imageBase64?.let { image ->
                        val bitmap = remember(image) { mediaHelper.decodeBase64ToBitmap(image) }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.private_chat_image_description),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { fullScreenImage = image },
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.height(Spacing.Small))
                        }
                    }
                    message.audioBase64?.let { audio ->
                        ModernVoicePlayer(
                            audioBase64 = audio,
                            mediaHelper = mediaHelper,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Spacer(Modifier.height(Spacing.ExtraSmall))
                    }

                    val (replyQuote, actualText) = remember(message.text) {
                        if (message.text.startsWith("> ") && message.text.contains("\n")) {
                            val firstNewline = message.text.indexOf("\n")
                            val quote = message.text.substring(2, firstNewline).trim()
                            val rest = message.text.substring(firstNewline + 1).trim()
                            quote to rest
                        } else {
                            null to message.text
                        }
                    }

                    if (replyQuote != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = contentColor.copy(alpha = 0.12f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(20.dp)
                                        .clip(RoundedCornerShape(1.5.dp))
                                        .background(if (mine) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.primary)
                                )
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Reply,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = if (mine) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = replyQuote,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = contentColor.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }

                    val isDefaultLocation = message.locationLat != null && (actualText.isBlank() || actualText.contains("I am sharing my location"))

                    if (message.locationLat != null && message.locationLng != null) {
                        TacticalLocationCard(
                            latitude = message.locationLat,
                            longitude = message.locationLng,
                            senderName = message.senderName,
                            noteText = if (!isDefaultLocation) actualText else null,
                            isMine = mine,
                            onTrackOnMap = { onViewMap(message.locationLat, message.locationLng) }
                        )
                        Spacer(Modifier.height(Spacing.Small))
                    }

                    if (actualText.isNotBlank() && !isDefaultLocation) {
                        Text(text = actualText, style = MaterialTheme.typography.bodyLarge)
                    }

                    Spacer(Modifier.height(Spacing.ExtraSmall))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (mine) {
                            Text(
                                text = deliveryLabel(message),
                                style = MaterialTheme.typography.labelMedium,
                                color = contentColor.copy(alpha = 0.72f)
                            )
                            Spacer(Modifier.width(Spacing.Small))
                        }
                        Text(
                            text = messageTime(message.timestamp),
                            style = MaterialTheme.typography.labelMedium,
                            color = contentColor.copy(alpha = 0.72f)
                        )
                    }
                }
            }

            if (onReplyClick != null) {
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_reply_action)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Reply,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showMenu = false
                            onReplyClick.invoke(message)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeleteConversationDialog(
    name: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    ResQConfirmationDialog(
        title = stringResource(R.string.private_chat_delete_title),
        message = stringResource(R.string.private_chat_delete_description, name),
        confirmText = stringResource(R.string.private_chat_delete_confirm),
        cancelText = stringResource(R.string.private_chat_cancel_action),
        icon = Icons.Outlined.DeleteOutline,
        isDestructive = true,
        onConfirm = onDelete,
        onDismiss = onDismiss
    )
}

internal enum class DeliveryFeedback {
    Pending,
    Sent,
    Delivered,
    Read,
    Failed
}

internal fun deliveryFeedback(message: ChatMessage): DeliveryFeedback = when {
    message.seenBy.isNotEmpty() -> DeliveryFeedback.Read
    message.deliveredTo.contains("FAILED") -> DeliveryFeedback.Failed
    message.deliveredTo.contains("PENDING") -> DeliveryFeedback.Pending
    message.deliveredTo.isNotEmpty() -> DeliveryFeedback.Delivered
    else -> DeliveryFeedback.Sent
}

@Composable
internal fun deliveryLabel(message: ChatMessage): String = stringResource(
    when (deliveryFeedback(message)) {
        DeliveryFeedback.Pending -> R.string.private_chat_status_pending
        DeliveryFeedback.Sent -> R.string.private_chat_status_sent
        DeliveryFeedback.Delivered -> R.string.private_chat_status_delivered
        DeliveryFeedback.Read -> R.string.private_chat_status_read
        DeliveryFeedback.Failed -> R.string.private_chat_status_failed
    }
)

internal fun messageTime(timestamp: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))

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
