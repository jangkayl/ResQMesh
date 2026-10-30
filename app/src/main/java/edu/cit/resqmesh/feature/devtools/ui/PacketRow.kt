package edu.cit.resqmesh.feature.devtools.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import edu.cit.resqmesh.core.logging.trace.TraceDir
import edu.cit.resqmesh.core.logging.trace.TraceDropReason
import edu.cit.resqmesh.core.logging.trace.TraceScope
import edu.cit.resqmesh.core.ui.components.feedback.ResQStatusChip
import edu.cit.resqmesh.core.ui.components.feedback.ResQStatusTone
import edu.cit.resqmesh.core.ui.theme.Spacing
import edu.cit.resqmesh.feature.devtools.model.TraceRow

/** Two-to-three line packet summary card. */
@Composable
internal fun PacketRow(row: TraceRow, names: Map<String, String>, onClick: () -> Unit) {
    val t = row.last
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(Spacing.Small), verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ResQStatusChip(if (t.dir == TraceDir.OUT) "OUT" else "IN", ResQStatusTone.Information)
                val scopeTone = if (t.scope == TraceScope.PRIVATE) ResQStatusTone.Warning else ResQStatusTone.Neutral
                ResQStatusChip(t.scope.name, scopeTone)
                ResQStatusChip(t.type.name + if (row.count > 1) " ×${row.count}" else "", ResQStatusTone.Neutral)
                Text(
                    formatTime(t.epochMs),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = MonoFont,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
            }
            Text(
                "${peerLabel(t.sourceId, names)} → ${destLabel(t, names)} · via ${peerLabel(t.nextHop, names)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${t.packetKey.ifEmpty { DASH }} · ${t.bytes} B · ${t.transport ?: DASH} · ${hopLabel(t)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = MonoFont,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val dropped = t.dropReason != TraceDropReason.NONE
                val status = if (dropped) "${t.stage.name} ${t.dropReason.name}" else t.stage.name
                ResQStatusChip(status, stageTone(t))
            }
        }
    }
}
