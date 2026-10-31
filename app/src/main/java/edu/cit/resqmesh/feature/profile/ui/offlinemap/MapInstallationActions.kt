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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.cit.resqmesh.core.ui.theme.SafetyOrange
import edu.cit.resqmesh.core.ui.theme.SignalAmber
import edu.cit.resqmesh.core.ui.theme.Spacing

import edu.cit.resqmesh.feature.profile.ui.offlinemap.formatBytes

@Composable
internal fun NotInstalledSection(
    estimatedBytes: Long,
    onDownload: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
        Text(
            text = "Download this map so you can see SOS locations without internet. " +
                "Without it, you'll see the location as numbers only.",
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
                text = "Download map (${formatBytes(estimatedBytes)})",
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
                    text = "You're not on Wi-Fi",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Connect to Wi-Fi, or use mobile data to download now. Your mobile plan may charge for this.",
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
                    Text("Use mobile data")
                }
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange)
                ) {
                    Text("Try Wi-Fi again")
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
        color = SignalAmber.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, SignalAmber.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storage, contentDescription = null, tint = SignalAmber)
                Spacer(Modifier.width(Spacing.Small))
                Text(
                    text = "Not enough space",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Free up about $requiredMb MB and try again. You have ${formatBytes(availableBytes)} free.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text("Check again", color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
internal fun CorruptPackageSection(onRetry: () -> Unit) {
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
                Icon(Icons.Default.Warning, contentDescription = null, tint = SignalAmber)
                Spacer(Modifier.width(Spacing.Small))
                Text(
                    text = "The download was damaged",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "We removed it. Please try again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange)
            ) {
                Text("Try again")
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
        color = SignalAmber.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, SignalAmber.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            Text(
                text = "Download didn't finish",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
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
                Text("Try again")
            }
        }
    }
}
