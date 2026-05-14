package com.example.testresqmesh.ui.components.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.ui.components.buttons.ResQButton
import com.example.testresqmesh.ui.components.inputs.ResQTextField
import com.example.testresqmesh.ui.theme.Spacing
import com.example.testresqmesh.utils.MediaHelper

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
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(Spacing.Small)
                .navigationBarsPadding()
                .imePadding()
        ) {
            if (pendingImage != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = Spacing.Small)
                ) {
                    Text(
                        "📷 Image Attached",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onClearImage) {
                        Text("Clear", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
                IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                    Text("📷")
                }
                
                IconButton(onClick = onToggleRecord) {
                    Text(if (isRecording) "⏹️" else "🎤")
                }

                ResQTextField(
                    value = if (isRecording) "Recording..." else inputText,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = "Type a message...",
                    enabled = !isRecording
                )

                IconButton(
                    onClick = onSend,
                    enabled = (inputText.isNotBlank() || pendingImage != null) && !isRecording,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
    }
}
