package edu.cit.resqmesh.feature.profile.ui

import edu.cit.resqmesh.feature.profile.ui.offlinemap.MapPackageOverviewCard
import edu.cit.resqmesh.feature.profile.ui.offlinemap.PackageTechnicalDetailsCard
import edu.cit.resqmesh.feature.profile.ui.offlinemap.TacticalDeploymentGuidanceCard
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import edu.cit.resqmesh.core.ui.components.layout.ResQAuroraBackground
import edu.cit.resqmesh.core.ui.theme.ResQSize
import edu.cit.resqmesh.core.ui.theme.SignalRed
import edu.cit.resqmesh.core.ui.theme.Spacing
import edu.cit.resqmesh.feature.profile.viewmodel.OfflineMapViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun OfflineMapSettingsScreen(
    onBack: () -> Unit,
    returnToSosAlert: Boolean = false,
    viewModel: OfflineMapViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = SignalRed) },
            title = { Text("Delete Offline Map?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Deleting the Cebu offline vector map will revert the SOS map to mapless coordinate fallback until downloaded again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePackage()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SignalRed)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    ResQAuroraBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(ResQSize.MinimumTouchTarget)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (returnToSosAlert) "Back to SOS alert" else "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Spacing.Small)
                ) {
                    Text(
                        text = "Offline Maps",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (returnToSosAlert) "Return to this SOS alert after setup"
                        else "Local vector packages for disconnected operations",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { viewModel.refreshStatus() },
                    modifier = Modifier.size(ResQSize.MinimumTouchTarget)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.Large, vertical = Spacing.Small)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
            ) {
                // Main Map Package Card
                MapPackageOverviewCard(
                    state = state,
                    onDownload = { viewModel.startDownload(forceWifiBypass = false) },
                    onDownloadCellular = { viewModel.startDownload(forceWifiBypass = true) },
                    onCancel = { viewModel.cancelDownload() },
                    onDelete = { showDeleteConfirm = true },
                    onRetry = { viewModel.retry() }
                )

                // Package Details & Verification Card
                PackageTechnicalDetailsCard(state = state)

                // Tactical Deployment Guidance Card
                TacticalDeploymentGuidanceCard()
            }
        }
    }
}
