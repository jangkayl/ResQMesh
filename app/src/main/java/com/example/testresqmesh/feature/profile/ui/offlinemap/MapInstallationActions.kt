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
import com.example.testresqmesh.core.ui.theme.SafetyOrange
import com.example.testresqmesh.core.ui.theme.SignalAmber
import com.example.testresqmesh.core.ui.theme.SignalRed
import com.example.testresqmesh.core.ui.theme.Spacing

import com.example.testresqmesh.feature.profile.ui.offlinemap.formatBytes

@Composable
internal fun NotInstalledSection(
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
internal fun WifiRequiredSection(
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
internal fun InsufficientStorageSection(
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
internal fun CorruptPackageSection(onRetry: () -> Unit) {
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
internal fun GenericErrorSection(
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
