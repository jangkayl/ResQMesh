package edu.cit.resqmesh.feature.devtools.ui

import androidx.compose.runtime.Composable
import edu.cit.resqmesh.core.logging.TerminalLogEntry

@Composable
internal fun LogsTab(logs: List<TerminalLogEntry>) {
    LogEntryList(logs, "No logs yet", "Application log lines appear here while developer mode is on.")
}
