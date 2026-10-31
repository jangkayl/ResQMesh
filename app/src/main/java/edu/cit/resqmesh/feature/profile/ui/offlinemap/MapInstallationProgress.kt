package edu.cit.resqmesh.feature.profile.ui.offlinemap

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.cit.resqmesh.core.map.model.MapPackageStatus
import edu.cit.resqmesh.core.ui.theme.SafetyOrange
import edu.cit.resqmesh.core.ui.theme.SignalAmber
import edu.cit.resqmesh.core.ui.theme.SignalGreen
import edu.cit.resqmesh.core.ui.theme.SignalRed
import edu.cit.resqmesh.core.ui.theme.Spacing

import edu.cit.resqmesh.feature.profile.ui.offlinemap.formatBytes

@Composable
internal fun DownloadingProgressSection(
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
                text = "Downloading map…",
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
internal fun VerifyingProgressSection() {
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
                text = "Checking the map…",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "This makes sure the download is complete and safe.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun ExtractingProgressSection(extracting: MapPackageStatus.Extracting) {
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
                text = "Installing the map…",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Almost done.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MapUpdateNotice(onUpdate: () -> Unit) {
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
                    text = "A newer map is available",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Update to get the latest version.",
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

@Composable
internal fun InstalledActiveSection(
    isUpdateAvailable: Boolean,
    onUpdate: () -> Unit,
    onDelete: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SignalGreen, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(Spacing.Small))
            Column {
                Text(
                    text = "Map is ready",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "You can use it without internet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (isUpdateAvailable) {
            MapUpdateNotice(onUpdate = onUpdate)
        }

        OutlinedButton(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = SignalRed),
            border = BorderStroke(1.dp, SignalRed.copy(alpha = 0.4f))
        ) {
            Icon(Icons.Default.DeleteOutline, contentDescription = null)
            Spacer(Modifier.width(Spacing.Small))
            Text("Delete map")
        }
    }
}
