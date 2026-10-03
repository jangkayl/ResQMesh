package com.example.testresqmesh.feature.comms.model

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.example.testresqmesh.R
import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.ui.theme.ModernMint
import com.example.testresqmesh.core.ui.theme.ModernSky
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.feature.comms.model.ChatUiState

enum class ConversationStatus {
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
        Direct -> ModernMint
        Checking -> ResQTheme.colors.warning
        Relayed -> ModernSky
        Offline -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

data class ConversationPreview(
    val id: String,
    val displayName: String,
    val initial: String,
    val preview: String,
    val timeLabel: String,
    val status: ConversationStatus,
    val lastMessage: ChatMessage,
    val unreadCount: Int = 0
)

fun conversationPreviews(state: ChatUiState): List<ConversationPreview> =
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
    nodes: List<com.example.testresqmesh.core.ui.model.NodeItemData>,
    permissionsReady: Boolean = true,
    hardwareReady: Boolean = true
): InboxMeshStatus {
    val reachable = nodes.count {
        !it.isBlocked && (
            it.kind == com.example.testresqmesh.core.ui.model.NodeKind.DIRECT ||
            it.kind == com.example.testresqmesh.core.ui.model.NodeKind.HOPPED ||
            it.kind == com.example.testresqmesh.core.ui.model.NodeKind.RELAY ||
            it.kind == com.example.testresqmesh.core.ui.model.NodeKind.HANDSHAKING
        )
    }
    val isChecking = nodes.any {
        it.kind == com.example.testresqmesh.core.ui.model.NodeKind.UNRESPONSIVE ||
        it.kind == com.example.testresqmesh.core.ui.model.NodeKind.HANDSHAKING ||
        it.kind == com.example.testresqmesh.core.ui.model.NodeKind.SYNCING
    }
    return InboxMeshStatus(
        active = active,
        reachableCount = reachable,
        checking = isChecking,
        permissionsReady = permissionsReady,
        hardwareReady = hardwareReady
    )
}
