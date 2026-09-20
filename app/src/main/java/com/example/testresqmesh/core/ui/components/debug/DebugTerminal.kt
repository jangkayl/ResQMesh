package com.example.testresqmesh.core.ui.components.debug

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.utils.AppLogger
import com.example.testresqmesh.core.utils.TerminalLinkSnapshot
import com.example.testresqmesh.core.utils.TerminalLogCategory
import com.example.testresqmesh.core.utils.TerminalLogEntry
import com.example.testresqmesh.core.utils.TerminalLogLevel
import kotlinx.coroutines.launch

private enum class TerminalFilter(val label: String) {
    ALL("ALL"), CONNECTION("CONNECTION"), SYNC("SYNC"), TRANSPORT("TRANSPORT"),
    ROUTING("ROUTING"), SECURITY("SECURITY"), ALERTS("ALERTS")
}

@Composable
fun DebugTerminal(onOpenRadar: (() -> Unit)? = null) {
    val isVisible by AppLogger.isTerminalVisible.collectAsState()
    if (!isVisible) return

    val logs by AppLogger.logs.collectAsState()
    val links by AppLogger.links.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var currentFilter by remember { mutableStateOf(TerminalFilter.ALL) }
    var showVerbose by remember { mutableStateOf(false) }
    var unseenCount by remember { mutableIntStateOf(0) }
    var followLatest by remember { mutableStateOf(true) }

    BackHandler { AppLogger.hideTerminal() }

    val filteredLogs = remember(logs, currentFilter, showVerbose) {
        logs.asReversed().filter { entry ->
            (showVerbose || !entry.isVerbose) && currentFilter.matches(entry)
        }
    }
    val atLatest by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }
    val latestSequence = filteredLogs.firstOrNull()?.sequence

    // A user moving into older entries turns off live follow until they explicitly use Latest.
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }.distinctUntilChanged().collect { viewingOlderLogs ->
            if (viewingOlderLogs) followLatest = false
        }
    }

    // Newest logs are at index zero. No new event moves the list after live follow is paused.
    LaunchedEffect(latestSequence) {
        if (latestSequence == null) {
            unseenCount = 0
        } else if (followLatest) {
            listState.scrollToItem(0)
            unseenCount = 0
        } else {
            unseenCount += 1
        }
    }

    // Selecting a filter is an explicit user navigation action, so it may go to that filter's latest event.
    LaunchedEffect(currentFilter) {
        if (filteredLogs.isNotEmpty()) listState.scrollToItem(0)
        unseenCount = 0
    }

    fun jumpToLatest() {
        followLatest = true
        unseenCount = 0
        coroutineScope.launch { if (filteredLogs.isNotEmpty()) listState.animateScrollToItem(0) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.90f))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TerminalHeader(
                eventCount = filteredLogs.size,
                linkCount = links.size,
                showVerbose = showVerbose,
                onVerboseToggle = { showVerbose = !showVerbose },
                onClear = { AppLogger.clear(); unseenCount = 0 },
                onClose = AppLogger::hideTerminal,
                onOpenRadar = onOpenRadar
            )

            Divider(color = TerminalGreen.copy(alpha = 0.5f), thickness = 1.dp)
            LinkSummary(links)

            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(TerminalFilter.entries) { filter ->
                    FilterChip(
                        selected = currentFilter == filter,
                        onClick = {
                            currentFilter = filter
                            followLatest = false
                            unseenCount = 0
                        },
                        label = {
                            Text(filter.label, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TerminalGreen,
                            selectedLabelColor = Color.Black,
                            labelColor = TerminalGreen
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = currentFilter == filter,
                            borderColor = TerminalGreen,
                            selectedBorderColor = TerminalGreen
                        )
                    )
                }
            }

            if (filteredLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No ${currentFilter.label.lowercase()} events yet.",
                        color = TerminalGreen.copy(alpha = 0.75f),
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(filteredLogs, key = { it.sequence }) { entry ->
                        Text(
                            text = entry.displayText(),
                            color = colorFor(entry.level),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (logs.any { it.category == TerminalLogCategory.SYNC }) {
                Button(
                    onClick = {
                        // This is a one-time jump to the most recent Sync event, not live follow.
                        currentFilter = TerminalFilter.SYNC
                        followLatest = false
                        unseenCount = 0
                        coroutineScope.launch { listState.scrollToItem(0) }
                    },
                    colors = terminalButtonColors()
                ) { Text("LAST SYNC", fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
            }
            if (unseenCount > 0 || !atLatest) {
                Button(onClick = ::jumpToLatest, colors = terminalButtonColors()) {
                    Text(
                        if (unseenCount > 0) "LATEST ($unseenCount)" else "LATEST",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalHeader(
    eventCount: Int,
    linkCount: Int,
    showVerbose: Boolean,
    onVerboseToggle: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    onOpenRadar: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "RESQMESH // DIAGNOSTIC TERMINAL",
                color = TerminalGreen,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                "$eventCount events · $linkCount direct link records · newest first",
                color = TerminalGreen.copy(alpha = 0.75f),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onOpenRadar != null) {
                Text(
                    text = "RADAR",
                    color = TerminalGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .border(1.dp, TerminalGreen, RoundedCornerShape(4.dp))
                        .clickable(onClick = onOpenRadar)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = if (showVerbose) "DETAILS ON" else "DETAILS",
                color = TerminalGreen,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                modifier = Modifier
                    .border(1.dp, TerminalGreen, RoundedCornerShape(4.dp))
                    .clickable(onClick = onVerboseToggle)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Delete, contentDescription = "Clear terminal", tint = TerminalGreen)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close terminal", tint = TerminalGreen)
            }
        }
    }
    Text(
        if (showVerbose) "Details: heartbeat and SYSTEM relay traffic shown" else "Details: heartbeat and SYSTEM relay traffic hidden",
        color = TerminalGreen.copy(alpha = 0.65f),
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp,
        modifier = Modifier.padding(top = 2.dp)
    )
}

@Composable
private fun LinkSummary(links: List<TerminalLinkSnapshot>) {
    val readyCount = links.count { it.state == "READY" }
    Text(
        "DIRECT LINKS: $readyCount ready · ${links.size - readyCount} configuring",
        color = TerminalGreen,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 8.dp)
    )
    if (links.isEmpty()) {
        Text(
            "No direct GATT links recorded in this app session.",
            color = TerminalGreen.copy(alpha = 0.7f),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    } else {
        links.sortedWith(compareByDescending<TerminalLinkSnapshot> { it.state == "READY" }.thenBy { it.peerName })
            .take(3)
            .forEach { link ->
                Text(
                    "• ${link.peerName}  ${link.state}  ${link.role}#${link.generation}  ${link.transport}  ${TerminalLogEntry.shortEndpoint(link.endpoint)}",
                    color = if (link.state == "READY") TerminalGreen else TerminalAmber,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }
    }
}

private fun TerminalFilter.matches(entry: TerminalLogEntry): Boolean = when (this) {
    TerminalFilter.ALL -> true
    TerminalFilter.CONNECTION -> entry.category == TerminalLogCategory.CONNECTION
    TerminalFilter.SYNC -> entry.category == TerminalLogCategory.SYNC
    TerminalFilter.TRANSPORT -> entry.category == TerminalLogCategory.TRANSPORT
    TerminalFilter.ROUTING -> entry.category == TerminalLogCategory.ROUTING
    TerminalFilter.SECURITY -> entry.category == TerminalLogCategory.SECURITY
    TerminalFilter.ALERTS -> entry.level == TerminalLogLevel.WARN || entry.level == TerminalLogLevel.ERROR
}

private fun colorFor(level: TerminalLogLevel): Color = when (level) {
    TerminalLogLevel.DEBUG -> TerminalGreen.copy(alpha = 0.70f)
    TerminalLogLevel.INFO -> TerminalGreen
    TerminalLogLevel.WARN -> TerminalAmber
    TerminalLogLevel.ERROR -> TerminalRed
}

@Composable
private fun terminalButtonColors() = ButtonDefaults.buttonColors(
    containerColor = TerminalGreen,
    contentColor = Color.Black
)

private val TerminalGreen = Color(0xFF00FF00)
private val TerminalAmber = Color(0xFFFFC107)
private val TerminalRed = Color(0xFFFF5252)
