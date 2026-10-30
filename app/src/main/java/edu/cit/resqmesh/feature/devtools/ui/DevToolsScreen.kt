package edu.cit.resqmesh.feature.devtools.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import edu.cit.resqmesh.core.logging.AppLogger
import edu.cit.resqmesh.feature.devtools.model.DevToolsTab
import edu.cit.resqmesh.feature.devtools.viewmodel.DevToolsViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private fun DevToolsTab.icon(): ImageVector = when (this) {
    DevToolsTab.OVERVIEW -> Icons.Default.Dashboard
    DevToolsTab.LINKS -> Icons.Default.Link
    DevToolsTab.PACKETS -> Icons.Default.SwapVert
    DevToolsTab.VOICE -> Icons.Default.Mic
    DevToolsTab.ERRORS -> Icons.Default.ErrorOutline
    DevToolsTab.LOGS -> Icons.Default.Notes
}

@Composable
private fun DevToolsBottomBar(selected: DevToolsTab, onSelect: (DevToolsTab) -> Unit) {
    NavigationBar {
        DevToolsTab.entries.forEach { t ->
            NavigationBarItem(
                selected = selected == t,
                onClick = { onSelect(t) },
                icon = { Icon(t.icon(), t.label) },
                label = { Text(t.label, maxLines = 1) }
            )
        }
    }
}

/** Full-screen developer console. Back and the close button both call [onClose]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevToolsScreen(initialTab: DevToolsTab, onClose: () -> Unit, viewModel: DevToolsViewModel = koinViewModel()) {
    var tab by remember(initialTab) { mutableStateOf(initialTab) }
    val snapshot by viewModel.snapshot.collectAsState()
    val names by viewModel.names.collectAsState()
    val paused by viewModel.paused.collectAsState()
    val groups by viewModel.linkGroups.collectAsState()
    val unseenErrors by AppLogger.errorCount.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resolver = LocalContext.current.contentResolver

    BackHandler(onBack = onClose)

    // Pause never outlives the console.
    DisposableEffect(viewModel) { onDispose { viewModel.setPaused(false) } }

    // Showing Errors counts as seeing them; new errors while it stays open are cleared too.
    LaunchedEffect(tab, unseenErrors) {
        if (tab == DevToolsTab.ERRORS && unseenErrors > 0) viewModel.markErrorsSeen()
    }

    fun launchExport(csv: Boolean, uri: android.net.Uri?) {
        if (uri == null) return
        scope.launch { viewModel.export(resolver, uri, csv)?.let { snackbar.showSnackbar(it) } }
    }
    val textLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) {
        launchExport(false, it)
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {
        launchExport(true, it)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Developer console") },
                    navigationIcon = {
                        IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close developer console") }
                    },
                    actions = {
                        TextButton(onClick = { textLauncher.launch("resqmesh-trace.txt") }) { Text("Text") }
                        TextButton(onClick = { csvLauncher.launch("resqmesh-trace.csv") }) { Text("CSV") }
                    }
                )
            },
            bottomBar = { DevToolsBottomBar(tab) { tab = it } },
            snackbarHost = { SnackbarHost(snackbar) }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (tab) {
                    DevToolsTab.OVERVIEW -> OverviewTab(snapshot, groups, unseenErrors)
                    DevToolsTab.LINKS -> LinksTab(groups)
                    DevToolsTab.PACKETS -> PacketsTab(snapshot.packets, names, paused, viewModel::setPaused)
                    DevToolsTab.VOICE -> VoiceTab(snapshot.voice, names, paused)
                    DevToolsTab.ERRORS -> ErrorsTab(snapshot.errors, paused)
                    DevToolsTab.LOGS -> LogsTab(snapshot.logs, paused)
                }
            }
        }
    }
}
