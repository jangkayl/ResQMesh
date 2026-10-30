package edu.cit.resqmesh.feature.devtools.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import edu.cit.resqmesh.core.logging.trace.PacketTrace
import edu.cit.resqmesh.core.ui.components.feedback.ResQStatusChip
import edu.cit.resqmesh.core.ui.theme.Spacing

/** All fields of one packet plus this phone's raw events that share its packet key. */
@Composable
internal fun PacketDetail(
    packet: PacketTrace,
    timeline: List<PacketTrace>,
    names: Map<String, String>,
    onClose: () -> Unit
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.Medium)) {
        Row {
            Text("Packet detail", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Back") }
        }
        LabeledValue("Packet", packet.packetKey.ifEmpty { DASH })
        LabeledValue("Direction", packet.dir.name, mono = false)
        LabeledValue("Scope", packet.scope.name, mono = false)
        LabeledValue("Type", packet.type.name, mono = false)
        LabeledValue("Stage", packet.stage.name, mono = false)
        LabeledValue("Source", peerLabel(packet.sourceId, names), mono = false)
        LabeledValue("Destination", destLabel(packet, names), mono = false)
        LabeledValue("Next hop", peerLabel(packet.nextHop, names), mono = false)
        LabeledValue("Hop / TTL", hopLabel(packet))
        LabeledValue("Route length", packet.pathLen?.toString() ?: DASH)
        LabeledValue("Bytes", packet.bytes.toString())
        LabeledValue("Transport", packet.transport ?: DASH)
        LabeledValue("Drop reason", packet.dropReason.name, mono = false)
        SectionTitle("Timeline (this phone only)")
        timeline.forEach { e ->
            Row(Modifier.padding(vertical = Spacing.ExtraSmall)) {
                Text(
                    formatTime(e.epochMs),
                    fontFamily = MonoFont,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(LABEL_WEIGHT)
                )
                ResQStatusChip("${e.dir.name} ${e.stage.name}", stageTone(e))
            }
        }
    }
}
