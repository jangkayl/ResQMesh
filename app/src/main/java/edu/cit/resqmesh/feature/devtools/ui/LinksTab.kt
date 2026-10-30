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
import edu.cit.resqmesh.core.logging.TerminalLinkSnapshot
import edu.cit.resqmesh.core.logging.TerminalLogEntry
import edu.cit.resqmesh.core.ui.components.feedback.ResQEmptyState
import edu.cit.resqmesh.core.ui.components.feedback.ResQStatusChip
import edu.cit.resqmesh.core.ui.components.feedback.ResQStatusTone
import edu.cit.resqmesh.core.ui.theme.Spacing

/** Radio state and payload readiness are separate: only READY means payloads may flow. */
internal fun linkStateChip(state: String): Pair<String, ResQStatusTone> = when (state) {
    "READY" -> "Payload READY" to ResQStatusTone.Success
    "DISCOVERING", "CONFIGURING" -> "Radio CONNECTED, not ready" to ResQStatusTone.Information
    "CONNECTING" -> "Connecting" to ResQStatusTone.Warning
    else -> state to ResQStatusTone.Neutral
}

@Composable
internal fun LinksTab(links: List<TerminalLinkSnapshot>) {
    if (links.isEmpty()) {
        ResQEmptyState(title = "No direct links", message = "Connected BLE links appear here.")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small)
    ) {
        items(links, key = { "${it.endpoint}/${it.role}/${it.generation}" }) { link ->
            val (label, tone) = linkStateChip(link.state)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    Modifier.padding(Spacing.Small),
                    verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                        Text(link.peerName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        ResQStatusChip(label, tone)
                    }
                    Text(
                        "${link.role}#${link.generation} · ${link.transport} · " +
                            TerminalLogEntry.shortEndpoint(link.endpoint),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = MonoFont,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
