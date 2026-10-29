package edu.cit.resqmesh.feature.sos.ui.broadcast

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.cit.resqmesh.core.domain.usecase.sos.SOS_CACHED_LOCATION_MAX_AGE_MS
import edu.cit.resqmesh.core.location.LocationFix
import edu.cit.resqmesh.core.location.MS_PER_MINUTE
import edu.cit.resqmesh.core.location.summary
import edu.cit.resqmesh.core.ui.components.location.LocationAvailability
import edu.cit.resqmesh.core.ui.components.location.fixLabel
import edu.cit.resqmesh.core.ui.theme.ResQTheme

internal enum class SosLocationTone { GOOD, PENDING, ATTENTION, NEUTRAL, PROBLEM }

internal data class SosLocationRowModel(
    val tone: SosLocationTone,
    val title: String,
    val subtitle: String,
    /** Non-null when the phone cannot give a location; the button fixes it. */
    val problem: LocationAvailability? = null
)

/** Pure mapping so every state's wording is unit-tested. Location never blocks the SOS. */
internal fun sosLocationRowModel(
    includeLocation: Boolean,
    availability: LocationAvailability,
    autoDetect: Boolean,
    fix: LocationFix?,
    now: Long
): SosLocationRowModel = when {
    !includeLocation -> SosLocationRowModel(
        SosLocationTone.NEUTRAL, "Location not included", "Responders will see your alert without a map pin."
    )
    availability == LocationAvailability.PERMISSION_DENIED -> SosLocationRowModel(
        SosLocationTone.PROBLEM, "Location unavailable", "Allow location so rescuers can find you.", availability
    )
    availability == LocationAvailability.LOCATION_OFF -> SosLocationRowModel(
        SosLocationTone.PROBLEM, "Location unavailable", "Turn on phone location to include it.", availability
    )
    fix != null && now - fix.capturedAt < SOS_CACHED_LOCATION_MAX_AGE_MS -> SosLocationRowModel(
        SosLocationTone.GOOD, "Location included", fix.summary(now)
    )
    fix != null -> SosLocationRowModel(
        SosLocationTone.ATTENTION, "Location included",
        "Last fix ${(now - fix.capturedAt) / MS_PER_MINUTE} min ago. A new fix is taken on send."
    )
    !autoDetect -> SosLocationRowModel(
        SosLocationTone.NEUTRAL, "Location included", "A fix will be taken when you send."
    )
    else -> SosLocationRowModel(
        SosLocationTone.PENDING, "Location included", "Finding your position. SOS sends now and updates when found."
    )
}

/**
 * Stateless "include my location" row on the SOS screen. The whole row toggles; when the phone
 * cannot give a location the switch is replaced by a one-tap fix and the SOS still sends.
 */
@Composable
internal fun SosLocationRow(
    model: SosLocationRowModel,
    includeLocation: Boolean,
    onIncludeChange: (Boolean) -> Unit,
    onFixProblem: (LocationAvailability) -> Unit,
    modifier: Modifier = Modifier
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > HALF
    val problem = model.problem
    val rowModifier = if (problem == null) {
        Modifier.toggleable(value = includeLocation, role = Role.Switch, onValueChange = onIncludeChange)
    } else {
        Modifier
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = if (isLight) 4.dp else 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = rowModifier
                .heightIn(min = 64.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LocationToneBadge(model.tone, includeLocation)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = model.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (problem != null) {
                FilledTonalButton(
                    onClick = { onFixProblem(problem) },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text(problem.fixLabel()) }
            } else {
                // The row owns the toggle; the switch is visual only so TalkBack hears one control.
                Switch(checked = includeLocation, onCheckedChange = null)
            }
        }
    }
}

@Composable
private fun LocationToneBadge(tone: SosLocationTone, includeLocation: Boolean) {
    val toneColor = when (tone) {
        SosLocationTone.GOOD -> ResQTheme.colors.success
        SosLocationTone.PENDING, SosLocationTone.ATTENTION -> ResQTheme.colors.warning
        SosLocationTone.PROBLEM -> MaterialTheme.colorScheme.error
        SosLocationTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when {
        !includeLocation -> Icons.Default.LocationOff
        tone == SosLocationTone.PROBLEM -> Icons.Default.WarningAmber
        else -> Icons.Default.LocationOn
    }
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).background(toneColor.copy(alpha = BADGE_ALPHA)),
        contentAlignment = Alignment.Center
    ) {
        if (tone == SosLocationTone.PENDING) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = toneColor)
        } else {
            Icon(icon, contentDescription = null, tint = toneColor, modifier = Modifier.size(20.dp))
        }
    }
}

private const val HALF = 0.5f
private const val BADGE_ALPHA = 0.14f
