package com.example.testresqmesh.feature.comms.ui.components

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
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.R
import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.utils.MediaHelper
import com.example.testresqmesh.feature.comms.ui.deliveryLabel
import com.example.testresqmesh.feature.comms.ui.messageTime
import kotlin.math.absoluteValue

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.ui.text.font.FontFamily

@Composable
fun ChatBubble(
    message: ChatMessage,
    mediaHelper: MediaHelper,
    showAvatar: Boolean = true,
    showSenderName: Boolean = true,
    onUserClick: ((String) -> Unit)? = null,
    onShowSeenBy: ((List<String>) -> Unit)? = null,
    onReplyClick: ((ChatMessage) -> Unit)? = null,
    onViewMap: ((Double, Double, String, String) -> Unit)? = null
) {
    val mine = message.isMine
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
        RoundedCornerShape(10.dp, 10.dp, 3.dp, 10.dp)
    } else {
        RoundedCornerShape(10.dp, 10.dp, 10.dp, 3.dp)
    }
    val sender = remember(message.senderName) { NodeIdentity.displayNameOf(message.senderName).ifBlank { message.senderName } }
    var showUserMenu by remember { mutableStateOf(false) }

    var fullScreenImage by remember { mutableStateOf<String?>(null) }
    if (fullScreenImage != null) {
        FullscreenImageViewer(
            imageBase64 = fullScreenImage!!,
            mediaHelper = mediaHelper,
            onDismiss = { fullScreenImage = null }
        )
    }

    val distinctReaders = remember(message.seenBy) { message.seenBy.distinct() }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        // Left Profile Avatar for other users
        if (!mine) {
            if (showAvatar) {
                Box {
                    UserAvatar(
                        name = message.senderName,
                        size = 32.dp,
                        onClick = { showUserMenu = true },
                        modifier = Modifier.padding(bottom = 4.dp, end = 8.dp)
                    )
                    DropdownMenu(
                        expanded = showUserMenu,
                        onDismissRequest = { showUserMenu = false }
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
                                showUserMenu = false
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
                                    showUserMenu = false
                                    onReplyClick.invoke(message)
                                }
                            )
                        }
                    }
                }
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
                        .clickable { showUserMenu = true },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                modifier = Modifier.widthIn(max = if (message.locationLat != null || message.imageBase64 != null) 300.dp else 260.dp),
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
                        val bitmap = remember(image) { mediaHelper.decodeBase64ToBitmap(image) }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.community_image_description),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { fullScreenImage = image },
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                    message.audioBase64?.let { audio ->
                        ModernVoicePlayer(
                            audioBase64 = audio,
                            mediaHelper = mediaHelper,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                        Spacer(Modifier.height(2.dp))
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

                    // Compact Metadata Row: Timestamp, Delivery Status, and Seen Receipts
                    if (mine) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = deliveryLabel(message),
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(alpha = 0.72f)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = messageTime(message.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(alpha = 0.72f)
                            )
                            if (distinctReaders.isNotEmpty()) {
                                Spacer(Modifier.width(6.dp))
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onShowSeenBy?.invoke(distinctReaders) },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    distinctReaders.take(3).forEachIndexed { index, reader ->
                                        if (index > 0) Spacer(Modifier.width(1.dp))
                                        Surface(
                                            modifier = Modifier.size(14.dp),
                                            shape = CircleShape,
                                            color = communityReaderColor(reader)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = reader.take(1).uppercase(),
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
                                    val additionalReaders = distinctReaders.size - 3
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
                        }
                    } else {
                        // Other user's message: Seen badges and timestamp aligned neatly
                        Row(
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (distinctReaders.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onShowSeenBy?.invoke(distinctReaders) },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    distinctReaders.take(3).forEachIndexed { index, reader ->
                                        if (index > 0) Spacer(Modifier.width(1.dp))
                                        Surface(
                                            modifier = Modifier.size(13.dp),
                                            shape = CircleShape,
                                            color = communityReaderColor(reader)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = reader.take(1).uppercase(),
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
                                    val additionalReaders = distinctReaders.size - 3
                                    if (additionalReaders > 0) {
                                        Text(
                                            text = "+$additionalReaders",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            color = contentColor.copy(alpha = 0.85f),
                                            modifier = Modifier.padding(start = 2.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                text = messageTime(message.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(alpha = 0.72f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun communityReaderColor(reader: String): Color =
    Color.hsv((reader.hashCode().absoluteValue % 360).toFloat(), 0.6f, 0.8f)
