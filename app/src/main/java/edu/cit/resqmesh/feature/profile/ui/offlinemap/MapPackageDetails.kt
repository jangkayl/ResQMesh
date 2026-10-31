package edu.cit.resqmesh.feature.profile.ui.offlinemap

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.cit.resqmesh.core.map.model.MapPackageStatus
import edu.cit.resqmesh.core.ui.theme.ResQSize
import edu.cit.resqmesh.core.ui.theme.SafetyOrange
import edu.cit.resqmesh.core.ui.theme.SignalAmber
import edu.cit.resqmesh.core.ui.theme.SignalGreen
import edu.cit.resqmesh.core.ui.theme.SignalRed
import edu.cit.resqmesh.core.ui.theme.Spacing
import edu.cit.resqmesh.feature.profile.viewmodel.OfflineMapUiState

import edu.cit.resqmesh.feature.profile.ui.offlinemap.NotInstalledSection
import edu.cit.resqmesh.feature.profile.ui.offlinemap.WifiRequiredSection
import edu.cit.resqmesh.feature.profile.ui.offlinemap.InsufficientStorageSection
import edu.cit.resqmesh.feature.profile.ui.offlinemap.CorruptPackageSection
import edu.cit.resqmesh.feature.profile.ui.offlinemap.GenericErrorSection
import edu.cit.resqmesh.feature.profile.ui.offlinemap.DownloadingProgressSection
import edu.cit.resqmesh.feature.profile.ui.offlinemap.VerifyingProgressSection
import edu.cit.resqmesh.feature.profile.ui.offlinemap.ExtractingProgressSection
import edu.cit.resqmesh.feature.profile.ui.offlinemap.InstalledActiveSection

private const val SPEC_LABEL_WEIGHT = 0.4f
private const val SPEC_VALUE_WEIGHT = 0.6f

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
                    VerifyingProgressSection()
                }
                state.packageStatus is MapPackageStatus.Extracting -> {
                    ExtractingProgressSection(extracting = state.packageStatus)
                }
                state.packageStatus is MapPackageStatus.Installed -> {
                    InstalledActiveSection(
                        isUpdateAvailable = state.isUpdateAvailable,
                        onUpdate = onDownload,
                        onDelete = onDelete
                    )
                }
                state.packageStatus is MapPackageStatus.Error -> {
                    GenericErrorSection(
                        message = friendlyMapError(state.packageStatus.message),
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
        isUpdateAvailable -> Triple("Update available", SignalAmber.copy(alpha = 0.15f), SignalAmber)
        status is MapPackageStatus.Installed -> Triple("Ready", SignalGreen.copy(alpha = 0.15f), SignalGreen)
        status is MapPackageStatus.Downloading -> Triple("Downloading", SafetyOrange.copy(alpha = 0.15f), SafetyOrange)
        status is MapPackageStatus.Verifying -> Triple("Checking", SafetyOrange.copy(alpha = 0.15f), SafetyOrange)
        status is MapPackageStatus.Extracting -> Triple("Installing", SafetyOrange.copy(alpha = 0.15f), SafetyOrange)
        status is MapPackageStatus.Error -> Triple("Didn't finish", SignalRed.copy(alpha = 0.15f), SignalRed)
        else -> {
            val neutral = MaterialTheme.colorScheme.onSurfaceVariant
            Triple("Not downloaded", neutral.copy(alpha = 0.15f), neutral)
        }
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
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.Large, vertical = Spacing.Small)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ResQSize.MinimumTouchTarget)
                    .clickable(onClickLabel = if (isExpanded) "Hide technical details" else "Show technical details") {
                        isExpanded = !isExpanded
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Technical details",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isExpanded) {
                Column(
                    modifier = Modifier.padding(bottom = Spacing.Small),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Small)
                ) {
                    state.activeManifest?.let { SpecRow("Map version", "${it.version}") }
                    SpecRow("Coverage", state.catalogEntry.coverageArea)
                    SpecRow("Zoom levels", "${state.catalogEntry.minZoom} to ${state.catalogEntry.maxZoom}")
                    SpecRow("Map engine", "MapLibre Native")
                    SpecRow("Map data", "© OpenStreetMap contributors, ODbL")
                    SpecRow("Download checks", "ECDSA P-256 signature, SHA-256 checksum")
                    SpecRow("Internet use", "None after download")
                }
            }
        }
    }
}

@Composable
internal fun OfflineUseNoteCard() {
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
                text = "Once downloaded, the map works without internet.",
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
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(SPEC_LABEL_WEIGHT)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(SPEC_VALUE_WEIGHT)
        )
    }
}
