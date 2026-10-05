package com.example.testresqmesh.feature.comms.ui.privatechat

import com.example.testresqmesh.feature.comms.ui.DeliveryFeedback
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.Image
import com.example.testresqmesh.core.ui.components.location.TacticalLocationCard
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.R
import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.utils.MediaHelper
import com.example.testresqmesh.feature.comms.ui.components.FullscreenImageViewer
import com.example.testresqmesh.feature.comms.ui.components.ModernVoicePlayer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun PrivateMessageBubble(
    message: ChatMessage,
    mediaHelper: MediaHelper,
    onViewMap: (Double, Double) -> Unit,
    onReplyClick: ((ChatMessage) -> Unit)? = null
) {
    val mine = message.isMine
    val bubbleColor = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val contentColor = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val shape = if (mine) {
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
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
