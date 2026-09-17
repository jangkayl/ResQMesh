package com.example.testresqmesh.feature.comms.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.R
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.ui.components.feedback.ResQEmptyState
import com.example.testresqmesh.core.ui.components.inputs.ResQTextField
import com.example.testresqmesh.core.ui.components.layout.ResQGlassSurface
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import com.example.testresqmesh.ui.state.ChatUiState

@Composable
fun NewMessageModal(
    uiState: ChatUiState,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onNodeSelected: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val candidates = remember(uiState) { recipientCandidates(uiState) }
    val shownCandidates = remember(candidates, query) {
        val term = query.trim()
        if (term.isBlank()) candidates else candidates.filter {
            it.displayName.contains(term, ignoreCase = true) || it.name.contains(term, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.88f)
            .padding(horizontal = Spacing.Large),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.new_message_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = stringResource(R.string.new_message_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                onClick = onDismiss
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.new_message_close_action),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        ResQTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.new_message_search_placeholder),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.new_message_people_section),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.new_message_refresh_action),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (shownCandidates.isEmpty()) {
            ResQGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                contentPadding = PaddingValues(vertical = Spacing.Medium)
            ) {
                ResQEmptyState(
                    title = stringResource(R.string.new_message_empty_title),
                    message = stringResource(R.string.new_message_empty_description),
                    icon = Icons.Outlined.PersonOutline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                contentPadding = PaddingValues(bottom = Spacing.Large)
            ) {
                items(shownCandidates, key = { it.name }) { candidate ->
                    RecipientRow(
                        candidate = candidate,
                        onClick = { onNodeSelected(candidate.name) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecipientRow(
    candidate: RecipientCandidate,
    onClick: () -> Unit
) {
    val statusColor = candidate.availability.color()
    val recipientDescription = stringResource(R.string.new_message_choose_recipient, candidate.displayName)
    ResQGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (candidate.isSelectable) 1f else 0.64f)
            .semantics { contentDescription = recipientDescription }
            .clickable(enabled = candidate.isSelectable, onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(Spacing.Medium),
        shadowElevation = 10.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = candidate.initial,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(Modifier.width(Spacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = candidate.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(Spacing.ExtraSmall))
                Text(
                    text = candidate.availability.label(),
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

internal data class RecipientCandidate(
    val name: String,
    val displayName: String,
    val initial: String,
    val availability: RecipientAvailability
) {
    val isSelectable: Boolean
        get() = availability == RecipientAvailability.Direct || availability == RecipientAvailability.Relayed
}

internal enum class RecipientAvailability {
    Direct,
    Checking,
    Relayed,
    Nearby,
    Connecting,
    Offline,
    Blocked;

    @Composable
    fun label(): String = stringResource(
        when (this) {
            Direct -> R.string.recipient_status_direct
            Checking -> R.string.recipient_status_checking
            Relayed -> R.string.recipient_status_relayed
            Nearby -> R.string.recipient_status_nearby
            Connecting -> R.string.recipient_status_connecting
            Offline -> R.string.recipient_status_offline
            Blocked -> R.string.recipient_status_blocked
        }
    )

    @Composable
    fun color() = when (this) {
        Direct -> ResQTheme.colors.success
        Checking, Connecting -> ResQTheme.colors.warning
        Relayed -> MaterialTheme.colorScheme.primary
        Nearby -> MaterialTheme.colorScheme.secondary
        Offline -> MaterialTheme.colorScheme.onSurfaceVariant
        Blocked -> MaterialTheme.colorScheme.error
    }
}

internal fun recipientCandidates(state: ChatUiState): List<RecipientCandidate> {
    val names = linkedSetOf<String>().apply {
        state.connectedDevices.mapTo(this) { it.name }
        state.knownNodes.mapTo(this) { it.name }
        state.scannedDevices.mapTo(this) { it.name }
        state.blockedDeviceNames.mapTo(this) { it }
    }.filterNot(NodeIdentity::isPlaceholder)

    return names.map { name ->
        val link = state.connectedDevices.firstOrNull { NodeIdentity.matches(it.name, name) }
        val known = state.knownNodes.firstOrNull { NodeIdentity.matches(it.name, name) }
        val scanned = state.scannedDevices.firstOrNull { NodeIdentity.matches(it.name, name) }
        val blocked = state.blockedDeviceNames.any { NodeIdentity.matches(it, name) }
        val availability = when {
            blocked -> RecipientAvailability.Blocked
            link?.isPayloadReady == true && link.isPeerResponsive && !link.isProvisional -> RecipientAvailability.Direct
            link?.isPayloadReady == true -> RecipientAvailability.Checking
            link != null || scanned?.isConnecting == true -> RecipientAvailability.Connecting
            known?.isDirect == false -> RecipientAvailability.Relayed
            scanned != null -> RecipientAvailability.Nearby
            else -> RecipientAvailability.Offline
        }
        val displayName = NodeIdentity.displayNameOf(name).ifBlank { name }
        RecipientCandidate(
            name = name,
            displayName = displayName,
            initial = displayName.firstOrNull()?.uppercase() ?: "?",
            availability = availability
        )
    }.sortedWith(
        compareBy<RecipientCandidate> { it.availability.sortOrder() }
            .thenBy { it.displayName.lowercase() }
    )
}

private fun RecipientAvailability.sortOrder(): Int = when (this) {
    RecipientAvailability.Direct -> 0
    RecipientAvailability.Relayed -> 1
    RecipientAvailability.Checking, RecipientAvailability.Connecting -> 2
    RecipientAvailability.Nearby -> 3
    RecipientAvailability.Offline -> 4
    RecipientAvailability.Blocked -> 5
}

@Preview(showBackground = true, widthDp = 390, heightDp = 744)
@Composable
private fun NewMessagePreview() {
    TestResQMeshTheme {
        NewMessageModal(
            uiState = ChatUiState(),
            onDismiss = {},
            onRefresh = {},
            onNodeSelected = {}
        )
    }
}
