package com.example.testresqmesh.feature.comms.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
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
        onConversationClick = onChatSelected
    )
}

internal enum class CommsFilter {
    ALL,
    DIRECT,
    RELAYS,
    UNREAD
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
    onConversationClick: (String) -> Unit
) {
    var selectedFilter by remember { mutableStateOf(CommsFilter.ALL) }

    val filteredConversations = remember(conversations, selectedFilter) {
        when (selectedFilter) {
            CommsFilter.ALL -> conversations
            CommsFilter.DIRECT -> conversations.filter { it.status == ConversationStatus.Direct }
            CommsFilter.RELAYS -> conversations.filter { it.status == ConversationStatus.Relayed }
            CommsFilter.UNREAD -> conversations.filter { it.unreadCount > 0 }
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
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. Tactical Comms Hub Header
            item {
                TacticalCommsHeader()
                Spacer(Modifier.height(8.dp))
            }

            // 2. All-Hands Emergency Community Broadcast Command Card
            item {
                GlobalBroadcastCommandCard(
                    channelId = channelId,
                    preview = communityPreview,
                    onClick = onCommunityClick,
                    onChannelSelected = onChannelSelected
                )
                Spacer(Modifier.height(6.dp))
            }

            // 3. Tactical Filter Toolbar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                    val unreadTotal = conversations.count { it.unreadCount > 0 }
                    TacticalFilterChip(
                        label = "UNREAD",
                        count = unreadTotal,
                        isSelected = selectedFilter == CommsFilter.UNREAD,
                        activeColor = ResQTheme.colors.warning,
                        onClick = { selectedFilter = CommsFilter.UNREAD }
                    )
                }
                Spacer(Modifier.height(6.dp))
            }

            // 4. Section Label (Ensures "Private" matches test assertion)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.messages_private_section),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${filteredConversations.size} CHATS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 5. Tactical Conversation List or Guided Empty State
            if (filteredConversations.isEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    TacticalEmptyInboxCard(
                        filter = selectedFilter,
                        onNewMessageClick = onNewMessageClick
                    )
                }
            } else {
                items(filteredConversations, key = { it.id }) { conversation ->
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
                .padding(end = 20.dp, bottom = 24.dp)
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
private fun TacticalCommsHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.messages_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.messages_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = ResQTheme.colors.success,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "LOCAL MESH",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        letterSpacing = 0.5.sp
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * High-visibility Community All-Hands Broadcast Card.
 * Uses exact semantics content description and label for automated tests.
 */
@Composable
private fun GlobalBroadcastCommandCard(
    channelId: String,
    preview: ChatMessage?,
    onClick: () -> Unit,
    onChannelSelected: (String) -> Unit
) {
    var channelPickerExpanded by remember { mutableStateOf(false) }
    val openCommunityDesc = stringResource(R.string.messages_open_community)

    ResQGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = openCommunityDesc }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(Spacing.Medium),
        shadowElevation = 8.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            // Header Row: Broadcast Badge + Channel Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Campaign,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(ResQTheme.colors.success)
                            )
                            Spacer(Modifier.width(6.dp))
                            // Satisfies onNodeWithText("Community") test assertion
                            Text(
                                text = stringResource(R.string.community_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = stringResource(R.string.messages_global_broadcast),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                letterSpacing = 0.8.sp
                            ),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Channel Switcher Pill
                Box {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        onClick = { channelPickerExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
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

            // Message Preview Strip
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = preview?.text?.ifBlank { stringResource(R.string.messages_attachment_preview) }
                            ?: stringResource(R.string.messages_global_desc, channelId),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
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
            if (isSelected) activeColor.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        ),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    letterSpacing = 0.6.sp,
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
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        shadowElevation = 3.dp
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
                )
            }

            Spacer(Modifier.width(12.dp))

            // Main Info
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (conversation.lastMessage.isMine) {
                        Icon(
                            imageVector = if (conversation.lastMessage.seenBy.isNotEmpty()) Icons.Outlined.DoneAll else Icons.Outlined.Check,
                            contentDescription = null,
                            tint = if (conversation.lastMessage.seenBy.isNotEmpty()) ResQTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = conversation.preview.ifBlank { stringResource(R.string.messages_attachment_preview) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Tactical Routing Tag
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

@Composable
private fun TacticalEmptyInboxCard(
    filter: CommsFilter,
    onNewMessageClick: () -> Unit
) {
    val (title, description) = when (filter) {
        CommsFilter.ALL -> stringResource(R.string.messages_empty_title) to stringResource(R.string.messages_empty_description)
        CommsFilter.DIRECT -> "No direct conversations" to "Chats with ready direct peers will appear here."
        CommsFilter.RELAYS -> "No relayed conversations" to "Chats with currently reachable relayed peers will appear here."
        CommsFilter.UNREAD -> "No unread conversations" to "Incoming messages you have not viewed will appear here."
    }
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
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(Modifier.height(Spacing.Medium))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (filter == CommsFilter.ALL) {
                Spacer(Modifier.height(Spacing.Medium))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier.clickable(onClick = onNewMessageClick)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.Medium, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Find Nearby People",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
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
        val displayName = NodeIdentity.displayNameOf(NodeIdentity.currentName(id, state.peerNames)).ifBlank { id }
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

internal data class InboxMeshStatus(
    val active: Boolean,
    val reachableCount: Int,
    val checking: Boolean,
    val permissionsReady: Boolean = true,
    val hardwareReady: Boolean = true
)

internal fun inboxMeshStatus(
    active: Boolean,
    nodes: List<com.example.testresqmesh.feature.radar.ui.NodeItemData>,
    permissionsReady: Boolean = true,
    hardwareReady: Boolean = true
): InboxMeshStatus {
    val reachable = nodes.count {
        !it.isBlocked && (
            it.kind == com.example.testresqmesh.feature.radar.ui.NodeKind.DIRECT ||
            it.kind == com.example.testresqmesh.feature.radar.ui.NodeKind.HOPPED ||
            it.kind == com.example.testresqmesh.feature.radar.ui.NodeKind.RELAY ||
            it.kind == com.example.testresqmesh.feature.radar.ui.NodeKind.HANDSHAKING
        )
    }
    val isChecking = nodes.any {
        it.kind == com.example.testresqmesh.feature.radar.ui.NodeKind.UNRESPONSIVE ||
        it.kind == com.example.testresqmesh.feature.radar.ui.NodeKind.HANDSHAKING ||
        it.kind == com.example.testresqmesh.feature.radar.ui.NodeKind.SYNCING
    }
    return InboxMeshStatus(
        active = active,
        reachableCount = reachable,
        checking = isChecking,
        permissionsReady = permissionsReady,
        hardwareReady = hardwareReady
    )
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
            onConversationClick = {}
        )
    }
}
