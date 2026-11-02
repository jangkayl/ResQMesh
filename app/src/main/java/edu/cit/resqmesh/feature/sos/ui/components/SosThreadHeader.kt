package edu.cit.resqmesh.feature.sos.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.platform.testTag
import edu.cit.resqmesh.feature.comms.model.sosTransmissionLabel
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import edu.cit.resqmesh.core.ui.theme.ResQTheme
import edu.cit.resqmesh.core.model.SosAlert

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

private fun sosStatusTimestamp(alert: SosAlert): Long = when {
    alert.ended -> alert.updatedAt.takeIf { it > 1_000_000_000_000L } ?: alert.createdAt
    alert.updatedAt > 1_000_000_000_000L -> alert.updatedAt
    alert.createdAt > 1_000_000_000_000L -> alert.createdAt
    else -> System.currentTimeMillis()
}

private fun sosShortLocationLabel(alert: SosAlert, locationWithheld: Boolean): String = when {
    alert.latitude != null -> "±${alert.accuracyMeters?.toInt() ?: "?"}m"
    locationWithheld -> "Not shared"
    alert.ended -> "No GPS"
    else -> "Acquiring..."
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExpandedSosHeader(
    alert: SosAlert,
    categoryColor: Color,
    categoryIcon: ImageVector,
    onMap: () -> Unit,
    onEnd: () -> Unit,
    onSilence: () -> Unit,
    onActivity: () -> Unit,
    onCollapse: () -> Unit,
    isOwner: Boolean,
    /** Own SOS sent with "Include my location" off. */
    locationWithheld: Boolean = false
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val statusTimestamp = sosStatusTimestamp(alert)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.5.dp,
            if (alert.ended) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            else categoryColor.copy(alpha = 0.7f)
        ),
        shadowElevation = if (isLight) 3.dp else 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Collapse SOS details", onClick = onCollapse)
                .testTag("sos_banner_toggle")
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            // Row 1: Left: Badges; Right: Collapse hint
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

                Icon(
                    imageVector = Icons.Default.ExpandLess,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.height(6.dp))

            // Row 2: Transmission status (Left) + Timestamp (Right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = if (alert.ended) Icons.Default.CheckCircle else Icons.Default.Sensors,
                        contentDescription = null,
                        tint = if (alert.ended) ResQTheme.colors.success else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (alert.ended) "Emergency ended" else sosTransmissionLabel(alert.transmission),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(8.dp))

                Text(
                    text = formatSosHeaderTime(statusTimestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(2.dp))

            // Row 3: Location telemetry (Spans full width)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = if (alert.latitude != null) ResQTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = when {
                        alert.latitude != null -> "Pinned (${String.format(java.util.Locale.US, "%.4f, %.4f", alert.latitude, alert.longitude)}) · ±${alert.accuracyMeters?.toInt() ?: "?"}m"
                        locationWithheld -> "Location not shared"
                        alert.ended -> "Location telemetry unavailable"
                        else -> "Finding your location…"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(8.dp))

            // Row 4: Map, History and End/Silence share one line
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onMap,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Map",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onActivity,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .testTag("sos_activity"),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "History",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!alert.ended && isOwner) {
                    Button(
                        onClick = onEnd,
                        modifier = Modifier
                            .weight(1.4f)
                            .heightIn(min = 40.dp)
                            .testTag("sos_end"),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB42318))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "End my SOS",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else if (!alert.ended) {
                    OutlinedButton(
                        onClick = onSilence,
                        modifier = Modifier
                            .weight(1.4f)
                            .heightIn(min = 40.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (alert.locallySilenced) "Silenced" else "Silence",
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

/** One-line strip shown after the banner is tapped; tapping it brings the full banner back. */
@Composable
internal fun CollapsedSosHeader(
    alert: SosAlert,
    categoryColor: Color,
    categoryIcon: ImageVector,
    onExpand: () -> Unit,
    /** Own SOS sent with "Include my location" off. */
    locationWithheld: Boolean = false
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
                .clickable(onClickLabel = "Show SOS details", onClick = onExpand)
                .testTag("sos_banner_toggle")
                .heightIn(min = 44.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp),
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
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(8.dp))
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
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${sosShortLocationLabel(alert, locationWithheld)} · ${formatSosHeaderTime(sosStatusTimestamp(alert))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
internal fun CompactSosHeader(
    alert: SosAlert,
    categoryColor: Color,
    categoryIcon: ImageVector,
    onMap: () -> Unit,
    onEnd: () -> Unit,
    onSilence: () -> Unit,
    onActivity: () -> Unit,
    isOwner: Boolean,
    /** Own SOS sent with "Include my location" off. */
    locationWithheld: Boolean = false
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
                    text = sosShortLocationLabel(alert, locationWithheld),
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
