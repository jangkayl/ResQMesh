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
import com.example.testresqmesh.core.ui.theme.SuccessGreen
import com.example.testresqmesh.core.utils.MediaHelper

@Composable
fun ChatBubble(message: ChatMessage, mediaHelper: MediaHelper) {
    val isMine = message.isMine
    val bubbleColor = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val alignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
    val shape = if (isMine) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 2.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 2.dp, bottomEnd = 16.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.ExtraSmall),
        contentAlignment = alignment
    ) {
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
                        text = message.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = contentColor.copy(alpha = 0.6f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                
                // Security indicator
                val securityText = if (message.isPrivate) "🔒 E2EE" else "🌐 PUBLIC MESH"
                val securityColor = if (message.isPrivate) contentColor.copy(alpha = 0.4f) else SuccessGreen
                Text(
                    text = securityText,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    color = securityColor,
                    fontWeight = if (!message.isPrivate) FontWeight.Bold else FontWeight.Normal
                )
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
                    color = contentColor.copy(alpha = 0.1f),
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
                            color = contentColor
                        )
                    }
                }
            } else {
                if (message.locationLat != null && message.locationLng != null) {
                    Surface(
                        color = contentColor.copy(alpha = 0.1f),
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
                    color = contentColor
                )
            }
            
            // Delivery Status (Mocked)
            Row(
                modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "10:42 AM", // Mocked timestamp
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    color = contentColor.copy(alpha = 0.4f)
                )
                if (isMine) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "✓✓", // Mocked delivered status
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = contentColor.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}
