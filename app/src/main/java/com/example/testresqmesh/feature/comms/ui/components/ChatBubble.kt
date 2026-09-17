package com.example.testresqmesh.feature.comms.ui.components

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.example.testresqmesh.R
import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.utils.MediaHelper
import com.example.testresqmesh.feature.comms.ui.deliveryLabel
import com.example.testresqmesh.feature.comms.ui.messageTime
import kotlin.math.absoluteValue

@Composable
fun ChatBubble(message: ChatMessage, mediaHelper: MediaHelper) {
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
        RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp)
    } else {
        RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp)
    }
    val sender = remember(message.senderName) { NodeIdentity.displayNameOf(message.senderName).ifBlank { message.senderName } }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start
    ) {
        if (!mine) {
            Text(
                text = sender,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.Small, bottom = 2.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Surface(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = shape,
            color = bubbleColor,
            contentColor = contentColor,
            shadowElevation = if (mine) 4.dp else 8.dp
        ) {
            Column(modifier = Modifier.padding(Spacing.Medium)) {
                if (isSos) {
                    Text(
                        text = stringResource(R.string.community_sos_label),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(Spacing.ExtraSmall))
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
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.padding(Spacing.Small),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.LocationOn, contentDescription = null)
                            Spacer(Modifier.width(Spacing.ExtraSmall))
                            Text(stringResource(R.string.community_location_shared))
                        }
                    }
                    Spacer(Modifier.height(Spacing.Small))
                }
                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSos) FontWeight.Bold else FontWeight.Normal
                    )
                }
                Spacer(Modifier.height(Spacing.ExtraSmall))
                androidx.compose.foundation.layout.Row(
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
        if (mine && message.seenBy.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = Spacing.ExtraSmall, end = Spacing.ExtraSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                message.seenBy.distinct().take(3).forEachIndexed { index, reader ->
                    if (index > 0) Spacer(Modifier.width(2.dp))
                    Surface(
                        modifier = Modifier.size(18.dp),
                        shape = CircleShape,
                        color = communityReaderColor(reader)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = reader.take(1).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
                val additionalReaders = message.seenBy.distinct().size - 3
                if (additionalReaders > 0) {
                    Text(
                        text = "+$additionalReaders",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.ExtraSmall)
                    )
                }
            }
        }
    }
}

private fun communityReaderColor(reader: String): Color =
    Color.hsv((reader.hashCode().absoluteValue % 360).toFloat(), 0.6f, 0.8f)
