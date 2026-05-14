package com.example.testresqmesh.ui.screens.comms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.data.models.ChatMessage
import com.example.testresqmesh.ui.theme.InboxBackground
import com.example.testresqmesh.ui.theme.InboxAccentBlue
import com.example.testresqmesh.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveChatScreen(name: String, onBack: () -> Unit) {
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
                            Text(name, fontWeight = FontWeight.Black, color = Color.White)
                            Spacer(modifier = Modifier.width(12.dp))
                            Surface(
                                color = Color.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF10B981)))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("IN RANGE", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = Color.White)
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.MoreHoriz, contentDescription = null, tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = InboxBackground)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.Medium, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SignalCellularAlt, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("SIGNAL STRENGTH: HIGH", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = InboxAccentBlue.copy(alpha = 0.6f), modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("E2E ENCRYPTED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp)
                    }
                }
                Divider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
            }
        },
        containerColor = InboxBackground,
        bottomBar = {
            ChatBottomInput()
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Spacing.Medium)
        ) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Divider(modifier = Modifier.width(60.dp).align(Alignment.CenterVertically), color = Color.White.copy(alpha = 0.1f))
                    Text(
                        "TODAY • OCTOBER 24",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White.copy(alpha = 0.3f),
                        letterSpacing = 1.sp
                    )
                    Divider(modifier = Modifier.width(60.dp).align(Alignment.CenterVertically), color = Color.White.copy(alpha = 0.1f))
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            val mockMessages = listOf(
                ChatMessageData("Is the relay node near the bridge active? I'm getting low signal on the north side.", "14:02", "2 Hops", false),
                ChatMessageData("Checking logs... Yes, Node-842 is relaying. It took 2 hops to reach you. I'll boost the beacon interval.", "14:03", "2 Hops", true),
                ChatMessageData("Copy that. I'm moving towards the rally point. Keep the mesh open.", "14:05", "1 Hops", false),
                ChatMessageData("Understood. Encrypted link is stable. See you at the marker.", "14:06", "Direct", true, isSent = true)
            )

            items(mockMessages) { msg ->
                HighFidelityChatBubble(msg)
            }
        }
    }
}

@Composable
fun HighFidelityChatBubble(msg: ChatMessageData) {
    val bubbleColor = if (msg.isMine) InboxAccentBlue else Color.White.copy(alpha = 0.1f)
    val alignment = if (msg.isMine) Alignment.End else Alignment.Start
    val shape = if (msg.isMine) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 2.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 2.dp, bottomEnd = 16.dp)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = bubbleColor,
            shape = shape,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = msg.text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                lineHeight = 20.sp
            )
        }
        
        Row(
            modifier = Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!msg.isMine) {
                Text(msg.time, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(10.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(msg.hops, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)))
                Spacer(modifier = Modifier.width(8.dp))
                Text("DELIVERED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp)
            } else {
                Text(if (msg.isSent) "SENT" else "DELIVERED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)))
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(10.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(msg.hops, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(msg.time, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun ChatBottomInput() {
    Column(
        modifier = Modifier
            .background(InboxBackground)
            .padding(Spacing.Medium)
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(modifier = Modifier.size(44.dp), shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = 0.1f)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White.copy(alpha = 0.6f))
                }
            }
            Surface(
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(10.dp),
                color = Color.White.copy(alpha = 0.1f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text("Secure Mesh Message...", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.Send, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(20.dp))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickReplyChip("Safe")
            QuickReplyChip("Moving")
            QuickReplyChip("SOS Needed")
            QuickReplyChip("Received")
        }
    }
}

@Composable
fun QuickReplyChip(text: String) {
    Surface(
        color = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

data class ChatMessageData(val text: String, val time: String, val hops: String, val isMine: Boolean, val isSent: Boolean = false)
