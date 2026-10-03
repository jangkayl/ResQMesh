package com.example.testresqmesh.feature.comms.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.R
import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.ui.theme.ModernMint
import com.example.testresqmesh.core.ui.theme.ModernSky
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import com.example.testresqmesh.core.utils.MediaHelper
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel
import com.example.testresqmesh.feature.comms.model.CommsFilter
import com.example.testresqmesh.feature.comms.model.ConversationPreview
import com.example.testresqmesh.feature.comms.model.ConversationStatus
import com.example.testresqmesh.feature.comms.model.conversationPreviews
import com.example.testresqmesh.feature.comms.ui.components.GlobalBroadcastCommandCard
import com.example.testresqmesh.feature.comms.ui.components.ModernConversationInboxRow
import com.example.testresqmesh.feature.comms.ui.components.ModernEmptyInboxCard
import com.example.testresqmesh.feature.comms.ui.components.ModernFilterChip
import com.example.testresqmesh.feature.comms.ui.components.ModernMessagesHeader

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ChatContainerScreen(
    viewModel: CommunicationViewModel,
    mediaHelper: MediaHelper,
    onChatSelected: (String) -> Unit,
    onCommunityClick: () -> Unit = {},
    onCommunityConversationChanged: (Boolean) -> Unit = {},
    onViewMap: (Double, Double, String, String) -> Unit = { _, _, _, _ -> },
    initialOpenCommunity: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsState()
    val savedConversationStates by viewModel.conversationStates.collectAsState()
    val currentChannel by viewModel.currentChannelId.collectAsState()
    val conversations = remember(uiState) { conversationPreviews(uiState) }
    val communityPreview = remember(uiState.publicMessages) {
        uiState.publicMessages.maxByOrNull { it.timestamp }
    }
    var showNewMessageModal by remember { mutableStateOf(false) }

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

    MessagesInboxContent(
        channelId = currentChannel,
        communityPreview = communityPreview,
        communityUnread = uiState.publicMessages.count { !it.isMine && it.timestamp > (savedConversationStates.firstOrNull { state -> state.conversationId == "COMMUNITY" }?.lastReadAt ?: 0L) },
        conversations = conversations,
        onNewMessageClick = { showNewMessageModal = true },
        onCommunityClick = onCommunityClick,
        onChannelSelected = viewModel::setChannel,
        onConversationClick = onChatSelected
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
    communityUnread: Int = 0
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
                bottom = 100.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Modern Messages Header
            item {
                ModernMessagesHeader()
                Spacer(Modifier.height(4.dp))
            }

            // 2. Hero Pinned Card: Global Mesh Community Channel
            item {
                GlobalBroadcastCommandCard(
                    channelId = channelId,
                    preview = communityPreview,
                    unreadCount = communityUnread,
                    onClick = onCommunityClick,
                    onChannelSelected = onChannelSelected
                )
                Spacer(Modifier.height(4.dp))
            }

            // 3. Modern Filter Pills (All, Direct, Relayed, Unread)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModernFilterChip(
                        label = "All",
                        count = conversations.size,
                        isSelected = selectedFilter == CommsFilter.ALL,
                        onClick = { selectedFilter = CommsFilter.ALL }
                    )
                    ModernFilterChip(
                        label = "Direct",
                        count = conversations.count { it.status == ConversationStatus.Direct },
                        isSelected = selectedFilter == CommsFilter.DIRECT,
                        activeColor = ModernMint,
                        onClick = { selectedFilter = CommsFilter.DIRECT }
                    )
                    ModernFilterChip(
                        label = "Relayed",
                        count = conversations.count { it.status == ConversationStatus.Relayed },
                        isSelected = selectedFilter == CommsFilter.RELAYS,
                        activeColor = ModernSky,
                        onClick = { selectedFilter = CommsFilter.RELAYS }
                    )
                    val unreadTotal = conversations.count { it.unreadCount > 0 }
                    ModernFilterChip(
                        label = "Unread",
                        count = unreadTotal,
                        isSelected = selectedFilter == CommsFilter.UNREAD,
                        activeColor = ResQTheme.colors.warning,
                        onClick = { selectedFilter = CommsFilter.UNREAD }
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            // 4. Section Label (Ensures "Private" matches test assertions)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.messages_private_section),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${filteredConversations.size}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // 5. Conversation List or Friendly Empty State
            if (filteredConversations.isEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    ModernEmptyInboxCard(
                        filter = selectedFilter,
                        onNewMessageClick = onNewMessageClick
                    )
                }
            } else {
                items(filteredConversations, key = { it.id }) { conversation ->
                    ModernConversationInboxRow(
                        conversation = conversation,
                        onClick = { onConversationClick(conversation.id) }
                    )
                }
            }
        }

        // 6. Modern Floating Action Button (FAB)
        FloatingActionButton(
            onClick = onNewMessageClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
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
