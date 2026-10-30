package edu.cit.resqmesh.feature.devtools.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import edu.cit.resqmesh.core.logging.TerminalLinkSnapshot
import edu.cit.resqmesh.core.ui.theme.Spacing
import edu.cit.resqmesh.feature.devtools.viewmodel.DevToolsSnapshot

/** Counts only from data already collected; no extra polling of the radio. */
@Composable
internal fun OverviewTab(snapshot: DevToolsSnapshot, links: List<TerminalLinkSnapshot>, unseenErrors: Int) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.Medium)) {
        SectionTitle("Links")
        LabeledValue("Direct links", links.size.toString())
        LabeledValue("Payload READY", links.count { it.state == "READY" }.toString())
        val connectedNotReady = links.count { it.state == "DISCOVERING" || it.state == "CONFIGURING" }
        LabeledValue("Radio connected, not ready", connectedNotReady.toString())
        SectionTitle("Traffic (buffered)")
        LabeledValue("Packet events", snapshot.packets.size.toString())
        LabeledValue("Voice events", snapshot.voice.size.toString())
        LabeledValue("Log lines", snapshot.logs.size.toString())
        SectionTitle("Problems")
        LabeledValue("Warnings and errors", snapshot.errors.size.toString())
        LabeledValue("New errors since last viewed", unseenErrors.toString())
        SectionTitle("Queues")
        LabeledValue("Queue depth", "not sampled yet", mono = false)
    }
}
