package com.example.testresqmesh.feature.comms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.testresqmesh.core.ui.theme.*
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel
import com.example.testresqmesh.feature.comms.ui.components.ChatInput
import com.example.testresqmesh.core.utils.MediaHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveChatScreen(
    name: String, 
    viewModel: CommunicationViewModel,
    mediaHelper: MediaHelper,
    onBack: () -> Unit,
    onViewMap: (Double, Double, String, String) -> Unit = { _, _, _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()
    val messages = uiState.privateMessages[name] ?: emptyList()
    val sortedMessages = remember(messages) { messages.sortedByDescending { it.timestamp } }
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var pendingImage by remember { mutableStateOf<String?>(null) }
    var pendingAudio by remember { mutableStateOf<String?>(null) }
    var isRecording by remember { mutableStateOf(false) }

    val displayName = uiState.connectedDevices.find { it.endpointId == name }?.name ?: name
    val context = LocalContext.current

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(AppBackground)) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = DarkGray)
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(modifier = Modifier.size(36.dp), shape = CircleShape, color = PrimaryRed.copy(alpha = 0.1f)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(displayName.take(1).uppercase(), color = PrimaryRed, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = DarkGray)
                                Text("Mesh Secured", style = MaterialTheme.typography.labelSmall, color = SuccessGreen, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmWhite)
                )
                HorizontalDivider(color = LightGray)
            }
        },
        containerColor = AppBackground,
        bottomBar = {
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
                        if (mediaHelper.startRecording()) isRecording = true
                    } else {
                        isRecording = false
                        mediaHelper.stopRecording()?.let { pendingAudio = it }
                    }
                },
                onSend = {
                    viewModel.sendPrivateMessage(displayName, if (pendingAudio != null && inputText.isBlank()) "🎤 Voice Note" else inputText.trim(), pendingImage, pendingAudio)
                    inputText = ""; pendingImage = null; pendingAudio = null
                },
                onSendLocation = { 
                    viewModel.broadcastLocation(context, true, displayName) 
                },
                mediaHelper = mediaHelper
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
            reverseLayout = true
        ) {
            items(sortedMessages) { msg ->
                ModernChatBubble(msg, mediaHelper, onViewMap = { lat, lng -> onViewMap(lat, lng, msg.senderName, msg.text) })
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun ModernChatBubble(msg: com.example.testresqmesh.core.model.ChatMessage, mediaHelper: MediaHelper, onViewMap: (Double, Double) -> Unit) {
    val isMine = msg.isMine
    val alignment = if (isMine) Alignment.End else Alignment.Start
    val bubbleColor = if (isMine) PrimaryRed else WarmWhite
    val contentColor = if (isMine) WarmWhite else DarkGray
    
    val shape = if (isMine) {
        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 24.dp)
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = alignment) {
        Surface(
            color = bubbleColor,
            shape = shape,
            modifier = Modifier.widthIn(max = 300.dp).then(if (!isMine) Modifier.shadow(2.dp, shape) else Modifier),
            border = if (!isMine) androidx.compose.foundation.BorderStroke(1.dp, LightGray) else null
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (msg.locationLat != null && msg.locationLng != null) {
                    LocationBadge(msg.locationLat, msg.locationLng, contentColor, onOpen = { onViewMap(msg.locationLat, msg.locationLng) })
                }
                if (msg.imageBase64 != null) {
                    mediaHelper.decodeBase64ToBitmap(msg.imageBase64)?.let { 
                        androidx.compose.foundation.Image(it.asImageBitmap(), null, modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)).background(LightGray), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                if (msg.audioBase64 != null) {
                    VoiceMailBadge(msg.audioBase64, contentColor, isMine, onPlay = { mediaHelper.playVoiceMail(msg.audioBase64) })
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (msg.text.isNotBlank()) {
                    Text(msg.text, style = MaterialTheme.typography.bodyLarge, color = contentColor, lineHeight = 22.sp)
                }
            }
        }
        
        Row(modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isMine) "Sent via Mesh" else "Received via Mesh",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontSize = 10.sp
            )
            if (isMine && msg.seenBy.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Default.DoneAll, null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
fun LocationBadge(lat: Double, lng: Double, tint: Color, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        color = tint.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocationOn, null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text("Emergency Location", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = tint)
        }
    }
}

@Composable
fun VoiceMailBadge(audio: String, tint: Color, isMine: Boolean, onPlay: () -> Unit) {
    Surface(
        onClick = onPlay,
        color = if (isMine) WarmWhite.copy(alpha = 0.2f) else LightGray,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayArrow, null, tint = tint)
            Spacer(modifier = Modifier.width(12.dp))
            Text("Voice Note", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = tint)
        }
    }
}
