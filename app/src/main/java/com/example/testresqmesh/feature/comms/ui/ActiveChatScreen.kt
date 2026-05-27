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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import com.example.testresqmesh.core.ui.theme.AppBackground
import com.example.testresqmesh.core.ui.theme.CyanPrimary
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel
import com.example.testresqmesh.feature.comms.ui.components.ChatInput
import com.example.testresqmesh.core.utils.MediaHelper


data class ChatMessageData(
    val id: String,
    val text: String,
    val imageBase64: String? = null,
    val audioBase64: String? = null,
    val time: String,
    val hops: String,
    val receiveMedium: String,
    val deliveredTo: List<String>,
    val seenBy: List<String>,
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    val isMine: Boolean,
    val isSent: Boolean = false,
    val outboundRoute: List<String> = emptyList(),
    val returnRoute: List<String> = emptyList()
)

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
    
    // Messages for this specific target (name is treated as endpointId here)
    val messages = uiState.privateMessages[name] ?: emptyList()
    
    // Sort messages newest first for reverseLayout
    val sortedMessages = remember(messages) { messages.sortedByDescending { it.timestamp } }
    
    // LazyListState to control scrolling if needed
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var pendingImage by remember { mutableStateOf<String?>(null) }
    var pendingAudio by remember { mutableStateOf<String?>(null) }
    var isRecording by remember { mutableStateOf(false) }

    // Fix: Prioritize connected device name, then look for the first message not sent by "Me"
    val displayName = uiState.connectedDevices.find { it.endpointId == name }?.name 
        ?: messages.firstOrNull { it.senderName != "Me" }?.senderName 
        ?: name

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(AppBackground)) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(displayName, fontWeight = FontWeight.Black, color = Color.White, fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            val isDirect = uiState.knownNodes.find { it.name == displayName }?.isDirect ?: false
                            
                            Surface(
                                color = Color.White.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Outlined.RadioButtonChecked, contentDescription = null, tint = if (isDirect) Color(0xFF10B981) else Color(0xFFF59E0B), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isDirect) "IN RANGE" else "MESH HOP", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, fontSize = 9.sp, color = Color.White)
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.MoreHoriz, contentDescription = null, tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.Medium, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SignalCellularAlt, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SIGNAL STRENGTH: HIGH", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = CyanPrimary.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("E2E ENCRYPTED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
            }
        },
        containerColor = AppBackground,
        bottomBar = {
            val context = androidx.compose.ui.platform.LocalContext.current
            androidx.compose.foundation.layout.Box(modifier = Modifier.imePadding()) {
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
                                pendingAudio = audioBase64
                            }
                        }
                    },
                    onSend = {
                        val hasAudio = pendingAudio != null
                        val finalMessage = if (hasAudio && inputText.isBlank()) "🎤 Voice Note" else inputText.trim()
                        
                        viewModel.sendPrivateMessage(
                            targetName = displayName,
                            text = finalMessage,
                            imageBase64 = pendingImage,
                            audioBase64 = pendingAudio
                        )
                        
                        inputText = ""
                        pendingImage = null
                        pendingAudio = null
                    },
                    onSendLocation = {
                        viewModel.broadcastLocation(context, isPrivate = true, targetName = displayName)
                    },
                    mediaHelper = mediaHelper
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Spacing.Medium),
            reverseLayout = true
        ) {
            items(sortedMessages) { msg ->
                if (!msg.isMine && !msg.seenBy.contains("Me")) {
                    LaunchedEffect(msg.id) {
                        viewModel.markMessageAsSeen(msg.id, isPrivate = true, targetId = name)
                    }
                }

                HighFidelityChatBubble(
                    ChatMessageData(
                        id = msg.id,
                        text = msg.text,
                        imageBase64 = msg.imageBase64,
                        audioBase64 = msg.audioBase64,
                        time = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(msg.timestamp)),
                        hops = if (msg.isHopped) "🌐 MESH HOPPED" else "🟢 DIRECT",
                        receiveMedium = msg.receiveMedium,
                        deliveredTo = msg.deliveredTo,
                        seenBy = msg.seenBy,
                        locationLat = msg.locationLat,
                        locationLng = msg.locationLng,
                        isMine = msg.isMine,
                        isSent = msg.isMine,
                        outboundRoute = msg.outboundRoute,
                        returnRoute = msg.returnRoute
                    ),
                    mediaHelper = mediaHelper,
                    onViewMap = { lat, lng ->
                        onViewMap(lat, lng, msg.senderName, msg.text)
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(modifier = Modifier.width(60.dp), color = Color.White.copy(alpha = 0.1f))
                    Text(
                        "TODAY • OCTOBER 24",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White.copy(alpha = 0.3f),
                        letterSpacing = 1.sp
                    )
                    HorizontalDivider(modifier = Modifier.width(60.dp), color = Color.White.copy(alpha = 0.1f))
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun HighFidelityChatBubble(msg: ChatMessageData, mediaHelper: MediaHelper, onViewMap: (Double, Double) -> Unit = { _, _ -> }) {
    val bubbleColor = if (msg.isMine) CyanPrimary else Color(0xFF35424D) // Lighter slate for others
    val alignment = if (msg.isMine) Alignment.End else Alignment.Start
    val shape = if (msg.isMine) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 2.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 2.dp, bottomEnd = 16.dp)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = bubbleColor,
            shape = shape,
            modifier = Modifier.widthIn(max = 280.dp),
            border = if (!msg.isMine) androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)) else null
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (msg.locationLat != null && msg.locationLng != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "📍 Shared Location:\n[${String.format(java.util.Locale.US, "%.4f", msg.locationLat)}, ${String.format(java.util.Locale.US, "%.4f", msg.locationLng)}]",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onViewMap(msg.locationLat, msg.locationLng) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Map, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("VIEW IN MAP", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                if (msg.imageBase64 != null) {
                    val bitmap = remember(msg.imageBase64) { mediaHelper.decodeBase64ToBitmap(msg.imageBase64) }
                    bitmap?.let { 
                        androidx.compose.foundation.Image(
                            bitmap = it.asImageBitmap(), 
                            contentDescription = "Attached Image", 
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .padding(bottom = Spacing.ExtraSmall),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        ) 
                    }
                }
                
                if (msg.audioBase64 != null) {
                    Surface(
                        onClick = { mediaHelper.playVoiceMail(msg.audioBase64) },
                        color = Color.Black.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.ExtraSmall)
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
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                if (msg.text.isNotBlank()) {
                    Text(
                        text = msg.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        lineHeight = 22.sp
                    )
                }
            }
        }
        
        Row(
            modifier = Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!msg.isMine) {
                val mediumColor = if (msg.receiveMedium.contains("Wi-Fi")) com.example.testresqmesh.core.ui.theme.SuccessGreen else CyanPrimary
                Text("📶 ${msg.receiveMedium}", style = MaterialTheme.typography.labelSmall, color = mediumColor.copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                DotSeparator()
                Text(msg.time, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                DotSeparator()
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(10.dp))
                Spacer(modifier = Modifier.width(4.dp))
                val hopsColor = if (msg.hops.contains("HOPPED")) Color(0xFFF59E0B) else Color.White.copy(alpha = 0.5f)
                Text(msg.hops, style = MaterialTheme.typography.labelSmall, color = hopsColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            } else {
                val statusText = when {
                    msg.seenBy.isNotEmpty() -> "READ"
                    msg.deliveredTo.isNotEmpty() -> "DELIVERED"
                    else -> "SENT"
                }
                
                val statusColor = when {
                    msg.seenBy.isNotEmpty() -> com.example.testresqmesh.core.ui.theme.SuccessGreen
                    else -> Color.White.copy(alpha = 0.7f)
                }
                
                Text(statusText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = statusColor, fontSize = 9.sp)
                DotSeparator()
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(10.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(msg.hops, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                DotSeparator()
                Text(msg.time, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        // --- Visual Mesh Route Tracer UI ---
        if (msg.hops.contains("HOPPED") && msg.isMine) {
            var showRoute by remember { mutableStateOf(false) }

            Text(
                "Trace Route 📍",
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { showRoute = !showRoute },
                style = MaterialTheme.typography.labelSmall,
                color = CyanPrimary.copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold
            )

            if (showRoute) {
                Surface(
                    modifier = Modifier.padding(top = 8.dp).widthIn(max = 280.dp),
                    color = Color.Black.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Outbound Path:", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, fontWeight = FontWeight.Black)
                        val outboundStr = if (msg.outboundRoute.isNotEmpty()) msg.outboundRoute.joinToString(" -> ") else "Unknown"
                        Text(outboundStr, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))

                        Text("Return Receipt Path:", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, fontWeight = FontWeight.Black)
                        val returnStr = if (msg.returnRoute.isNotEmpty()) msg.returnRoute.joinToString(" -> ") else "Pending..."
                        Text(returnStr, color = Color(0xFF10B981), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun DotSeparator() {
    Box(modifier = Modifier.padding(horizontal = 8.dp).size(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)))
}


