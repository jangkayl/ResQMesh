package com.example.testresqmesh.core.ui.components.feedback

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.core.ui.components.buttons.ButtonVariant
import com.example.testresqmesh.core.ui.components.buttons.ResQButton
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.Spacing

enum class ResQStatusTone {
    Neutral,
    Information,
    Success,
    Warning,
    Critical
}

private data class ToneColors(
    val foreground: Color,
    val container: Color,
    val border: Color
)

@Composable
private fun ResQStatusTone.colors(): ToneColors = when (this) {
    ResQStatusTone.Neutral -> ToneColors(
        foreground = MaterialTheme.colorScheme.onSurfaceVariant,
        container = MaterialTheme.colorScheme.surfaceVariant,
        border = MaterialTheme.colorScheme.outlineVariant
    )
    ResQStatusTone.Information -> ToneColors(
        foreground = MaterialTheme.colorScheme.onPrimaryContainer,
        container = MaterialTheme.colorScheme.primaryContainer,
        border = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    )
    ResQStatusTone.Success -> ToneColors(
        foreground = ResQTheme.colors.onSuccessContainer,
        container = ResQTheme.colors.successContainer,
        border = ResQTheme.colors.success.copy(alpha = 0.35f)
    )
    ResQStatusTone.Warning -> ToneColors(
        foreground = ResQTheme.colors.onWarningContainer,
        container = ResQTheme.colors.warningContainer,
        border = ResQTheme.colors.warning.copy(alpha = 0.35f)
    )
    ResQStatusTone.Critical -> ToneColors(
        foreground = ResQTheme.colors.onSosContainer,
        container = ResQTheme.colors.sosContainer,
        border = ResQTheme.colors.sos.copy(alpha = 0.35f)
    )
}

@Composable
fun ResQStatusChip(
    label: String,
    tone: ResQStatusTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val colors = tone.colors()
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = colors.container,
        contentColor = colors.foreground,
        border = BorderStroke(1.dp, colors.border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun ResQStateCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    tone: ResQStatusTone = ResQStatusTone.Neutral,
    icon: ImageVector = Icons.Outlined.Info,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = tone.colors()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.container,
        contentColor = colors.foreground,
        border = BorderStroke(1.dp, colors.border)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
            verticalAlignment = Alignment.Top
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(Spacing.ExtraSmall))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.height(Spacing.Small))
                    ResQButton(
                        onClick = onAction,
                        variant = ButtonVariant.Ghost
                    ) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

@Composable
fun ResQEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Info,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Large, vertical = Spacing.ExtraLarge),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(Spacing.Medium).size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(Spacing.Medium))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Spacing.ExtraSmall))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.Medium))
            ResQButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
