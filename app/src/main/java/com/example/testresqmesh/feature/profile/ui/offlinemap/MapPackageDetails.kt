package com.example.testresqmesh.feature.profile.ui.offlinemap

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.map.model.MapPackageStatus
import com.example.testresqmesh.core.ui.theme.SafetyOrange
import com.example.testresqmesh.core.ui.theme.SignalAmber
import com.example.testresqmesh.core.ui.theme.SignalGreen
import com.example.testresqmesh.core.ui.theme.SignalRed
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.feature.profile.viewmodel.OfflineMapUiState

import com.example.testresqmesh.feature.profile.ui.offlinemap.NotInstalledSection
import com.example.testresqmesh.feature.profile.ui.offlinemap.WifiRequiredSection
import com.example.testresqmesh.feature.profile.ui.offlinemap.InsufficientStorageSection
import com.example.testresqmesh.feature.profile.ui.offlinemap.CorruptPackageSection
import com.example.testresqmesh.feature.profile.ui.offlinemap.GenericErrorSection
import com.example.testresqmesh.feature.profile.ui.offlinemap.DownloadingProgressSection
import com.example.testresqmesh.feature.profile.ui.offlinemap.VerifyingProgressSection
import com.example.testresqmesh.feature.profile.ui.offlinemap.ExtractingProgressSection
import com.example.testresqmesh.feature.profile.ui.offlinemap.InstalledActiveSection

@Composable
internal fun MapPackageOverviewCard(
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
internal fun StatusBadge(
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
internal fun PackageTechnicalDetailsCard(state: OfflineMapUiState) {
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
internal fun TacticalDeploymentGuidanceCard() {
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
internal fun SpecRow(label: String, value: String) {
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
