package com.example.testresqmesh.feature.sos.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.platform.testTag
import com.example.testresqmesh.feature.comms.model.sosTransmissionLabel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.data.local.entity.SosAlertEntity

internal fun formatSosHeaderTime(timestamp: Long): String {
    if (timestamp < 1_000_000_000_000L) {
        return java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
            .format(java.util.Date(System.currentTimeMillis()))
    }
    return runCatching {
        java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
            .format(java.util.Date(timestamp))
    }.getOrDefault("Just now")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExpandedSosHeader(
    alert: SosAlertEntity,
    categoryColor: Color,
    categoryIcon: ImageVector,
    onMap: () -> Unit,
    onEnd: () -> Unit,
    onSilence: () -> Unit,
    onActivity: () -> Unit,
    isOwner: Boolean
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val statusTimestamp = when {
        alert.ended -> alert.updatedAt.takeIf { it > 1_000_000_000_000L } ?: alert.createdAt
        alert.updatedAt > 1_000_000_000_000L -> alert.updatedAt
        alert.createdAt > 1_000_000_000_000L -> alert.createdAt
        else -> System.currentTimeMillis()
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.5.dp,
            if (alert.ended) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            else categoryColor.copy(alpha = 0.7f)
        ),
        shadowElevation = if (isLight) 3.dp else 6.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Row 1: Left: Badges; Right: Audit History Action (Balances the card header)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category & Status Badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = categoryColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, categoryColor.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = categoryIcon,
                                contentDescription = null,
                                tint = categoryColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = alert.emergencyType,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when {
                            alert.ended -> MaterialTheme.colorScheme.surfaceVariant
                            alert.locallySilenced -> Color(0xFFFFB300).copy(alpha = 0.15f)
                            else -> Color(0xFFDC2626).copy(alpha = 0.15f)
                        },
                        border = BorderStroke(
                            1.dp,
                            when {
                                alert.ended -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                alert.locallySilenced -> Color(0xFFFFB300).copy(alpha = 0.4f)
                                else -> Color(0xFFDC2626).copy(alpha = 0.4f)
                            }
                        )
                    ) {
                        Text(
                            text = when {
                                alert.ended -> "ENDED"
                                alert.locallySilenced -> "SILENCED LOCALLY"
                                else -> "ACTIVE SOS"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = when {
                                alert.ended -> MaterialTheme.colorScheme.onSurfaceVariant
                                alert.locallySilenced -> if (isLight) Color(0xFFB45309) else Color(0xFFFDE68A)
                                else -> if (isLight) Color(0xFFDC2626) else Color(0xFFFCA5A5)
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // History button in top-right corner to balance header
                Surface(
                    onClick = onActivity,
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.testTag("sos_activity")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Activity history",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "History",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Telemetry Box (Tactical HUD, spans 100% of card width)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isLight) 0.35f else 0.25f)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // Row 1: Transmission status (Left) + Timestamp (Right)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (alert.ended) Icons.Default.CheckCircle else Icons.Default.Sensors,
                                contentDescription = null,
                                tint = if (alert.ended) ResQTheme.colors.success else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (alert.ended) "Emergency ended" else sosTransmissionLabel(alert.transmission),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = formatSosHeaderTime(statusTimestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    // Row 2: Location telemetry (Spans full width)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = if (alert.latitude != null) ResQTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = when {
                                alert.latitude != null -> "Pinned (${String.format(java.util.Locale.US, "%.4f, %.4f", alert.latitude, alert.longitude)}) · ±${alert.accuracyMeters?.toInt() ?: "?"}m"
                                alert.ended -> "Location telemetry unavailable"
                                else -> "Acquiring GPS location..."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Action Buttons: Symmetrically distributed across 100% width!
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onMap,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Map", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }

                if (!alert.ended && isOwner) {
                    Button(
                        onClick = onEnd,
                        modifier = Modifier
                            .weight(1.4f)
                            .heightIn(min = 44.dp)
                            .testTag("sos_end"),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB42318))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("End my SOS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                } else if (!alert.ended) {
                    OutlinedButton(
                        onClick = onSilence,
                        modifier = Modifier
                            .weight(1.4f)
                            .heightIn(min = 44.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (alert.locallySilenced) "Silenced on this phone" else "Silence on this phone",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("sos_silence"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CompactSosHeader(
    alert: SosAlertEntity,
    categoryColor: Color,
    categoryIcon: ImageVector,
    onMap: () -> Unit,
    onEnd: () -> Unit,
    onSilence: () -> Unit,
    onActivity: () -> Unit,
    isOwner: Boolean
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (alert.ended) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            else categoryColor.copy(alpha = 0.65f)
        ),
        shadowElevation = if (isLight) 2.dp else 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Badge
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = categoryColor.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, categoryColor.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = null,
                        tint = categoryColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = alert.emergencyType,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            // Status Pill
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = when {
                    alert.ended -> MaterialTheme.colorScheme.surfaceVariant
                    alert.locallySilenced -> Color(0xFFFFB300).copy(alpha = 0.15f)
                    else -> ResQTheme.colors.sos.copy(alpha = 0.15f)
                }
            ) {
                Text(
                    text = when {
                        alert.ended -> "ENDED"
                        alert.locallySilenced -> "SILENCED"
                        else -> "ACTIVE"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }

            Spacer(Modifier.width(6.dp))

            // Compact Location Telemetry
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = if (alert.latitude != null) "±${alert.accuracyMeters?.toInt() ?: "?"}m" else if (alert.ended) "No GPS" else "Acquiring...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.weight(1f))

            // Action Buttons
            IconButton(
                onClick = onMap,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = "Map",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }

            if (!alert.ended && isOwner) {
                Button(
                    onClick = onEnd,
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("sos_end"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB42318))
                ) {
                    Text(
                        "End",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (!alert.ended) {
                OutlinedButton(
                    onClick = onSilence,
                    modifier = Modifier.height(30.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsOff,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        if (alert.locallySilenced) "Silenced" else "Silence",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.testTag("sos_silence")
                    )
                }
            }

            IconButton(
                onClick = onActivity,
                modifier = Modifier.size(32.dp).testTag("sos_activity")
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "Activity history",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
