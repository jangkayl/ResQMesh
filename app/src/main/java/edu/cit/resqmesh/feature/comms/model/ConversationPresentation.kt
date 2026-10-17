package edu.cit.resqmesh.feature.comms.model

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import edu.cit.resqmesh.R
import edu.cit.resqmesh.core.model.ChatMessage
import edu.cit.resqmesh.core.model.NodeIdentity
import edu.cit.resqmesh.core.ui.theme.ModernMint
import edu.cit.resqmesh.core.ui.theme.ModernSky
import edu.cit.resqmesh.core.ui.theme.ResQTheme
import edu.cit.resqmesh.feature.comms.model.ChatUiState

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
    nodes: List<edu.cit.resqmesh.core.ui.model.NodeItemData>,
    permissionsReady: Boolean = true,
    hardwareReady: Boolean = true
): InboxMeshStatus {
    val reachable = nodes.count {
        !it.isBlocked && (
            it.kind == edu.cit.resqmesh.core.ui.model.NodeKind.DIRECT ||
            it.kind == edu.cit.resqmesh.core.ui.model.NodeKind.HOPPED ||
            it.kind == edu.cit.resqmesh.core.ui.model.NodeKind.RELAY ||
            it.kind == edu.cit.resqmesh.core.ui.model.NodeKind.HANDSHAKING
        )
    }
    val isChecking = nodes.any {
        it.kind == edu.cit.resqmesh.core.ui.model.NodeKind.UNRESPONSIVE ||
        it.kind == edu.cit.resqmesh.core.ui.model.NodeKind.HANDSHAKING ||
        it.kind == edu.cit.resqmesh.core.ui.model.NodeKind.SYNCING
    }
    return InboxMeshStatus(
        active = active,
        reachableCount = reachable,
        checking = isChecking,
        permissionsReady = permissionsReady,
        hardwareReady = hardwareReady
    )
}
