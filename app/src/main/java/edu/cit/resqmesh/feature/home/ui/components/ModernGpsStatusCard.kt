package edu.cit.resqmesh.feature.home.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.cit.resqmesh.core.location.LocationFix
import edu.cit.resqmesh.core.location.LocationStatus
import edu.cit.resqmesh.core.ui.components.location.LocationAvailability
import edu.cit.resqmesh.core.ui.components.location.fixLabel
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.produceState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import edu.cit.resqmesh.core.ui.theme.ModernMint
import edu.cit.resqmesh.core.ui.theme.ResQTheme

@Composable
internal fun ModernGpsStatusCard(
    locationStatus: LocationStatus,
    lastFix: LocationFix? = null,
    autoDetectEnabled: Boolean = locationStatus != LocationStatus.OFF,
    onAutoDetectChange: ((Boolean) -> Unit)? = null,
    onFixLocation: ((LocationAvailability) -> Unit)? = null
) {
    val now by produceState(System.currentTimeMillis(), lastFix) {
        while (true) {
            value = System.currentTimeMillis()
            delay(FIX_AGE_TICK_MS)
        }
    }
    val text = gpsStatusText(locationStatus, lastFix, now)
    val problem = when (locationStatus) {
        LocationStatus.ERROR_DENIED -> LocationAvailability.PERMISSION_DENIED
        LocationStatus.ERROR_DISABLED -> LocationAvailability.LOCATION_OFF
        else -> null
    }
    val rowModifier = if (onAutoDetectChange != null && problem == null) {
        Modifier.toggleable(value = autoDetectEnabled, role = Role.Switch, onValueChange = onAutoDetectChange)
    } else {
        Modifier
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = rowModifier
                .heightIn(min = 56.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GpsStatusBadge(locationStatus)
            GpsStatusTexts(text, Modifier.weight(1f))
            if (problem != null && onFixLocation != null) {
                FilledTonalButton(
                    onClick = { onFixLocation(problem) },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text(problem.fixLabel()) }
            } else if (onAutoDetectChange != null) {
                // Visual only: the row is the toggle, labelled by the texts above.
                Switch(
                    checked = autoDetectEnabled,
                    onCheckedChange = null,
                    modifier = Modifier.semantics { contentDescription = "Auto-detect location" }
                )
            }
        }
    }
}

@Composable
private fun GpsStatusTexts(text: GpsStatusText, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(
            text = text.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = text.description,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GpsStatusBadge(locationStatus: LocationStatus) {
    val statusColor = when (locationStatus) {
        LocationStatus.ACQUIRING, LocationStatus.ERROR_DISABLED -> ResQTheme.colors.warning
        LocationStatus.READY -> ModernMint
        LocationStatus.ERROR_DENIED -> MaterialTheme.colorScheme.error
        LocationStatus.IDLE, LocationStatus.OFF -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(statusColor.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        if (locationStatus == LocationStatus.ACQUIRING) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = statusColor
            )
        } else {
            Icon(
                imageVector = if (locationStatus == LocationStatus.OFF) {
                    Icons.Outlined.LocationOff
                } else {
                    Icons.Outlined.NearMe
                },
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private const val FIX_AGE_TICK_MS = 30_000L

/**
 * Clean, reassuring safety guidance footer.
 */
