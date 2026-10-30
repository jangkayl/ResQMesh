package edu.cit.resqmesh.feature.devtools.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import edu.cit.resqmesh.feature.devtools.model.TraceRow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import edu.cit.resqmesh.core.logging.trace.PacketTrace
import edu.cit.resqmesh.core.logging.trace.TraceDir
import edu.cit.resqmesh.core.logging.trace.TraceScope
import edu.cit.resqmesh.core.logging.trace.TraceType
import edu.cit.resqmesh.core.ui.components.feedback.ResQEmptyState
import edu.cit.resqmesh.core.ui.theme.Spacing
import edu.cit.resqmesh.feature.devtools.model.TraceCollapser
import edu.cit.resqmesh.feature.devtools.model.TraceFilter

private val transports = listOf("GATT", "L2CAP", "TRANSFER")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChips(
    filter: TraceFilter,
    onDir: (TraceDir) -> Unit,
    onScope: (TraceScope) -> Unit,
    onTransport: (String) -> Unit,
    onType: (TraceType) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.Medium),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
    ) {
        items(TraceDir.entries) { d -> FilterChip(filter.dir == d, { onDir(d) }, { Text(d.name) }) }
        items(listOf(TraceScope.PUBLIC, TraceScope.PRIVATE)) { s ->
            FilterChip(filter.scope == s, { onScope(s) }, { Text(s.name) })
        }
        items(transports) { tr -> FilterChip(filter.transport == tr, { onTransport(tr) }, { Text(tr) }) }
        items(TraceType.entries) { ty -> FilterChip(filter.type == ty, { onType(ty) }, { Text(ty.name) }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PacketsTab(
    packets: List<PacketTrace>,
    names: Map<String, String>,
    paused: Boolean,
    onPausedChange: (Boolean) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var dir by remember { mutableStateOf<TraceDir?>(null) }
    var scope by remember { mutableStateOf<TraceScope?>(null) }
    var type by remember { mutableStateOf<TraceType?>(null) }
    var transport by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<PacketTrace?>(null) }

    val detail = selected
    if (detail != null) {
        val timeline = packets.filter { it.packetKey == detail.packetKey && detail.packetKey.isNotEmpty() }
        PacketDetail(detail, timeline, names) { selected = null }
        return
    }

    val filter = TraceFilter(query, dir, scope, type, transport)
    val rows by produceState(emptyList<TraceRow>(), packets, filter, names) {
        value = withContext(Dispatchers.Default) {
            TraceCollapser.collapse(filter.apply(packets) { names[it] }).asReversed()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.Medium)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search id, peer, type") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onPausedChange(!paused) }) {
                Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, if (paused) "Resume" else "Pause")
            }
        }
        FilterChips(
            filter,
            onDir = { dir = if (dir == it) null else it },
            onScope = { scope = if (scope == it) null else it },
            onTransport = { transport = if (transport == it) null else it },
            onType = { type = if (type == it) null else it }
        )
        if (rows.isEmpty()) {
            ResQEmptyState(
                title = if (filter.isActive) "No matching packets" else "No packets yet",
                message = if (paused) "Paused." else "Traffic appears here while developer mode is on."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(Spacing.Medium),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
                items(rows, key = { it.first.seq }) { row -> PacketRow(row, names) { selected = row.last } }
            }
        }
    }
}
