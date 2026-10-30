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
import edu.cit.resqmesh.core.logging.TerminalLogLevel
import edu.cit.resqmesh.core.ui.components.feedback.ResQEmptyState
import edu.cit.resqmesh.core.ui.components.feedback.ResQStatusChip
import edu.cit.resqmesh.core.ui.components.feedback.ResQStatusTone
import edu.cit.resqmesh.core.ui.theme.Spacing

/** Newest-first list of already redacted terminal entries, shared by the Errors and Logs tabs. */
@Composable
internal fun LogEntryList(entries: List<TerminalLogEntry>, emptyTitle: String, emptyMessage: String) {
    if (entries.isEmpty()) {
        ResQEmptyState(title = emptyTitle, message = emptyMessage)
        return
    }
    val newestFirst = entries.asReversed()
    LazyColumn(
        contentPadding = PaddingValues(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small)
    ) {
        items(newestFirst, key = { it.sequence }) { e ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(Spacing.Small)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                        ResQStatusChip(
                            e.level.name,
                            when (e.level) {
                                TerminalLogLevel.ERROR -> ResQStatusTone.Critical
                                TerminalLogLevel.WARN -> ResQStatusTone.Warning
                                else -> ResQStatusTone.Neutral
                            }
                        )
                        Text(e.timestampLabel, fontFamily = MonoFont, style = MaterialTheme.typography.labelMedium)
                        Text(
                            e.event,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (e.message.isNotBlank()) {
                        Text(
                            e.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
