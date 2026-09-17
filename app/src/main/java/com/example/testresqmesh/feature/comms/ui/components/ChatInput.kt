package com.example.testresqmesh.feature.comms.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.R
import com.example.testresqmesh.core.ui.components.layout.ResQGlassSurface
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
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bitmap = android.graphics.BitmapFactory.decodeStream(context.contentResolver.openInputStream(it))
            if (bitmap != null) onImageSelected(mediaHelper.compressBitmapToBase64(bitmap))
        }
    }
    val canSend = (inputText.isNotBlank() || pendingImage != null || pendingAudio != null) && !isRecording

    ResQGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = Spacing.Small, vertical = Spacing.Small),
        shape = RoundedCornerShape(28.dp),
        contentPadding = PaddingValues(Spacing.Small),
        shadowElevation = 18.dp
    ) {
        Column {
            if (pendingImage != null || pendingAudio != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (pendingImage != null) stringResource(R.string.private_chat_photo) else stringResource(R.string.private_chat_voice_note),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        if (pendingImage != null) onClearImage() else onClearAudio()
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.private_chat_remove_attachment)
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { imagePicker.launch("image/*") }) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = stringResource(R.string.private_chat_add_image_action),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onSendLocation) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = stringResource(R.string.private_chat_share_location_action),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onToggleRecord) {
                    Icon(
                        imageVector = if (isRecording) Icons.Outlined.Stop else Icons.Outlined.Mic,
                        contentDescription = stringResource(
                            if (isRecording) R.string.private_chat_stop_recording_action else R.string.private_chat_record_action
                        ),
                        tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    BasicTextField(
                        value = inputText,
                        onValueChange = onTextChange,
                        enabled = !isRecording,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = Spacing.Medium, vertical = 13.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        decorationBox = { input ->
                            if (inputText.isEmpty() && !isRecording) {
                                Text(
                                    text = stringResource(R.string.community_message_placeholder),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            input()
                        }
                    )
                }
                IconButton(onClick = onSend, enabled = canSend) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = stringResource(R.string.private_chat_send_action),
                        tint = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
