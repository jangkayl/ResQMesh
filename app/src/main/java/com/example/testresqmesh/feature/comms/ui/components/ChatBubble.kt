package com.example.testresqmesh.feature.comms.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.utils.MediaHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import kotlin.math.absoluteValue

@Composable
fun ChatBubble(message: ChatMessage, mediaHelper: MediaHelper) {
    val isMine = message.isMine
    val isSOS = message.text.contains("SOS", ignoreCase = true) || message.text.contains("HELP", ignoreCase = true)

    // Base Colors
    val bubbleColor = when {
        isSOS -> MaterialTheme.colorScheme.error
        isMine -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        isSOS -> Color.White
        isMine -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    val alignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
    val shape = if (isMine) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 2.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 2.dp, bottomEnd = 16.dp)
    }

    val timeString = remember(message.timestamp) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.ExtraSmall),
        contentAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
        ) {
            // Avatar for peers
            if (!isMine) {
                val avatarColor = remember(message.senderName) { generateAvatarColor(message.senderName) }
                val initial = message.senderName.take(1).uppercase()
                
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(avatarColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .background(bubbleColor, shape)
                    .padding(Spacing.Small)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!isMine) {
                        Text(
                            text = if (isSOS) "🚨 ${message.senderName}" else message.senderName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = contentColor.copy(alpha = 0.8f)
                        )
                    } else {
                        if (isSOS) {
                            Text("🚨 SOS ALERT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.White)
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    
                    // Security and Medium indicators
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val mediumColor = if (message.receiveMedium.contains("Wi-Fi")) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                        Text(
                            text = if (isMine) "" else "📶 ${message.receiveMedium}",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            color = mediumColor.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold
                        )

                        val securityText = if (message.isPrivate) "🔒 E2EE" else "🌐 PUBLIC"
                        val securityColor = if (message.isPrivate) contentColor.copy(alpha = 0.6f) else if (isSOS) Color.White else Color(0xFF10B981)
                        Text(
                            text = securityText,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            color = securityColor,
                            fontWeight = if (!message.isPrivate) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(2.dp))
                
                if (message.imageBase64 != null) {
                    val bitmap = remember(message.imageBase64) { mediaHelper.decodeBase64ToBitmap(message.imageBase64) }
                    bitmap?.let { 
                        Image(
                            bitmap = it.asImageBitmap(), 
                            contentDescription = "Attached Image", 
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .padding(bottom = Spacing.ExtraSmall)
                        ) 
                    }
                }
                
                if (message.audioBase64 != null) {
                    Surface(
                        onClick = { mediaHelper.playVoiceMail(message.audioBase64) },
                        color = contentColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.Small),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("▶️", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(Spacing.Small))
                            Text(
                                "Voice Note", 
                                style = MaterialTheme.typography.bodyMedium,
                                color = contentColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    if (message.locationLat != null && message.locationLng != null) {
                        Surface(
                            color = contentColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.ExtraSmall)
                        ) {
                            Row(
                                modifier = Modifier.padding(Spacing.Small),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📍", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(Spacing.Small))
                                Text(
                                    "Shared Location:\n[${String.format(java.util.Locale.US, "%.4f", message.locationLat)}, ${String.format(java.util.Locale.US, "%.4f", message.locationLng)}]", 
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = contentColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                        fontWeight = if (isSOS) FontWeight.Bold else FontWeight.Normal
                    )
                }
                
                // Real Timestamp & Seen By
                Row(
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isMine && message.seenBy.isNotEmpty()) {
                        Row(modifier = Modifier.padding(end = 4.dp)) {
                            message.seenBy.take(3).forEach { reader ->
                                val color = remember(reader) { generateAvatarColor(reader) }
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .offset(x = 4.dp) // Slight overlap
                                        .background(color, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(reader.take(1).uppercase(), color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (message.seenBy.size > 3) {
                                Text("+${message.seenBy.size - 3}", fontSize = 8.sp, color = contentColor.copy(alpha = 0.6f), modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }

                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = contentColor.copy(alpha = 0.6f)
                    )
                    if (isMine) {
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        val statusText = when {
                            message.seenBy.isNotEmpty() -> "👀"
                            message.deliveredTo.isNotEmpty() -> "✓✓"
                            else -> "✓"
                        }
                        
                        val statusColor = when {
                            message.seenBy.isNotEmpty() -> Color(0xFF10B981)
                            else -> contentColor.copy(alpha = 0.6f)
                        }
                        
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            color = statusColor
                        )
                    }
                }
            }
        }
    }
}

private fun generateAvatarColor(name: String): Color {
    val hash = name.hashCode().absoluteValue
    val hue = (hash % 360).toFloat()
    return Color.hsv(hue, 0.6f, 0.8f)
}
