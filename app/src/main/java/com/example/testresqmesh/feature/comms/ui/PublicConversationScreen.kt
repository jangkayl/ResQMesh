package com.example.testresqmesh.feature.comms.ui

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import com.example.testresqmesh.core.ui.components.layout.ResQAuroraBackground
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.core.model.ConversationPolicy
import com.example.testresqmesh.core.utils.MediaHelper
import com.example.testresqmesh.feature.comms.ui.components.ChatBubble
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel

@Composable
fun PublicConversationScreen(
    vm: CommunicationViewModel,
    media: MediaHelper,
    kind: String,
    channel: String = "",
    sosId: String = "",
    title: String,
    readOnly: Boolean = false,
    onBack: () -> Unit,
    header: @Composable (isCompact: Boolean) -> Unit = {}
) {
    val all by vm.allPublicMessages.collectAsState()
    val states by vm.conversationStates.collectAsState()
    val key = ConversationPolicy.key(kind, channel, sosId)
    val messages = ConversationPolicy.messages(all, kind, channel, sosId)
    val displayMessages = remember(messages) { messages.asReversed() }
    var input by remember(key) { mutableStateOf(states.firstOrNull { it.conversationId == key }?.draft.orEmpty()) }
    var restored by remember(key) { mutableStateOf(false) }

    LaunchedEffect(states, key) {
        val saved = states.firstOrNull { it.conversationId == key }
        if (!restored && saved != null) {
            if (input.isEmpty()) input = saved.draft
            restored = true
        }
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val latestMessageId = messages.lastOrNull()?.id
    LaunchedEffect(latestMessageId, key) {
        vm.read(key)
        messages.filter { !it.isMine && it.seenBy.isEmpty() }.forEach { vm.markMessageAsSeen(it.id, false) }
        if (latestMessageId != null) {
            listState.scrollToItem(0)
        }
    }

    @Composable
    fun ConversationContainer(content: @Composable () -> Unit) {
        if (kind == "RADIO") {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                content = content
            )
        } else {
            ResQAuroraBackground(Modifier.fillMaxSize()) {
                content()
            }
        }
    }

    ConversationContainer {
        BoxWithConstraints(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        val compactLayout = maxHeight < 520.dp || LocalDensity.current.fontScale > 1.3f
        val compactControls = kind == "SOS" && compactLayout
        val density = LocalDensity.current
        val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
        var headerHeightDp by remember { mutableStateOf(160.dp) }

        Column(Modifier.fillMaxSize()) {
            // Content Area: Full-height message stream with floating header overlay
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Layer 1: Message Stream (Scrolls underneath floating header)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(
                        top = headerHeightDp + 8.dp,
                        bottom = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom)
                ) {
                    items(displayMessages, key = { it.id }) { message ->
                        ChatBubble(message, media)
                    }
                    if (messages.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (kind == "SOS") "No replies yet." else "No Radio notes yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // Layer 2: Floating Header (Solid Top Bar Mask + Solid Details Card)
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            val heightInDp = with(density) { coordinates.size.height.toDp() }
                            if (headerHeightDp != heightInDp) {
                                headerHeightDp = heightInDp
                            }
                        }
                ) {
                    // Solid Top Bar Mask (Non-transparent, anchors title, back button, and status bar)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = if (isLight) 1.5.dp else 2.5.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBack, modifier = Modifier.testTag("conversation_back")) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = when (kind) {
                                        "RADIO" -> "Channel $channel Broadcast"
                                        "SOS" -> "Emergency Distress Thread"
                                        else -> "Broadcast Conversation"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Floating Details Card Slot (Solid card with outer margin for floating effect)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        header(compactControls)
                    }
                }
            }

            // Bottom Composer / Status Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                if (readOnly) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = if (kind == "SOS") "Read-only history · This emergency conversation has ended." else "Read-only history",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp).testTag("conversation_read_only"),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = {
                                input = it
                                restored = true
                                vm.draft(key, it)
                            },
                            placeholder = {
                                Text(if (kind == "RADIO") "Message this Radio channel…" else "Reply to this SOS…")
                            },
                            modifier = Modifier.weight(1f).testTag("conversation_input"),
                            maxLines = if (compactLayout) 2 else 4,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )

                        FilledIconButton(
                            onClick = {
                                if (input.isNotBlank()) {
                                    vm.sendConversation(kind, channel, sosId, input.trim())
                                    input = ""
                                }
                            },
                            enabled = input.isNotBlank(),
                            modifier = Modifier.size(50.dp).testTag("conversation_send"),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send Message",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    }
}
