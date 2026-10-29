package edu.cit.resqmesh.feature.comms.ui.components

import edu.cit.resqmesh.core.ui.components.inputs.InputCounter
import edu.cit.resqmesh.core.security.input.TextField
import edu.cit.resqmesh.core.security.input.TextPolicy
import edu.cit.resqmesh.feature.comms.ui.components.composer.LocationAcquisitionBanner
import edu.cit.resqmesh.feature.comms.ui.components.composer.ChatBudgetLine
import edu.cit.resqmesh.feature.comms.ui.components.composer.chatBudgetWaitDescription
import edu.cit.resqmesh.feature.comms.ui.components.composer.waitSeconds
import edu.cit.resqmesh.core.network.ratelimit.ChatBudgetState
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import edu.cit.resqmesh.feature.comms.ui.components.composer.ComposerAttachmentPreview
import edu.cit.resqmesh.feature.comms.ui.components.composer.ComposerActionPopup
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.cit.resqmesh.R
import edu.cit.resqmesh.core.model.ChatMessage
import edu.cit.resqmesh.core.model.NodeIdentity
import edu.cit.resqmesh.core.ui.components.layout.ResQGlassSurface
import edu.cit.resqmesh.core.ui.theme.ResQTheme
import edu.cit.resqmesh.core.audio.MediaHelper

/**
 * Modern Context-Aware Chat Composer with Floating Action Bubble.
 *
 * Interaction Behavior:
 * - When idle/empty: Image, Location, and Voice buttons are displayed directly inline.
 * - When typing: Inline buttons smoothly slide left and morph into a compact '+' toggle,
 *   granting maximum horizontal typing area.
 * - While typing, clicking '+': A floating glassmorphic bubble pops up directly above the '+' button
 *   with Photo, Location, and Voice actions.
 * - Auto-expanding multi-line text input that morphs its corner radius dynamically.
 */
@Suppress("FunctionNaming", "LongParameterList", "LongMethod", "CyclomaticComplexMethod")
@Composable
fun ChatInput(
    inputText: String,
    onTextChange: (String) -> Unit,
    pendingImage: String?,
    onImageSelected: (String) -> Unit,
    onClearImage: () -> Unit,
    pendingAudio: String?,
    onClearAudio: () -> Unit,
    isRecording: Boolean,
    onToggleRecord: () -> Unit,
    onSend: () -> Unit,
    onSendLocation: () -> Unit,
    mediaHelper: MediaHelper,
    isAcquiringLocation: Boolean = false,
    replyingTo: ChatMessage? = null,
    onCancelReply: () -> Unit = {},
    maxInputLength: Int = TextField.MESSAGE.maxLength,
    sendBudget: ChatBudgetState? = null
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bitmap = android.graphics.BitmapFactory.decodeStream(context.contentResolver.openInputStream(it))
            if (bitmap != null) onImageSelected(mediaHelper.compressBitmapToBase64(bitmap))
        }
    }

    val isTyping = inputText.isNotEmpty()
    var isPopupExpanded by remember { mutableStateOf(false) }

    // Auto-dismiss popup if text is cleared back to empty
    LaunchedEffect(isTyping) {
        if (!isTyping) {
            isPopupExpanded = false
        }
    }

    val budgetWaiting = sendBudget != null && sendBudget.remaining <= 0
    val hasContent = inputText.isNotBlank() || pendingImage != null || pendingAudio != null
    val canSend = hasContent && !isRecording && !budgetWaiting

    // Dynamic morphing calculation based on content length and line count
    val lineCount = remember(inputText) { inputText.count { it == '\n' } + 1 }
    val isMultiLine = lineCount > 1 || inputText.length > 38

    val animatedCornerRadius by animateDpAsState(
        targetValue = if (isMultiLine) 16.dp else 26.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "cornerMorph"
    )

    val plusRotation by animateFloatAsState(
        targetValue = if (isPopupExpanded) 45f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "plusRotate"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // 1. Inline Docked Threaded Reply Banner
        AnimatedVisibility(
            visible = replyingTo != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            if (replyingTo != null) {
                val replySender = remember(replyingTo.senderName) {
                    NodeIdentity.displayNameOf(replyingTo.senderName).ifBlank { replyingTo.senderName }
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Glowing Connecting Bar
                        Box(
                            modifier = Modifier
                                .width(3.5.dp)
                                .height(28.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )

                        Spacer(Modifier.width(8.dp))

                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Reply,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )

                        Spacer(Modifier.width(6.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.chat_replying_to, replySender),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val replyPreview = when {
                                replyingTo.locationLat != null && replyingTo.locationLng != null -> {
                                    "📍 Shared Location (%.4f, %.4f)".format(java.util.Locale.US, replyingTo.locationLat, replyingTo.locationLng)
                                }
                                replyingTo.imageBase64 != null -> stringResource(R.string.private_chat_photo)
                                replyingTo.audioBase64 != null -> stringResource(R.string.private_chat_voice_note)
                                replyingTo.text.isNotBlank() -> replyingTo.text
                                else -> stringResource(R.string.messages_attachment_preview)
                            }
                            Text(
                                text = replyPreview,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = onCancelReply,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        LocationAcquisitionBanner(isAcquiringLocation)

        ComposerAttachmentPreview(pendingImage, pendingAudio, onClearImage, onClearAudio)

        ComposerActionPopup(isTyping, isPopupExpanded, isAcquiringLocation,
            onPickImage = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                imagePicker.launch("image/*")
                                isPopupExpanded = false
                            },
            onPickLocation = {
                                if (!isAcquiringLocation) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSendLocation()
                                    isPopupExpanded = false
                                }
                            },
            onRecord = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleRecord()
                                isPopupExpanded = false
                            })

        if (sendBudget != null) ChatBudgetLine(sendBudget)

        // 4. Floating Frosted-Glass Composer Row
        ResQGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
            shadowElevation = 16.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                // When NOT typing: show Photo, Location, and Voice inline by default
                AnimatedVisibility(
                    visible = !isTyping,
                    enter = fadeIn(tween(180)) + expandHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMedium)),
                    exit = fadeOut(tween(140)) + shrinkHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMedium))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 2.dp)
                    ) {
                        IconButton(
                            onClick = { imagePicker.launch("image/*") },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Image,
                                contentDescription = stringResource(R.string.private_chat_add_image_action),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onSendLocation,
                            enabled = !isAcquiringLocation,
                            modifier = Modifier.size(38.dp)
                        ) {
                            if (isAcquiringLocation) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = ResQTheme.colors.success
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.LocationOn,
                                    contentDescription = stringResource(R.string.private_chat_share_location_action),
                                    tint = ResQTheme.colors.success,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = onToggleRecord,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Mic,
                                contentDescription = stringResource(R.string.private_chat_record_action),
                                tint = ResQTheme.colors.sos,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // When TYPING: show '+' toggle button to trigger the floating action bubble above
                AnimatedVisibility(
                    visible = isTyping,
                    enter = fadeIn(tween(180)) + expandHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMedium)),
                    exit = fadeOut(tween(140)) + shrinkHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMedium))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 2.dp, end = 2.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                            shape = CircleShape,
                            color = if (isPopupExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            border = BorderStroke(
                                1.dp,
                                if (isPopupExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            ),
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isPopupExpanded = !isPopupExpanded
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Toggle media attachments",
                                    tint = if (isPopupExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .rotate(plusRotation)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.width(2.dp))

                // Auto-Expanding Multi-Line Text Input Area (or Active Recording Mode)
                if (isRecording) {
                    // Active Voice Recording Indicator Bar
                    val infiniteTransition = rememberInfiniteTransition(label = "recordingPulse")
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulseAlpha"
                    )

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .padding(horizontal = 4.dp),
                        shape = RoundedCornerShape(22.dp),
                        color = ResQTheme.colors.sos.copy(alpha = 0.12f),
                        border = BorderStroke(1.5.dp, ResQTheme.colors.sos.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(ResQTheme.colors.sos.copy(alpha = pulseAlpha))
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Recording voice note...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ResQTheme.colors.sos
                                )
                            }

                            IconButton(
                                onClick = onToggleRecord,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Stop,
                                    contentDescription = stringResource(R.string.private_chat_stop_recording_action),
                                    tint = ResQTheme.colors.sos,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Expanding Text Field
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp, max = 150.dp)
                            .padding(horizontal = 4.dp),
                        shape = RoundedCornerShape(animatedCornerRadius),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(
                            width = if (inputText.isNotBlank()) 1.5.dp else 1.dp,
                            color = if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                            BasicTextField(
                                value = inputText,
                                onValueChange = { onTextChange(TextPolicy.clampLength(it, maxInputLength)) },
                                enabled = true,
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 1,
                                maxLines = 6,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    if (inputText.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.community_message_placeholder),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            InputCounter(inputText.length, TextField.MESSAGE)
                            }
                        }
                    }
                }

                Spacer(Modifier.width(4.dp))

                // High-Impact Glowing Send Button
                Surface(
                    modifier = Modifier
                        .then(if (budgetWaiting) Modifier.height(44.dp).widthIn(min = 44.dp) else Modifier.size(44.dp))
                        .padding(bottom = 2.dp)
                        .then(
                            if (budgetWaiting && sendBudget != null) {
                                val waitDescription = chatBudgetWaitDescription(sendBudget)
                                Modifier.semantics { contentDescription = waitDescription }
                            } else Modifier
                        )
                        .shadow(
                            elevation = if (canSend) 10.dp else 0.dp,
                            shape = CircleShape,
                            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        ),
                    shape = if (budgetWaiting) RoundedCornerShape(22.dp) else CircleShape,
                    color = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    contentColor = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    onClick = {
                        if (canSend) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSend()
                        }
                    },
                    enabled = canSend
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (budgetWaiting && sendBudget != null) {
                            Text(
                                text = stringResource(R.string.chat_budget_wait, sendBudget.waitSeconds()),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp).clearAndSetSemantics { }
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Send,
                                contentDescription = stringResource(R.string.private_chat_send_action),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
