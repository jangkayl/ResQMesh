package com.example.testresqmesh.feature.comms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.components.GlassSurface
import androidx.compose.ui.text.font.FontFamily
import com.example.testresqmesh.core.utils.MediaHelper
import androidx.compose.ui.graphics.Color
import com.example.testresqmesh.feature.comms.ui.components.ChatBubble
import com.example.testresqmesh.feature.comms.ui.components.ChatInput
import com.example.testresqmesh.feature.comms.ui.components.EndOfMeshIndicator
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel

@Composable
fun PublicChatTab(
    viewModel: CommunicationViewModel,
    mediaHelper: MediaHelper,
    onChatSelected: (String) -> Unit // Keeping this in case we want to tap avatars later
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    var inputText by remember { mutableStateOf("") }
    var pendingImage by remember { mutableStateOf<String?>(null) }
    var pendingAudio by remember { mutableStateOf<String?>(null) }
    var isRecording by remember { mutableStateOf(false) }

    // Sort messages by timestamp descending (newest first). 
    // reverseLayout = true will anchor newest to the bottom.
    val sortedMessages = remember(uiState.publicMessages) { uiState.publicMessages.sortedByDescending { it.timestamp } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Dynamic Sticky Header
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )
        
        GlassSurface(
            intensity = 0.5f,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Medium, vertical = Spacing.Small)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pulsing Live Indicator
                Box(contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(16.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha), CircleShape))
                    Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        "PUBLIC FREQUENCY", 
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Broadcasted to all available mesh nodes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        // Chat History
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(Spacing.Medium)
                .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .padding(Spacing.Medium),
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(sortedMessages) { msg ->
                // Fire a SEEN receipt over the mesh when this message is rendered on screen!
                if (!msg.isMine && !msg.seenBy.contains("Me")) {
                    LaunchedEffect(msg.id) {
                        viewModel.markMessageAsSeen(msg.id, isPrivate = false)
                    }
                }
                
                ChatBubble(message = msg, mediaHelper = mediaHelper)
            }

            item {
                EndOfMeshIndicator()
            }
        }

        // Chat Input Box
        ChatInput(
            inputText = inputText,
            onTextChange = { inputText = it },
            pendingImage = pendingImage,
            onImageSelected = { pendingImage = it },
            onClearImage = { pendingImage = null },
            pendingAudio = pendingAudio,
            onClearAudio = { pendingAudio = null },
            isRecording = isRecording,
            onToggleRecord = {
                if (!isRecording) {
                    val started = mediaHelper.startRecording()
                    if (started) isRecording = true
                } else {
                    isRecording = false
                    val audioBase64 = mediaHelper.stopRecording()
                    if (audioBase64 != null) {
                        pendingAudio = audioBase64 // Trap it in the preview state!
                    }
                }
            },
            onSend = {
                val hasAudio = pendingAudio != null
                val finalMessage = if (hasAudio && inputText.isBlank()) "🎤 Voice Note" else inputText.trim()
                
                viewModel.sendPublicMessage(
                    text = finalMessage,
                    imageBase64 = pendingImage,
                    audioBase64 = pendingAudio
                )
                // Clear input after sending
                inputText = ""
                pendingImage = null
                pendingAudio = null
                focusManager.clearFocus()
            },
            onSendLocation = {
                viewModel.broadcastLocation(context = context, isPrivate = false)
            },
            mediaHelper = mediaHelper
        )
    }
}

