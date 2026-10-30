package edu.cit.resqmesh.feature.devtools.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import edu.cit.resqmesh.core.logging.trace.PacketTrace
import edu.cit.resqmesh.core.ui.components.feedback.ResQEmptyState
import edu.cit.resqmesh.core.ui.theme.Spacing
import edu.cit.resqmesh.feature.devtools.model.TraceRow

@Composable
internal fun VoiceTab(voice: List<PacketTrace>, names: Map<String, String>, paused: Boolean) {
    val listState = rememberLazyListState()
    if (voice.isEmpty()) {
        ResQEmptyState(
            title = "No voice traces",
            message = "Voice-note transfer and record/playback events are not instrumented yet."
        )
        return
    }
    LiveListBox(
        listState,
        voice.last().seq,
        paused,
        newerThan = { key -> voice.asReversed().takeWhile { it.seq > key }.size }
    ) { listModifier ->
        LazyColumn(
            modifier = listModifier,
            state = listState,
            contentPadding = PaddingValues(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            items(voice.asReversed(), key = { it.seq }) { t -> PacketRow(TraceRow(t, t, 1), names) {} }
        }
    }
}
