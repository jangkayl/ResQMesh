package edu.cit.resqmesh.feature.comms.ui.components

import edu.cit.resqmesh.feature.comms.ui.ReplyQuote
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.cit.resqmesh.R
import edu.cit.resqmesh.core.model.ChatMessage
import edu.cit.resqmesh.core.model.NodeIdentity
import edu.cit.resqmesh.core.ui.theme.Spacing
import edu.cit.resqmesh.core.audio.MediaHelper
import edu.cit.resqmesh.feature.comms.ui.deliveryLabel
import edu.cit.resqmesh.core.network.SendProgress
import edu.cit.resqmesh.feature.comms.ui.messageTime
import kotlin.math.absoluteValue

import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.ui.text.font.FontFamily
import edu.cit.resqmesh.core.ui.components.identity.UserAvatar
import edu.cit.resqmesh.core.ui.components.location.TacticalLocationCard

/** Reader initials shown before the "+N" overflow count. */
private const val MAX_SEEN_BADGES = 3
private const val HUE_DEGREES = 360
private const val READER_SATURATION = 0.6f
private const val READER_VALUE = 0.8f

@Composable
fun ChatBubble(
    message: ChatMessage,
    mediaHelper: MediaHelper,
    showAvatar: Boolean = true,
    showSenderName: Boolean = true,
    onUserClick: ((String) -> Unit)? = null,
    onShowSeenBy: ((List<String>) -> Unit)? = null,
    onReplyClick: ((ChatMessage) -> Unit)? = null,
    onViewMap: ((Double, Double, String, String) -> Unit)? = null,
    transferProgress: SendProgress? = null
) {
    val mine = message.isMine
    val stage = transferStage(message, transferProgress)
    val isSos = message.isSOS
    val bubbleColor = when {
        isSos -> MaterialTheme.colorScheme.errorContainer
        mine -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        isSos -> MaterialTheme.colorScheme.onErrorContainer
        mine -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val shape = if (mine) {
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    }
    val sender = remember(message.senderName) {
        NodeIdentity.displayNameOf(message.senderName).ifBlank { message.senderName }
    }
    val showUserMenu = remember { mutableStateOf(false) }

    var fullScreenImage by remember { mutableStateOf<String?>(null) }
    if (fullScreenImage != null) {
        FullscreenImageViewer(
            imageBase64 = fullScreenImage!!,
            mediaHelper = mediaHelper,
            onDismiss = { fullScreenImage = null }
        )
    }

    val distinctReaders = remember(message.seenBy) { message.seenBy.distinct() }
    val onSeenByClick: () -> Unit = { onShowSeenBy?.invoke(distinctReaders) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        // Left Profile Avatar for other users
        if (!mine) {
            if (showAvatar) {
                SenderAvatarMenu(message, sender, showUserMenu, onUserClick, onReplyClick)
            } else {
                Spacer(modifier = Modifier.width(40.dp))
            }
        }

        Column(
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start
        ) {
            if (!mine && showSenderName) {
                Text(
                    text = sender,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = Spacing.Small, bottom = 2.dp)
                        .clickable { showUserMenu.value = true },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val hasWideContent = message.locationLat != null || message.imageBase64 != null
            Surface(
                modifier = Modifier.widthIn(max = if (hasWideContent) 300.dp else 260.dp),
                shape = shape,
                color = bubbleColor,
                contentColor = contentColor,
                shadowElevation = if (mine) 3.dp else 4.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
                    if (isSos) {
                        Text(
                            text = stringResource(R.string.community_sos_label),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    message.imageBase64?.let { image ->
                        BubbleImage(image, mediaHelper, onOpen = { fullScreenImage = image })
                    }
                    message.audioBase64?.let { audio ->
                        ModernVoicePlayer(
                            audioBase64 = audio,
                            mediaHelper = mediaHelper,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    syncMediaPlaceholder(message)?.let { placeholder ->
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.labelMedium,
                            color = contentColor.copy(alpha = 0.72f)
                        )
                        Spacer(Modifier.height(2.dp))
                    }

                    val (replyQuote, actualText) = remember(message.text) { ReplyQuote.parse(message.text) }

                    if (replyQuote != null) {
                        ReplyQuoteStrip(replyQuote, mine, contentColor)
                    }

                    val isDefaultLocation = message.locationLat != null &&
                        (actualText.isBlank() || actualText.contains("I am sharing my location"))

                    if (message.locationLat != null && message.locationLng != null) {
                        TacticalLocationCard(
                            latitude = message.locationLat,
                            longitude = message.locationLng,
                            senderName = message.senderName,
                            noteText = if (!isDefaultLocation) actualText else null,
                            isMine = mine,
                            onTrackOnMap = {
                                onViewMap?.invoke(
                                    message.locationLat,
                                    message.locationLng,
                                    message.senderName,
                                    message.text
                                )
                            }
                        )
                        Spacer(Modifier.height(4.dp))
                    }

                    if (actualText.isNotBlank() && !isDefaultLocation) {
                        Text(
                            text = actualText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSos) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    syncedTimeLabel(message, System.currentTimeMillis())?.let { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.72f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    if (stage != null && transferProgress != null) {
                        TransferProgressLine(stage, transferProgress, contentColor)
                    }
                    // Compact Metadata Row: Timestamp, Delivery Status, and Seen Receipts
                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (mine) {
                            if (stage == null) {
                                MetadataText(deliveryLabel(message), contentColor)
                                Spacer(Modifier.width(4.dp))
                            }
                            MetadataText(messageTime(message.timestamp), contentColor)
                            if (distinctReaders.isNotEmpty()) {
                                Spacer(Modifier.width(6.dp))
                                SeenByBadges(distinctReaders, MINE_BADGE, contentColor, onSeenByClick)
                            }
                        } else {
                            // Other user's message: Seen badges and timestamp aligned neatly
                            if (distinctReaders.isNotEmpty()) {
                                SeenByBadges(distinctReaders, OTHER_BADGE, contentColor, onSeenByClick)
                                Spacer(Modifier.width(4.dp))
                            }
                            MetadataText(messageTime(message.timestamp), contentColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SenderAvatarMenu(
    message: ChatMessage,
    sender: String,
    showUserMenu: MutableState<Boolean>,
    onUserClick: ((String) -> Unit)?,
    onReplyClick: ((ChatMessage) -> Unit)?
) {
    Box {
        UserAvatar(
            name = message.senderName,
            size = 32.dp,
            onClick = { showUserMenu.value = true },
            modifier = Modifier.padding(bottom = 4.dp, end = 8.dp)
        )
        DropdownMenu(
            expanded = showUserMenu.value,
            onDismissRequest = { showUserMenu.value = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.community_message_user, sender)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = null
                    )
                },
                onClick = {
                    showUserMenu.value = false
                    onUserClick?.invoke(message.senderName)
                }
            )
            if (onReplyClick != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_reply_action)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Reply,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        showUserMenu.value = false
                        onReplyClick.invoke(message)
                    }
                )
            }
        }
    }
}

@Composable
private fun BubbleImage(image: String, mediaHelper: MediaHelper, onOpen: () -> Unit) {
    val bitmap = remember(image) { mediaHelper.decodeBase64ToBitmap(image) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.community_image_description),
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onOpen() },
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ReplyQuoteStrip(replyQuote: String, mine: Boolean, contentColor: Color) {
    val accent = if (mine) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.primary
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
                    .background(accent)
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Reply,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = accent
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

@Composable
private fun MetadataText(text: String, contentColor: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = contentColor.copy(alpha = 0.72f)
    )
}

/** Badge geometry differs slightly between own and received bubbles. */
private class SeenBadgeStyle(val badgeSize: Dp, val cornerRadius: Dp)

private val MINE_BADGE = SeenBadgeStyle(badgeSize = 14.dp, cornerRadius = 8.dp)
private val OTHER_BADGE = SeenBadgeStyle(badgeSize = 13.dp, cornerRadius = 6.dp)

@Composable
private fun SeenByBadges(
    readers: List<String>,
    style: SeenBadgeStyle,
    contentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(style.cornerRadius))
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        readers.take(MAX_SEEN_BADGES).forEachIndexed { index, reader ->
            if (index > 0) Spacer(Modifier.width(1.dp))
            Surface(
                modifier = Modifier.size(style.badgeSize),
                shape = CircleShape,
                color = communityReaderColor(reader)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = edu.cit.resqmesh.core.security.input.TextPolicy
                            .firstGrapheme(reader).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            lineHeight = 8.sp
                        ),
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
        val additionalReaders = readers.size - MAX_SEEN_BADGES
        if (additionalReaders > 0) {
            Text(
                text = "+$additionalReaders",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = contentColor.copy(alpha = 0.85f),
                modifier = Modifier.padding(start = 2.dp)
            )
        }
    }
}

private fun communityReaderColor(reader: String): Color =
    Color.hsv((reader.hashCode().absoluteValue % HUE_DEGREES).toFloat(), READER_SATURATION, READER_VALUE)
