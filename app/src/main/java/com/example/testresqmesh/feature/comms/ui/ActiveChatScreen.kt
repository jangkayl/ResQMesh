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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import com.example.testresqmesh.core.ui.theme.InboxBackground
import com.example.testresqmesh.core.ui.theme.InboxAccentBlue
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel

data class ChatMessageData(
    val id: String,
    val text: String,
    val time: String,
    val hops: String,
    val receiveMedium: String,
    val deliveredTo: List<String>,
    val seenBy: List<String>,
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    val isMine: Boolean,
    val isSent: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveChatScreen(name: String, viewModel: CommunicationViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Messages for this specific target (name is treated as endpointId here)
    val messages = uiState.privateMessages[name] ?: emptyList()
    
    // Sort messages newest first for reverseLayout
    val sortedMessages = remember(messages) { messages.sortedByDescending { it.timestamp } }
    
    // LazyListState to control scrolling if needed
    val listState = rememberLazyListState()

    // Fix: Prioritize connected device name, then look for the first message not sent by "Me"
    val displayName = uiState.connectedDevices.find { it.endpointId == name }?.name 
        ?: messages.firstOrNull { it.senderName != "Me" }?.senderName 
        ?: name

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(InboxBackground)) {
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
                            Surface(
                                color = Color.White.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Outlined.RadioButtonChecked, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("IN RANGE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, fontSize = 9.sp, color = Color.White)
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.disconnectDevice(name) }) {
                            Icon(Icons.Outlined.LinkOff, contentDescription = "Disconnect Link", tint = Color(0xFFEF4444))
                        }
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.MoreHoriz, contentDescription = null, tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = InboxBackground)
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
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = InboxAccentBlue.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("E2E ENCRYPTED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
            }
        },
        containerColor = InboxBackground,
        bottomBar = {
            val context = androidx.compose.ui.platform.LocalContext.current
            ChatBottomInput(
                onSendMessage = { text ->
                    viewModel.sendPrivateMessage(displayName, text)
                },
                onSendLocation = {
                    viewModel.broadcastLocation(context, isPrivate = true, targetName = displayName)
                }
            )
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
                        time = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(msg.timestamp)),
                        hops = if (msg.isHopped) "🌐 MESH HOPPED" else "🟢 DIRECT",
                        receiveMedium = msg.receiveMedium,
                        deliveredTo = msg.deliveredTo,
                        seenBy = msg.seenBy,
                        locationLat = msg.locationLat,
                        locationLng = msg.locationLng,
                        isMine = msg.isMine,
                        isSent = msg.isMine
                    )
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
fun HighFidelityChatBubble(msg: ChatMessageData) {
    val bubbleColor = if (msg.isMine) InboxAccentBlue else Color(0xFF35424D) // Lighter slate for others
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
                }
                Text(
                    text = msg.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    lineHeight = 22.sp
                )
            }
        }
        
        Row(
            modifier = Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!msg.isMine) {
                val mediumColor = if (msg.receiveMedium.contains("Wi-Fi")) com.example.testresqmesh.core.ui.theme.SuccessGreen else InboxAccentBlue
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
    }
}

@Composable
fun DotSeparator() {
    Box(modifier = Modifier.padding(horizontal = 8.dp).size(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)))
}

@Composable
fun ChatBottomInput(onSendMessage: (String) -> Unit, onSendLocation: () -> Unit) {
    var textState by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .background(InboxBackground)
            .padding(horizontal = Spacing.Medium, vertical = 16.dp)
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Utility Buttons
            UtilityButton(Icons.Default.LocationOn, onClick = onSendLocation)
            UtilityButton(Icons.Outlined.Wifi)
            
            // Message Input
            Surface(
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                    BasicTextField(
                        value = textState,
                        onValueChange = { textState = it },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        decorationBox = { innerTextField ->
                            if (textState.isEmpty()) {
                                Text("Secure Mesh Message...", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodyMedium)
                            }
                            innerTextField()
                        }
                    )
                    IconButton(onClick = {
                        if (textState.isNotBlank()) {
                            onSendMessage(textState)
                            textState = ""
                        }
                    }) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = if (textState.isNotBlank()) InboxAccentBlue else Color.White.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickReplyChip("Safe") { textState = it }
            QuickReplyChip("Moving") { textState = it }
            QuickReplyChip("SOS Needed") { textState = it }
            QuickReplyChip("Received") { textState = it }
        }
    }
}

@Composable
fun UtilityButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit = {}) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp), 
        shape = RoundedCornerShape(10.dp), 
        color = Color.White.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
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
