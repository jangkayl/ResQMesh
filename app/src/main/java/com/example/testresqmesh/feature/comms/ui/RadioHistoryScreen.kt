package com.example.testresqmesh.feature.comms.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.core.model.ConversationPolicy
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.utils.MediaHelper
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel

@Composable
fun RadioHistoryScreen(vm: CommunicationViewModel, media: MediaHelper, onBack: () -> Unit) {
    val all by vm.allPublicMessages.collectAsState()
    val states by vm.conversationStates.collectAsState()
    var selected by remember { mutableStateOf<String?>(null) }
    BackHandler { if (selected != null) selected = null else onBack() }

    if (selected != null) {
        PublicConversationScreen(
            vm = vm,
            media = media,
            kind = "RADIO",
            channel = selected!!,
            title = "Radio Channel $selected",
            readOnly = false,
            onBack = { selected = null }
        )
        return
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Tactical Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Radio"
                    )
                }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Radio Conversations",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Offline voice notes & chatter by channel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items((1..5).map { it.toString() }) { ch ->
                    val messages = ConversationPolicy.messages(all, "RADIO", ch)
                    val key = ConversationPolicy.key("RADIO", ch)
                    val read = states.firstOrNull { it.conversationId == key }?.lastReadAt ?: 0L
                    val unreadCount = messages.count { !it.isMine && it.timestamp > read }
                    val latestMsg = messages.lastOrNull()
                    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

                    Surface(
                        onClick = { selected = ch },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (unreadCount > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else if (isLight) MaterialTheme.colorScheme.outlineVariant
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        shadowElevation = if (isLight) 1.dp else 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "CH $ch",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(Modifier.width(14.dp))

                            Column(Modifier.widthIn(max = 260.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Channel $ch",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (unreadCount > 0) {
                                        Spacer(Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFFB42318)
                                        ) {
                                            Text(
                                                text = "$unreadCount new",
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = latestMsg?.let { msg ->
                                        val sender = NodeIdentity.displayNameOf(msg.senderName)
                                        val preview = if (msg.audioBase64 != null) "🎤 Voice note" else msg.text
                                        "$sender: $preview"
                                    } ?: "No recorded messages yet",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (latestMsg != null) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Icon(
                                imageVector = Icons.Outlined.ChevronRight,
                                contentDescription = "Open Channel $ch",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
