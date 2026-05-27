package com.example.testresqmesh.feature.comms.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.theme.InboxAccentBlue
import com.example.testresqmesh.core.ui.theme.InboxBackground
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.utils.MediaHelper

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
    mediaHelper: MediaHelper
) {
    val context = LocalContext.current
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bitmap = android.graphics.BitmapFactory.decodeStream(context.contentResolver.openInputStream(it))
            onImageSelected(mediaHelper.compressBitmapToBase64(bitmap))
        }
    }

    Column(
        modifier = Modifier
            .background(InboxBackground)
            .padding(horizontal = Spacing.Medium, vertical = 16.dp)
    ) {
        // Audio Preview Area
        if (pendingAudio != null) {
            Surface(
                onClick = { mediaHelper.playVoiceMail(pendingAudio) },
                color = InboxAccentBlue.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.Small)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("▶️", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Review Voice Note", 
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = onClearAudio,
                        modifier = Modifier
                            .size(24.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape)
                    ) {
                        Text("✕", color = MaterialTheme.colorScheme.onError, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Image Attachment Preview Area
        if (pendingImage != null) {
            val bitmap = remember(pendingImage) { mediaHelper.decodeBase64ToBitmap(pendingImage) }
            Box(modifier = Modifier.padding(bottom = Spacing.Small)) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Preview",
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    )
                }
                
                // Clear button
                IconButton(
                    onClick = onClearImage,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-8).dp)
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape)
                ) {
                    Text("✕", color = MaterialTheme.colorScheme.onError, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Utility Buttons
            UtilityButton(Icons.Default.LocationOn, onClick = onSendLocation)
            UtilityButton(Icons.Default.CameraAlt, onClick = { imagePickerLauncher.launch("image/*") })
            UtilityButton(if (isRecording) Icons.Default.Stop else Icons.Outlined.Mic, onClick = onToggleRecord)
            
            // Message Input
            Surface(
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                    BasicTextField(
                        value = if (isRecording) "Recording voice note..." else inputText,
                        onValueChange = onTextChange,
                        modifier = Modifier.weight(1f),
                        enabled = !isRecording,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        decorationBox = { innerTextField ->
                            if (inputText.isEmpty() && !isRecording) {
                                Text("Secure Mesh Message...", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodyMedium)
                            }
                            innerTextField()
                        }
                    )
                    
                    val canSend = (inputText.isNotBlank() || pendingImage != null || pendingAudio != null) && !isRecording
                    IconButton(onClick = {
                        if (canSend) {
                            onSend()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = if (canSend) InboxAccentBlue else Color.White.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickReplyChip("Safe") { onTextChange(it) }
            QuickReplyChip("SOS Needed") { onTextChange(it) }
            QuickReplyChip("Received") { onTextChange(it) }
        }
    }
}

@Composable
fun UtilityButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit = {}) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(36.dp), 
        shape = RoundedCornerShape(8.dp), 
        color = Color.White.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun QuickReplyChip(text: String, onClick: (String) -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick(text) },
        color = Color.Black.copy(alpha = 0.5f),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}
