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
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.testresqmesh.R
import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.model.AttachmentStatus
import com.example.testresqmesh.core.model.AttachmentUiState
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.ui.components.feedback.ResQEmptyState
import com.example.testresqmesh.core.ui.components.layout.ResQAuroraBackground
import com.example.testresqmesh.core.ui.components.layout.ResQGlassSurface
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import com.example.testresqmesh.core.utils.MediaHelper
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
    var pendingImage by remember { mutableStateOf<Uri?>(null) }
    var pendingAudio by remember { mutableStateOf<String?>(null) }
    var isRecording by remember { mutableStateOf(false) }
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
            onDismissRequest = { showKeyChangeDialog = false },
            title = { Text("Recipient key changed") },
            text = { Text("This device advertised a different encryption key. Accept it only after verifying the recipient through a separate channel, then resend your message.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.acceptPendingPublicKeyChange(name)
                    showKeyChangeDialog = false
                }) { Text("Accept new key") }
            },
            dismissButton = { TextButton(onClick = { showKeyChangeDialog = false }) { Text("Keep existing key") } }
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
                PrivateChatComposer(
                    draft = drafts[name].orEmpty(),
                    onDraftChange = { viewModel.updatePrivateDraft(name, it) },
                    pendingImage = pendingImage,
                    onImageSelected = { pendingImage = it; pendingAudio = null },
                    onClearImage = { pendingImage = null },
                    pendingAudio = pendingAudio,
                    onClearAudio = { pendingAudio = null },
                    isRecording = isRecording,
                    onToggleRecording = {
                        if (isRecording) {
                            isRecording = false
                            mediaHelper.stopRecording()?.let { pendingAudio = it }
                        } else {
                            isRecording = mediaHelper.startRecording()
                        }
                    },
                    onSend = {
                        val text = drafts[name].orEmpty().trim()
                        val messageText = when { text.isNotBlank() -> text; pendingAudio != null -> voiceNoteText; pendingImage != null -> photoText; else -> "" }
                        if (messageText.isBlank() && pendingImage == null && pendingAudio == null) return@PrivateChatComposer
                        val selectedImage = pendingImage
                        if (selectedImage != null) {
                            viewModel.sendPrivateImage(name, messageText, selectedImage) { sent ->
                                if (sent) { viewModel.clearPrivateDraft(name); pendingImage = null; pendingAudio = null }
                            }
                        } else {
                            val sent = viewModel.sendPrivateMessage(name, messageText, audioBase64 = pendingAudio)
                            if (sent) { viewModel.clearPrivateDraft(name); pendingAudio = null }
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
                    mediaHelper = mediaHelper
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
                            attachment = message.attachmentId?.let(uiState.attachments::get),
                            mediaHelper = mediaHelper,
                            onDownload = viewModel::requestAttachmentDownload,
                            onCancelAttachment = viewModel::cancelAttachment,
                            onViewMap = { latitude, longitude ->
                                onViewMap(latitude, longitude, message.senderName, message.text)
                            }
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
    attachment: AttachmentUiState?,
    mediaHelper: MediaHelper,
    onDownload: (String) -> Unit,
    onCancelAttachment: (String) -> Unit,
    onViewMap: (Double, Double) -> Unit
) {
    val mine = message.isMine
    val bubbleColor = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val contentColor = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val shape = if (mine) {
        RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp)
    } else {
        RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = shape,
            color = bubbleColor,
            contentColor = contentColor,
            shadowElevation = if (mine) 4.dp else 8.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = Spacing.Medium, vertical = 10.dp)) {
                attachment?.let {
                    PrivateAttachmentCard(
                        attachment = it,
                        mediaHelper = mediaHelper,
                        onDownload = { onDownload(it.attachmentId) },
                        onCancel = { onCancelAttachment(it.attachmentId) }
                    )
                    Spacer(Modifier.height(Spacing.Small))
                }
                message.imageBase64?.let { image ->
                    val bitmap = remember(image) { mediaHelper.decodeBase64ToBitmap(image) }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.private_chat_image_description),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(Spacing.Small))
                    }
                }
                message.audioBase64?.let { audio ->
                    TextButton(onClick = { mediaHelper.playVoiceMail(audio) }) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(Spacing.ExtraSmall))
                        Text(stringResource(R.string.private_chat_voice_note))
                    }
                }
                if (message.locationLat != null && message.locationLng != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = contentColor.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.Small),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.LocationOn, contentDescription = null)
                            Spacer(Modifier.width(Spacing.ExtraSmall))
                            Text(stringResource(R.string.private_chat_location_shared))
                            Spacer(Modifier.width(Spacing.Small))
                            TextButton(onClick = { onViewMap(message.locationLat, message.locationLng) }) {
                                Text(stringResource(R.string.private_chat_view_map_action))
                            }
                        }
                    }
                    Spacer(Modifier.height(Spacing.Small))
                }
                if (message.text.isNotBlank()) {
                    Text(text = message.text, style = MaterialTheme.typography.bodyLarge)
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
    }
}

@Composable
private fun PrivateAttachmentCard(
    attachment: AttachmentUiState,
    mediaHelper: MediaHelper,
    onDownload: () -> Unit,
    onCancel: () -> Unit
) {
    var showImage by remember(attachment.localPath) { mutableStateOf(false) }
    val preview = remember(attachment.previewPath) { attachment.previewPath?.let(mediaHelper::decodeFileToBitmap) }
    val full = remember(attachment.localPath) { attachment.localPath?.let(mediaHelper::decodeFileToBitmap) }
    val complete = attachment.status == AttachmentStatus.COMPLETE
    val progress = if (attachment.byteSize == 0) 0f else attachment.receivedBytes.toFloat() / attachment.byteSize
    Column(modifier = Modifier.fillMaxWidth()) {
        (if (complete) full else preview)?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = if (complete) "Private photo. Tap to view full size." else "Private photo preview.",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(14.dp))
                    .then(if (complete) Modifier.clickable { showImage = true } else Modifier)
            )
        }
        Spacer(Modifier.height(Spacing.ExtraSmall))
        Text(attachmentLabel(attachment), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        if (attachment.status == AttachmentStatus.TRANSFERRING || attachment.status == AttachmentStatus.DOWNLOAD_REQUESTED) {
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = Spacing.ExtraSmall))
        }
        attachment.failureReason?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        when (attachment.status) {
            AttachmentStatus.OFFERED, AttachmentStatus.PAUSED_ROUTE_UNAVAILABLE, AttachmentStatus.CORRUPT_RETRY ->
                Button(onClick = onDownload, modifier = Modifier.padding(top = Spacing.Small)) { Text(if (attachment.status == AttachmentStatus.OFFERED) "Download image" else "Retry download") }
            AttachmentStatus.DOWNLOAD_REQUESTED, AttachmentStatus.TRANSFERRING ->
                TextButton(onClick = onCancel) { Text("Cancel transfer") }
            else -> Unit
        }
    }
    if (showImage && full != null) {
        AlertDialog(
            onDismissRequest = { showImage = false },
            confirmButton = { TextButton(onClick = { showImage = false }) { Text("Close") } },
            text = { Image(full.asImageBitmap(), contentDescription = "Full private photo", modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Fit) }
        )
    }
}

private fun attachmentLabel(attachment: AttachmentUiState): String = when (attachment.status) {
    AttachmentStatus.QUEUED_OFFER -> "Offering private image"
    AttachmentStatus.OFFERED -> "Private image ready to download"
    AttachmentStatus.DOWNLOAD_REQUESTED -> "Download requested"
    AttachmentStatus.TRANSFERRING -> "Downloading ${(attachment.receivedBytes * 100 / attachment.byteSize.coerceAtLeast(1))}%"
    AttachmentStatus.PAUSED_ROUTE_UNAVAILABLE -> "Paused — mesh route unavailable"
    AttachmentStatus.COMPLETE -> "Private image delivered"
    AttachmentStatus.CORRUPT_RETRY -> "Image needs another download"
    AttachmentStatus.CANCELLED -> "Image transfer cancelled"
    AttachmentStatus.SOURCE_UNAVAILABLE -> "Image is no longer available from sender"
}

@Composable
private fun PrivateChatComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    pendingImage: Uri?,
    onImageSelected: (Uri) -> Unit,
    onClearImage: () -> Unit,
    pendingAudio: String?,
    onClearAudio: () -> Unit,
    isRecording: Boolean,
    onToggleRecording: () -> Unit,
    onSend: () -> Unit,
    onSendLocation: () -> Unit,
    mediaHelper: MediaHelper
) {
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let(onImageSelected)
    }
    val canSend = (draft.isNotBlank() || pendingImage != null || pendingAudio != null) && !isRecording

    ResQGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = Spacing.Small, vertical = Spacing.Small),
        shape = RoundedCornerShape(28.dp),
        contentPadding = PaddingValues(Spacing.Small),
        shadowElevation = 18.dp
    ) {
        Column {
            if (pendingImage != null || pendingAudio != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (pendingImage != null) stringResource(R.string.private_chat_photo) else stringResource(R.string.private_chat_voice_note),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        if (pendingImage != null) onClearImage() else onClearAudio()
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.private_chat_remove_attachment)
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { imagePicker.launch("image/*") }) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = stringResource(R.string.private_chat_add_image_action),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onSendLocation) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = stringResource(R.string.private_chat_share_location_action),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onToggleRecording) {
                    Icon(
                        imageVector = if (isRecording) Icons.Outlined.Stop else Icons.Outlined.Mic,
                        contentDescription = stringResource(
                            if (isRecording) R.string.private_chat_stop_recording_action else R.string.private_chat_record_action
                        ),
                        tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        enabled = !isRecording,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = Spacing.Medium, vertical = 13.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        decorationBox = { input ->
                            if (draft.isEmpty() && !isRecording) {
                                Text(
                                    text = stringResource(R.string.private_chat_message_placeholder),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            input()
                        }
                    )
                }
                IconButton(onClick = onSend, enabled = canSend) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = stringResource(R.string.private_chat_send_action),
                        tint = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.private_chat_delete_title)) },
        text = { Text(stringResource(R.string.private_chat_delete_description, name)) },
        confirmButton = {
            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text(stringResource(R.string.private_chat_delete_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.private_chat_cancel_action)) }
        }
    )
}

internal enum class DeliveryFeedback {
    Sent,
    Delivered,
    Read
}

internal fun deliveryFeedback(message: ChatMessage): DeliveryFeedback = when {
    message.seenBy.isNotEmpty() -> DeliveryFeedback.Read
    message.deliveredTo.isNotEmpty() -> DeliveryFeedback.Delivered
    else -> DeliveryFeedback.Sent
}

@Composable
internal fun deliveryLabel(message: ChatMessage): String = stringResource(
    when (deliveryFeedback(message)) {
        DeliveryFeedback.Sent -> R.string.private_chat_status_sent
        DeliveryFeedback.Delivered -> R.string.private_chat_status_delivered
        DeliveryFeedback.Read -> R.string.private_chat_status_read
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
