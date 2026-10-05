package com.example.testresqmesh.app.navigation.overlays

import com.example.testresqmesh.app.navigation.overlays.FloatingDeveloperBadge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.core.ui.components.debug.DebugTerminal
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun BoxScope.MainApplicationOverlays(
    isDeveloperMode: Boolean,
    hasIncomingSos: Boolean,
    hasMapAlert: Boolean,
    hasOwnSos: Boolean,
    isSOSActive: Boolean,
    publicSendSnackbar: androidx.compose.material3.SnackbarHostState,
    onToggleTerminal: () -> Unit,
    onToggleRadar: () -> Unit,
    onOpenRadar: () -> Unit
) {
    if (isDeveloperMode && !hasIncomingSos && !hasMapAlert && !hasOwnSos && !isSOSActive) {
        FloatingDeveloperBadge(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 12.dp),
            onClick = {
                onToggleTerminal()
            },
            onLongClick = {
                onToggleRadar()
            }
        )
    }

    androidx.compose.material3.SnackbarHost(
        hostState = publicSendSnackbar,
        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 88.dp)
    )

    DebugTerminal(
        onOpenRadar = {
            onOpenRadar()
        }
    )
}
