package com.example.testresqmesh.feature.comms.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.R
import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.ui.components.layout.ResQGlassSurface
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import com.example.testresqmesh.core.utils.MediaHelper
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel
import com.example.testresqmesh.ui.state.ChatUiState

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ChatContainerScreen(
    viewModel: CommunicationViewModel,
    mediaHelper: MediaHelper,
    onChatSelected: (String) -> Unit,
    onCommunityConversationChanged: (Boolean) -> Unit,
    onViewMap: (Double, Double, String, String) -> Unit = { _, _, _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentChannel by viewModel.currentChannelId.collectAsState()
    val conversations = remember(uiState) { conversationPreviews(uiState) }
    val communityPreview = remember(uiState.publicMessages) {
        uiState.publicMessages.maxByOrNull { it.timestamp }
    }
    var showNewMessageModal by remember { mutableStateOf(false) }
    var showCommunityConversation by remember { mutableStateOf(false) }

    DisposableEffect(showCommunityConversation) {
        onCommunityConversationChanged(showCommunityConversation)
        onDispose { onCommunityConversationChanged(false) }
    }

    if (showNewMessageModal) {
        ModalBottomSheet(
            onDismissRequest = { showNewMessageModal = false },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            NewMessageModal(
                uiState = uiState,
                onDismiss = { showNewMessageModal = false },
                onRefresh = viewModel::rescan,
                onNodeSelected = {
                    showNewMessageModal = false
                    onChatSelected(it)
                }
            )
        }
    }

    if (showCommunityConversation) {
        PublicChatTab(
            viewModel = viewModel,
            mediaHelper = mediaHelper,
            onBack = { showCommunityConversation = false },
            onChatSelected = { user ->
                showCommunityConversation = false
                onChatSelected(user)
            },
            onViewMap = onViewMap
        )
        return
    }

    MessagesInboxContent(
        channelId = currentChannel,
        communityPreview = communityPreview,
        conversations = conversations,
        onNewMessageClick = { showNewMessageModal = true },
        onCommunityClick = { showCommunityConversation = true },
        onChannelSelected = viewModel::setChannel,
        onConversationClick = onChatSelected,
        onConversationSeen = { message, peer ->
            if (!message.isMine && !message.seenBy.contains("Me")) {
                viewModel.markMessageAsSeen(message.id, isPrivate = true, targetId = peer)
            }
        }
    )
}

internal enum class CommsFilter {
    ALL,
    DIRECT,
    RELAYS
}

@Composable
internal fun MessagesInboxContent(
    channelId: String,
    communityPreview: ChatMessage?,
    conversations: List<ConversationPreview>,
    modifier: Modifier = Modifier,
    onNewMessageClick: () -> Unit,
    onCommunityClick: () -> Unit,
    onChannelSelected: (String) -> Unit,
    onConversationClick: (String) -> Unit,
    onConversationSeen: (ChatMessage, String) -> Unit
) {
    var selectedFilter by remember { mutableStateOf(CommsFilter.ALL) }

    val filteredConversations = remember(conversations, selectedFilter) {
        when (selectedFilter) {
            CommsFilter.ALL -> conversations
            CommsFilter.DIRECT -> conversations.filter { it.status == ConversationStatus.Direct }
            CommsFilter.RELAYS -> conversations.filter { it.status == ConversationStatus.Relayed }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.Large,
                top = Spacing.Large,
                end = Spacing.Large,
                bottom = 120.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            // 1. Tactical Comms Hub Header
            item {
                TacticalCommsHeader(
                    totalNodes = conversations.size
                )
            }

            // 2. Pinned Global Broadcast Row (Replaced giant hero card)
            item {
                GlobalBroadcastPinnedRow(
                    channelId = channelId,
                    preview = communityPreview,
                    onClick = onCommunityClick,
                    onChannelSelected = onChannelSelected
                )
            }

            // 3. Tactical Filter Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TacticalFilterChip(
                        label = "ALL",
                        count = conversations.size,
                        isSelected = selectedFilter == CommsFilter.ALL,
                        onClick = { selectedFilter = CommsFilter.ALL }
                    )
                    TacticalFilterChip(
                        label = "DIRECT",
                        count = conversations.count { it.status == ConversationStatus.Direct },
                        isSelected = selectedFilter == CommsFilter.DIRECT,
                        activeColor = ResQTheme.colors.success,
                        onClick = { selectedFilter = CommsFilter.DIRECT }
                    )
                    TacticalFilterChip(
                        label = "RELAYS",
                        count = conversations.count { it.status == ConversationStatus.Relayed },
                        isSelected = selectedFilter == CommsFilter.RELAYS,
                        activeColor = MaterialTheme.colorScheme.primary,
                        onClick = { selectedFilter = CommsFilter.RELAYS }
                    )
                }
            }

            // 4. Section Label
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.messages_private_section).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.2.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${filteredConversations.size} LINKS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 5. Tactical Conversation List
            if (filteredConversations.isEmpty()) {
                item {
                    ResQGlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(Spacing.Large)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ChatBubbleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.height(Spacing.Small))
                            Text(
                                text = stringResource(R.string.messages_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.messages_empty_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredConversations, key = { it.id }) { conversation ->
                    LaunchedEffect(conversation.lastMessage.id) {
                        onConversationSeen(conversation.lastMessage, conversation.id)
                    }
                    TacticalConversationInboxRow(
                        conversation = conversation,
                        onClick = { onConversationClick(conversation.id) }
                    )
                }
            }
        }

        // 6. Tactical Floating Action Button (FAB) anchored at bottom-right
        FloatingActionButton(
            onClick = onNewMessageClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 28.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = CircleShape,
                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                ),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.messages_new_action),
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun TacticalCommsHeader(
    totalNodes: Int
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = ResQTheme.colors.success.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, ResQTheme.colors.success.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(ResQTheme.colors.success)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.messages_hub_status),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        fontWeight = FontWeight.Bold,
                        color = ResQTheme.colors.success
                    )
                }
            }

            Text(
                text = stringResource(R.string.messages_hub_badge),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.messages_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(2.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.messages_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "AES-GCM",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun GlobalBroadcastPinnedRow(
    channelId: String,
    preview: ChatMessage?,
    onClick: () -> Unit,
    onChannelSelected: (String) -> Unit
) {
    var channelPickerExpanded by remember { mutableStateOf(false) }

    ResQGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(14.dp),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Animated Pulsing Broadcast Icon
            val infiniteTransition = rememberInfiniteTransition(label = "broadcastPulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 0.96f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "broadcastScale"
            )

            Surface(
                modifier = Modifier
                    .size(46.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Campaign,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Broadcast Details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(ResQTheme.colors.success)
                    )
                    Text(
                        text = stringResource(R.string.messages_global_broadcast),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    text = preview?.text?.ifBlank { stringResource(R.string.messages_attachment_preview) }
                        ?: stringResource(R.string.messages_global_desc, channelId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            // Channel Selector Chip
            Box {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                    onClick = { channelPickerExpanded = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.messages_channel_short, channelId),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                DropdownMenu(
                    expanded = channelPickerExpanded,
                    onDismissRequest = { channelPickerExpanded = false }
                ) {
                    (1..5).forEach { channel ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.messages_channel_option, channel)) },
                            onClick = {
                                onChannelSelected(channel.toString())
                                channelPickerExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TacticalFilterChip(
    label: String,
    count: Int,
    isSelected: Boolean,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) activeColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            1.dp,
            if (isSelected) activeColor.copy(alpha = 0.6f) else Color.Transparent
        ),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    letterSpacing = 0.8.sp,
                    fontFamily = FontFamily.Monospace
                ),
                fontWeight = FontWeight.Bold,
                color = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "($count)",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = if (isSelected) activeColor.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun TacticalConversationInboxRow(
    conversation: ConversationPreview,
    onClick: () -> Unit
) {
    val openDescription = stringResource(R.string.messages_open_conversation, conversation.displayName)
    val statusColor = conversation.status.color()

    ResQGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = openDescription }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(12.dp),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tactical Node Avatar with Status Ring
            Box {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    border = BorderStroke(1.5.dp, statusColor.copy(alpha = 0.7f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = conversation.initial,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                // Connection status dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(statusColor)
                        .then(
                            Modifier.padding(1.dp)
                        )
                )
            }

            Spacer(Modifier.width(12.dp))

            // Main info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = conversation.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = conversation.timeLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    text = conversation.preview.ifBlank { stringResource(R.string.messages_attachment_preview) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(6.dp))

                // Tactical Routing Tag
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = statusColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = when (conversation.status) {
                                ConversationStatus.Direct -> stringResource(R.string.messages_direct_link_tag)
                                ConversationStatus.Relayed -> stringResource(R.string.messages_relay_tag)
                                ConversationStatus.Checking -> conversation.status.label()
                                ConversationStatus.Offline -> conversation.status.label()
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp
                            ),
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                        )
                    }
                }
            }

            // Right side: Unread Badge or Chevron
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = 6.dp)
            ) {
                if (conversation.unreadCount > 0) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = conversation.unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

internal enum class ConversationStatus {
    Direct,
    Checking,
    Relayed,
    Offline;

    @Composable
    fun label(): String = stringResource(
        when (this) {
            Direct -> R.string.messages_status_direct
            Checking -> R.string.messages_status_checking
            Relayed -> R.string.messages_status_relayed
            Offline -> R.string.messages_status_offline
        }
    )

    @Composable
    fun color() = when (this) {
        Direct -> ResQTheme.colors.success
        Checking -> ResQTheme.colors.warning
        Relayed -> MaterialTheme.colorScheme.primary
        Offline -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

internal data class ConversationPreview(
    val id: String,
    val displayName: String,
    val initial: String,
    val preview: String,
    val timeLabel: String,
    val status: ConversationStatus,
    val lastMessage: ChatMessage,
    val unreadCount: Int = 0
)

internal fun conversationPreviews(state: ChatUiState): List<ConversationPreview> =
    state.privateMessages.mapNotNull { (id, messages) ->
        val latest = messages.maxByOrNull { it.timestamp } ?: return@mapNotNull null
        val displayName = NodeIdentity.displayNameOf(id).ifBlank { id }
        val directLink = state.connectedDevices.firstOrNull { NodeIdentity.matches(it.name, id) }
        val status = when {
            directLink?.isPayloadReady == true && directLink.isPeerResponsive && !directLink.isProvisional -> ConversationStatus.Direct
            directLink != null -> ConversationStatus.Checking
            state.knownNodes.any { NodeIdentity.matches(it.name, id) && !it.isDirect } -> ConversationStatus.Relayed
            else -> ConversationStatus.Offline
        }
        val unread = messages.count { !it.isMine && !it.seenBy.contains("Me") }
        ConversationPreview(
            id = id,
            displayName = displayName,
            initial = displayName.firstOrNull()?.uppercase() ?: "?",
            preview = latest.text,
            timeLabel = inboxTimeLabel(latest.timestamp),
            status = status,
            lastMessage = latest,
            unreadCount = unread
        )
    }.sortedByDescending { it.lastMessage.timestamp }

internal fun inboxTimeLabel(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = ((now - timestamp).coerceAtLeast(0L) / 60_000L)
    return when {
        minutes == 0L -> "Now"
        minutes < 60L -> "${minutes}m"
        minutes < 1_440L -> "${minutes / 60}h"
        else -> "${minutes / 1_440}d"
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun MessagesInboxPreview() {
    TestResQMeshTheme {
        MessagesInboxContent(
            channelId = "1",
            communityPreview = null,
            conversations = emptyList(),
            onNewMessageClick = {},
            onCommunityClick = {},
            onChannelSelected = {},
            onConversationClick = {},
            onConversationSeen = { _, _ -> }
        )
    }
}
