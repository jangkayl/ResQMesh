package edu.cit.resqmesh.feature.devtools.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import edu.cit.resqmesh.core.ui.theme.Spacing
import edu.cit.resqmesh.feature.devtools.model.LinkGroup
import edu.cit.resqmesh.feature.devtools.viewmodel.DevToolsSnapshot

/** Counts only from data already collected; no extra polling of the radio. */
@Composable
internal fun OverviewTab(snapshot: DevToolsSnapshot, groups: List<LinkGroup>, unseenErrors: Int) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.Medium)) {
        val paths = groups.flatMap { it.paths }
        SectionTitle("Peers")
        LabeledValue("Direct peers", groups.size.toString())
        LabeledValue("Peers with a READY path", groups.count { it.readyCount > 0 }.toString())
        SectionTitle("Paths")
        LabeledValue("Live paths", paths.size.toString())
        LabeledValue("Payload READY", paths.count { it.ready }.toString())
        val connectedNotReady = paths.count { it.state == "DISCOVERING" || it.state == "CONFIGURING" }
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
