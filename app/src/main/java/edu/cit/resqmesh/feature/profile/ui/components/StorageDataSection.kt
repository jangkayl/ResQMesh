package edu.cit.resqmesh.feature.profile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import edu.cit.resqmesh.core.ui.theme.ResQTheme
import edu.cit.resqmesh.core.ui.theme.Spacing
import edu.cit.resqmesh.feature.profile.viewmodel.StorageSettingsViewModel
import edu.cit.resqmesh.feature.profile.viewmodel.formatStorageBytes

private const val RESET_CONFIRM_WORD = "RESET"
private const val CLEAR_CACHE_TEXT =
    "Removes temporary voice recordings and image captures. Messages, contacts, keys and offline maps are kept."
private const val RESETTING_TEXT =
    "Deleting all data on this phone. ResQMesh will close in a moment and start again at setup."
private const val RESET_SOS_TEXT =
    "Your SOS is still active. End it first so nearby phones are not left with an alert you can no longer update."
private const val RESET_DELETES_TEXT =
    "This permanently deletes everything on this phone: messages, incidents, SOS history, contacts, " +
        "blocked users, settings, encryption keys and offline maps. ResQMesh will close and start again at setup."
private const val RESET_IDENTITY_TEXT =
    "You will get a new node ID. Nearby phones will see you as a new contact and may still hold your old key. " +
        "Messages this phone is relaying for others will not be delivered."

@Composable
internal fun StorageDataSection(viewModel: StorageSettingsViewModel) {
    val state by viewModel.state.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        SettingsCard {
            SettingRow(
                icon = Icons.Default.CleaningServices,
                title = "Clear cache",
                subtitle = state.cacheBytes?.let { "${formatStorageBytes(it)} of temporary voice and image files" }
                    ?: "Calculating…",
                onClick = { if (!state.busy) confirmClear = true }
            )
            DividerLine()
            SettingRow(
                icon = Icons.Default.DeleteForever,
                title = "Reset app",
                subtitle = "Delete all messages, identity, keys and maps on this phone",
                onClick = { if (!state.busy) confirmReset = true }
            )
        }
        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = Spacing.Small)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }

    if (confirmClear) {
        ClearCacheDialog(
            onConfirm = {
                confirmClear = false
                viewModel.clearCache()
            },
            onDismiss = { confirmClear = false }
        )
    }

    if (state.resetting) ResettingDialog()

    if (confirmReset) {
        ResetAppDialog(
            sosActive = state.ownSosActive,
            onConfirm = {
                confirmReset = false
                viewModel.resetApp()
            },
            onDismiss = { confirmReset = false }
        )
    }
}

@Composable
private fun ResettingDialog() {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        icon = { CircularProgressIndicator(modifier = Modifier.size(32.dp)) },
        title = { Text("Resetting ResQMesh…", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                RESETTING_TEXT,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
            )
        },
        confirmButton = {}
    )
}

@Composable
private fun ClearCacheDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear cache?", fontWeight = FontWeight.Bold) },
        text = { Text(CLEAR_CACHE_TEXT) },
        confirmButton = { Button(onClick = onConfirm) { Text("Clear") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ResetAppDialog(sosActive: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset ResQMesh?", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (sosActive) {
                    Text(RESET_SOS_TEXT, color = ResQTheme.colors.sos, fontWeight = FontWeight.Bold)
                }
                Text(RESET_DELETES_TEXT)
                Text(RESET_IDENTITY_TEXT)
                Text("Type $RESET_CONFIRM_WORD to confirm.", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it.take(RESET_CONFIRM_WORD.length + 2) },
                    singleLine = true,
                    enabled = !sosActive,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !sosActive && typed.trim() == RESET_CONFIRM_WORD,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ResQTheme.colors.sos,
                    contentColor = ResQTheme.colors.onSos
                )
            ) { Text("Delete everything") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
