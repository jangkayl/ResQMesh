package edu.cit.resqmesh.feature.devtools.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import edu.cit.resqmesh.core.logging.TerminalLogEntry
import edu.cit.resqmesh.core.ui.components.feedback.ResQEmptyState
import edu.cit.resqmesh.core.ui.components.feedback.ResQStatusChip
import edu.cit.resqmesh.core.ui.components.feedback.ResQStatusTone
import edu.cit.resqmesh.core.ui.theme.ResQTheme
import edu.cit.resqmesh.core.ui.theme.Spacing
import edu.cit.resqmesh.feature.devtools.model.LinkGroup
import edu.cit.resqmesh.feature.devtools.model.LinkPath

/** Radio state and payload readiness are separate: only READY means payloads may flow. */
internal fun linkStateChip(state: String): Pair<String, ResQStatusTone> = when (state) {
    "READY" -> "Payload READY" to ResQStatusTone.Success
    "DISCOVERING", "CONFIGURING" -> "Radio CONNECTED, not ready" to ResQStatusTone.Information
    "CONNECTING" -> "Connecting" to ResQStatusTone.Warning
    else -> state to ResQStatusTone.Neutral
}

/** One card per peer; alternate live paths stay visible under it because they are real links. */
@Composable
internal fun LinksTab(groups: List<LinkGroup>) {
    if (groups.isEmpty()) {
        ResQEmptyState(title = "No direct links", message = "Connected BLE links appear here.")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small)
    ) {
        items(groups, key = { it.key }) { group ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    Modifier.padding(Spacing.Small),
                    verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)
                ) {
                    Text(group.title, style = MaterialTheme.typography.titleSmall)
                    group.paths.forEach { PathRow(it) }
                }
            }
        }
    }
}

@Composable
private fun PathRow(path: LinkPath) {
    val (label, tone) = linkStateChip(path.state)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        Text(
            "${path.role}#${path.generation} · ${path.transport} · ${TerminalLogEntry.shortEndpoint(path.endpoint)}",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = MonoFont,
            color = if (path.ready) ResQTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        ResQStatusChip(label, tone)
    }
}
