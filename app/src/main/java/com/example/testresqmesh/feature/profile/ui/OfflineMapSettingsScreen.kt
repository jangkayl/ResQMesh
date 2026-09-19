package com.example.testresqmesh.feature.profile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.map.model.MapPackageStatus
import com.example.testresqmesh.core.ui.components.layout.ResQAuroraBackground
import com.example.testresqmesh.core.ui.theme.ResQSize
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.SafetyOrange
import com.example.testresqmesh.core.ui.theme.SignalAmber
import com.example.testresqmesh.core.ui.theme.SignalGreen
import com.example.testresqmesh.core.ui.theme.SignalRed
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.feature.profile.viewmodel.OfflineMapUiState
import com.example.testresqmesh.feature.profile.viewmodel.OfflineMapViewModel
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

@Composable
fun OfflineMapSettingsScreen(
    onBack: () -> Unit,
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
                        contentDescription = "Back",
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
                        text = "Local vector packages for disconnected operations",
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

@Composable
private fun MapPackageOverviewCard(
    state: OfflineMapUiState,
    onDownload: () -> Unit,
    onDownloadCellular: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
        ) {
            // Title & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.catalogEntry.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = state.catalogEntry.coverageArea,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                StatusBadge(status = state.packageStatus, isUpdateAvailable = state.isUpdateAvailable)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // State-Specific Action & Progress Area
            when {
                state.wifiRequiredWarning -> {
                    WifiRequiredSection(onRetry = onDownload, onBypass = onDownloadCellular)
                }
                state.insufficientStorageError -> {
                    InsufficientStorageSection(
                        availableBytes = state.availableStorageBytes,
                        requiredBytes = state.catalogEntry.estimatedDownloadSizeBytes * 2,
                        onRetry = onRetry
                    )
                }
                state.corruptPackageError -> {
                    CorruptPackageSection(onRetry = onRetry)
                }
                state.packageStatus is MapPackageStatus.Downloading -> {
                    DownloadingProgressSection(
                        downloading = state.packageStatus,
                        onCancel = onCancel
                    )
                }
                state.packageStatus is MapPackageStatus.Verifying -> {
                    VerifyingProgressSection(verifying = state.packageStatus)
                }
                state.packageStatus is MapPackageStatus.Extracting -> {
                    ExtractingProgressSection(extracting = state.packageStatus)
                }
                state.packageStatus is MapPackageStatus.Installed -> {
                    InstalledActiveSection(
                        installed = state.packageStatus,
                        isUpdateAvailable = state.isUpdateAvailable,
                        targetVersion = state.catalogEntry.targetVersion,
                        onUpdate = onDownload,
                        onDelete = onDelete
                    )
                }
                state.packageStatus is MapPackageStatus.Error -> {
                    GenericErrorSection(
                        message = state.packageStatus.message,
                        onRetry = onRetry
                    )
                }
                else -> {
                    // Not Installed
                    NotInstalledSection(
                        estimatedBytes = state.catalogEntry.estimatedDownloadSizeBytes,
                        onDownload = onDownload
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(
    status: MapPackageStatus,
    isUpdateAvailable: Boolean
) {
    val (label, bg, fg) = when {
        isUpdateAvailable -> Triple("UPDATE AVAILABLE", SignalAmber.copy(alpha = 0.15f), SignalAmber)
        status is MapPackageStatus.Installed -> Triple("ACTIVE & VERIFIED", SignalGreen.copy(alpha = 0.15f), SignalGreen)
        status is MapPackageStatus.Downloading -> Triple("DOWNLOADING", SafetyOrange.copy(alpha = 0.15f), SafetyOrange)
        status is MapPackageStatus.Verifying -> Triple("VERIFYING", SafetyOrange.copy(alpha = 0.15f), SafetyOrange)
        status is MapPackageStatus.Extracting -> Triple("EXTRACTING", SafetyOrange.copy(alpha = 0.15f), SafetyOrange)
        status is MapPackageStatus.Error -> Triple("ERROR", SignalRed.copy(alpha = 0.15f), SignalRed)
        else -> Triple("NOT INSTALLED", MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f), MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
        border = BorderStroke(1.dp, fg)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = fg
        )
    }
}

@Composable
private fun NotInstalledSection(
    estimatedBytes: Long,
    onDownload: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
        Text(
            text = "No offline map installed. SOS screens will operate in mapless coordinate fallback mode until installed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = onDownload,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange)
        ) {
            Icon(Icons.Default.CloudDownload, contentDescription = null)
            Spacer(Modifier.width(Spacing.Small))
            Text(
                text = "Download Cebu Map (${formatBytes(estimatedBytes)})",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

@Composable
private fun WifiRequiredSection(
    onRetry: () -> Unit,
    onBypass: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SignalAmber.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, SignalAmber.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WifiOff, contentDescription = null, tint = SignalAmber)
                Spacer(Modifier.width(Spacing.Small))
                Text(
                    text = "Wi-Fi Connection Required",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Connecting to Wi-Fi prevents large data usage on cellular links. Connect to Wi-Fi to proceed, or download anyway.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
                OutlinedButton(
                    onClick = onBypass,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cellular")
                }
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange)
                ) {
                    Text("Retry Wi-Fi")
                }
            }
        }
    }
}

@Composable
private fun InsufficientStorageSection(
    availableBytes: Long,
    requiredBytes: Long,
    onRetry: () -> Unit
) {
    val requiredMb = (requiredBytes / (1024 * 1024)).coerceAtLeast(1)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SignalRed.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, SignalRed.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storage, contentDescription = null, tint = SignalRed)
                Spacer(Modifier.width(Spacing.Small))
                Text(
                    text = "Insufficient Storage Space",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = SignalRed
                )
            }
            Text(
                text = "Available: ${formatBytes(availableBytes)}. Approximately $requiredMb MB required for download and vector extraction safety margins.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text("Recheck Storage", color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun CorruptPackageSection(onRetry: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SignalRed.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, SignalRed.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SecurityUpdateWarning, contentDescription = null, tint = SignalRed)
                Spacer(Modifier.width(Spacing.Small))
                Text(
                    text = "Integrity Verification Failed",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = SignalRed
                )
            }
            Text(
                text = "Digital signature or SHA-256 hash check failed. Damaged staging files have been discarded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange)
            ) {
                Text("Retry Clean Download")
            }
        }
    }
}

@Composable
private fun GenericErrorSection(
    message: String,
    onRetry: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SignalRed.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, SignalRed.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            Text(
                text = "Download Error",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = SignalRed
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange)
            ) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun DownloadingProgressSection(
    downloading: MapPackageStatus.Downloading,
    onCancel: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Downloading vector archive...",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${downloading.progressPercent}%",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = SafetyOrange
            )
        }

        LinearProgressIndicator(
            progress = { (downloading.progressPercent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = SafetyOrange,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${formatBytes(downloading.bytesDownloaded)} of ${formatBytes(downloading.totalBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onCancel) {
                Text("Cancel", color = SignalRed)
            }
        }
    }
}

@Composable
private fun VerifyingProgressSection(verifying: MapPackageStatus.Verifying) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = SafetyOrange,
            strokeWidth = 2.dp
        )
        Column {
            Text(
                text = "Verifying Package Integrity",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = verifying.stage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExtractingProgressSection(extracting: MapPackageStatus.Extracting) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = SafetyOrange,
            strokeWidth = 2.dp
        )
        Column {
            Text(
                text = "Extracting Assets",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Unpacking PMTiles and tactical vector styles...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InstalledActiveSection(
    installed: MapPackageStatus.Installed,
    isUpdateAvailable: Boolean,
    targetVersion: Int,
    onUpdate: () -> Unit,
    onDelete: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SignalGreen, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(Spacing.Small))
            Column {
                Text(
                    text = "Package Active (Version ${installed.manifest.version})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Local PMTiles & styles ready for MapLibre Native SOS display.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (isUpdateAvailable) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SignalAmber.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, SignalAmber.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.Medium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "New Update Available: v$targetVersion",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Latest road network and obstacle updates.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onUpdate,
                        colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange)
                    ) {
                        Text("Update")
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = SignalRed),
            border = BorderStroke(1.dp, SignalRed.copy(alpha = 0.4f))
        ) {
            Icon(Icons.Default.DeleteOutline, contentDescription = null)
            Spacer(Modifier.width(Spacing.Small))
            Text("Delete Offline Map Package")
        }
    }
}

@Composable
private fun PackageTechnicalDetailsCard(state: OfflineMapUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            Text(
                text = "PACKAGE SPECIFICATIONS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = SafetyOrange,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(4.dp))
            SpecRow("Coverage", state.catalogEntry.coverageArea)
            SpecRow("Zoom Levels", "${state.catalogEntry.minZoom} to ${state.catalogEntry.maxZoom}")
            SpecRow("Renderer", "MapLibre Native Android (Local PMTiles)")
            SpecRow("Data Attribution", "© OpenStreetMap contributors, ODbL")
            SpecRow("Integrity Engine", "Ed25519 Signatures + SHA-256 Checksums")
            SpecRow("Network Access", "Zero runtime network requests after install")
        }
    }
}

@Composable
private fun TacticalDeploymentGuidanceCard() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(Spacing.Medium),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(Spacing.Medium))
            Text(
                text = "ResQMesh offline vector maps are pre-compiled and signed for zero-trust deployment. Once downloaded over Wi-Fi, map assets remain completely local and never phone home.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        val kb = bytes.toDouble() / 1024
        String.format(Locale.US, "%.1f KB", kb)
    }
}
