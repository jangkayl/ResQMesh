package com.example.testresqmesh.feature.comms.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Send
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
import com.example.testresqmesh.core.ui.components.inputs.ResQTextField
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.utils.MediaHelper

@Composable
fun ChatInput(
    inputText: String,
    onTextChange: (String) -> Unit,
    pendingImage: String?,
    onImageSelected: (String) -> Unit,
    onClearImage: () -> Unit,
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

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(Spacing.Medium)
                .navigationBarsPadding()
                .imePadding()
        ) {
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
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
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

            // Input Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Sleek Actions Bar
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onSendLocation, modifier = Modifier.size(40.dp)) {
                        Text("📍", fontSize = 18.sp)
                    }
                    IconButton(onClick = { imagePickerLauncher.launch("image/*") }, modifier = Modifier.size(40.dp)) {
                        Text("📷", fontSize = 18.sp)
                    }
                    IconButton(onClick = onToggleRecord, modifier = Modifier.size(40.dp)) {
                        Text(if (isRecording) "⏹️" else "🎤", fontSize = 18.sp)
                    }
                }

                Spacer(modifier = Modifier.width(Spacing.Small))

                // Text field
                ResQTextField(
                    value = if (isRecording) "Recording voice note..." else inputText,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = "Message...",
                    enabled = !isRecording
                )

                Spacer(modifier = Modifier.width(Spacing.Small))

                // Send Button
                val canSend = (inputText.isNotBlank() || pendingImage != null) && !isRecording
                FloatingActionButton(
                    onClick = { if (canSend) onSend() },
                    containerColor = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    elevation = FloatingActionButtonDefaults.elevation(if (canSend) 4.dp else 0.dp),
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
