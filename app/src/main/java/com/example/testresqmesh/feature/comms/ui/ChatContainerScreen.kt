package com.example.testresqmesh.feature.comms.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.R
import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.ui.components.feedback.ResQEmptyState
import com.example.testresqmesh.core.ui.components.layout.ResQContentSurface
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
    onCommunityConversationChanged: (Boolean) -> Unit
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
            onBack = { showCommunityConversation = false }
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
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.Large,
            top = Spacing.Large,
            end = Spacing.Large,
            bottom = 120.dp
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.messages_title),
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = stringResource(R.string.messages_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 10.dp,
                    onClick = onNewMessageClick
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.messages_new_action),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }

        item {
            CommunityHeroBanner(
                channelId = channelId,
                preview = communityPreview,
                onClick = onCommunityClick,
                onChannelSelected = onChannelSelected
            )
        }

        item {
            Text(
                text = stringResource(R.string.messages_private_section),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = Spacing.Small)
            )
        }

        if (conversations.isEmpty()) {
            item {
                ResQGlassSurface(
                    shape = RoundedCornerShape(28.dp),
                    contentPadding = PaddingValues(vertical = Spacing.Medium)
                ) {
                    ResQEmptyState(
                        title = stringResource(R.string.messages_empty_title),
                        message = stringResource(R.string.messages_empty_description),
                        icon = Icons.Outlined.ChatBubbleOutline
                    )
                }
            }
        } else {
            items(conversations, key = { it.id }) { conversation ->
                LaunchedEffect(conversation.lastMessage.id) {
                    onConversationSeen(conversation.lastMessage, conversation.id)
                }
                ConversationInboxRow(
                    conversation = conversation,
                    onClick = { onConversationClick(conversation.id) }
                )
            }
        }
    }
}

@Composable
private fun CommunityHeroBanner(
    channelId: String,
    preview: ChatMessage?,
    onClick: () -> Unit,
    onChannelSelected: (String) -> Unit
) {
    var channelPickerExpanded by remember { mutableStateOf(false) }
    val openDescription = stringResource(R.string.messages_open_community)
    
    ResQGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = openDescription }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        contentPadding = PaddingValues(Spacing.Large),
        shadowElevation = 14.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Campaign,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(Modifier.height(Spacing.Medium))
            Text(
                text = stringResource(R.string.messages_community_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(Spacing.ExtraSmall))
            Text(
                text = preview?.text?.ifBlank { stringResource(R.string.messages_attachment_preview) }
                    ?: stringResource(R.string.messages_community_description, channelId),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = Spacing.Medium)
            )
            Spacer(Modifier.height(Spacing.Medium))
            Box {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = { channelPickerExpanded = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.messages_channel_short, channelId),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp).padding(start = 4.dp)
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
private fun ConversationInboxRow(
    conversation: ConversationPreview,
    onClick: () -> Unit
) {
    val openDescription = stringResource(R.string.messages_open_conversation, conversation.displayName)
    ResQContentSurface(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = openDescription }.clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(Spacing.Medium),
        shadowElevation = 2.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = conversation.initial,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(Modifier.width(Spacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = conversation.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(Spacing.Small))
                    Text(
                        text = conversation.timeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = conversation.preview.ifBlank { stringResource(R.string.messages_attachment_preview) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(Spacing.ExtraSmall))
                Text(
                    text = conversation.status.label(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = conversation.status.color()
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
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
    val lastMessage: ChatMessage
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
        ConversationPreview(
            id = id,
            displayName = displayName,
            initial = displayName.firstOrNull()?.uppercase() ?: "?",
            preview = latest.text,
            timeLabel = inboxTimeLabel(latest.timestamp),
            status = status,
            lastMessage = latest
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
