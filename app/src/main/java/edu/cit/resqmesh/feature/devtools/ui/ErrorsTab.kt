package edu.cit.resqmesh.feature.devtools.ui

import androidx.compose.runtime.Composable
import edu.cit.resqmesh.core.logging.TerminalLogEntry

@Composable
internal fun ErrorsTab(errors: List<TerminalLogEntry>) {
    LogEntryList(errors, "No errors", "Warnings and errors from the mesh stack appear here.")
}
