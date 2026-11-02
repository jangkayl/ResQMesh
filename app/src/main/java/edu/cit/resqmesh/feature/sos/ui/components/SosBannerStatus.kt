@file:Suppress("MagicNumber")

package edu.cit.resqmesh.feature.sos.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.cit.resqmesh.core.model.SosAlert
import edu.cit.resqmesh.core.ui.theme.ResQTheme

private const val LIGHT_BACKGROUND_LUMINANCE = 0.5f
private val SilencedAccent = Color(0xFFFFB300)
private val SilencedTextLight = Color(0xFFB45309)
private val SilencedTextDark = Color(0xFFFDE68A)
private val ActiveAccent = Color(0xFFDC2626)
private val ActiveTextDark = Color(0xFFFCA5A5)

private fun collapsedSosSummary(alert: SosAlert, locationWithheld: Boolean): String =
    "${sosShortLocationLabel(alert, locationWithheld)} · ${formatSosHeaderTime(sosStatusTimestamp(alert))}"

/** One-line strip shown after the banner is tapped; tapping it brings the full banner back. */
@Suppress("FunctionNaming")
@Composable
internal fun CollapsedSosHeader(
    alert: SosAlert,
    categoryColor: Color,
    categoryIcon: ImageVector,
    onExpand: () -> Unit,
    /** Own SOS sent with "Include my location" off. */
    locationWithheld: Boolean = false
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > LIGHT_BACKGROUND_LUMINANCE
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
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
            CollapsedSosStatus(alert)
            Spacer(Modifier.width(8.dp))
            Text(
                text = collapsedSosSummary(alert, locationWithheld),
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

@Suppress("FunctionNaming")
@Composable
private fun CollapsedSosStatus(alert: SosAlert) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = when {
            alert.ended -> MaterialTheme.colorScheme.surfaceVariant
            alert.locallySilenced -> SilencedAccent.copy(alpha = 0.15f)
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
}

@Suppress("FunctionNaming")
@Composable
internal fun ExpandedSosStatusBadge(alert: SosAlert, isLight: Boolean) {
    val accent = if (alert.locallySilenced) SilencedAccent else ActiveAccent
    val accentText = when {
        alert.locallySilenced -> if (isLight) SilencedTextLight else SilencedTextDark
        else -> if (isLight) ActiveAccent else ActiveTextDark
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (alert.ended) MaterialTheme.colorScheme.surfaceVariant else accent.copy(alpha = 0.15f),
        border = BorderStroke(
            1.dp,
            if (alert.ended) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            else accent.copy(alpha = 0.4f)
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
            color = if (alert.ended) MaterialTheme.colorScheme.onSurfaceVariant else accentText,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
