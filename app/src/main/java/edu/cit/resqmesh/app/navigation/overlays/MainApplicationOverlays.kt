package edu.cit.resqmesh.app.navigation.overlays

import edu.cit.resqmesh.app.navigation.overlays.FloatingDeveloperBadge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import edu.cit.resqmesh.core.logging.AppLogger
import edu.cit.resqmesh.feature.devtools.model.DevToolsTab
import edu.cit.resqmesh.feature.devtools.ui.DevToolsScreen
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api

@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun BoxScope.MainApplicationOverlays(
    isDeveloperMode: Boolean,
    hasIncomingSos: Boolean,
    hasMapAlert: Boolean,
    hasOwnSos: Boolean,
    isSOSActive: Boolean,
    publicSendSnackbar: androidx.compose.material3.SnackbarHostState,
    devToolsTab: DevToolsTab?,
    openDevTools: (DevToolsTab) -> Unit,
    closeDevTools: () -> Unit
) {
    val devToolsAvailable = isDeveloperMode && !hasIncomingSos && !hasMapAlert && !hasOwnSos && !isSOSActive
    val unseenErrors by AppLogger.errorCount.collectAsState()
    // Leaving the available state (e.g. an SOS) closes the console so it does not reappear afterwards.
    LaunchedEffect(devToolsAvailable) { if (!devToolsAvailable) closeDevTools() }
    if (devToolsAvailable) {
        FloatingDeveloperBadge(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 12.dp),
            showErrorDot = unseenErrors > 0,
            onClick = { openDevTools(DevToolsTab.OVERVIEW) },
            onLongClick = { openDevTools(DevToolsTab.ERRORS) }
        )
    }

    androidx.compose.material3.SnackbarHost(
        hostState = publicSendSnackbar,
        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 88.dp)
    )

    if (devToolsAvailable && devToolsTab != null) {
        DevToolsScreen(initialTab = devToolsTab, onClose = closeDevTools)
    }
}
