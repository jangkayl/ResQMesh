package com.example.testresqmesh.core.ui.components.buttons

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.testresqmesh.core.ui.theme.ResQSize
import com.example.testresqmesh.core.ui.theme.ResQTheme
import androidx.compose.ui.unit.dp

enum class ButtonVariant {
    Primary, Secondary, Outline, Ghost, Destructive
}

@Composable
fun ResQButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.extraLarge
    val containerColor = when (variant) {
        ButtonVariant.Primary -> MaterialTheme.colorScheme.primary
        ButtonVariant.Secondary -> MaterialTheme.colorScheme.surface
        ButtonVariant.Outline -> Color.Transparent
        ButtonVariant.Ghost -> Color.Transparent
        ButtonVariant.Destructive -> MaterialTheme.colorScheme.error
    }

    val contentColor = when (variant) {
        ButtonVariant.Primary -> MaterialTheme.colorScheme.onPrimary
        ButtonVariant.Secondary -> MaterialTheme.colorScheme.onSurface
        ButtonVariant.Outline -> MaterialTheme.colorScheme.primary
        ButtonVariant.Ghost -> MaterialTheme.colorScheme.primary
        ButtonVariant.Destructive -> MaterialTheme.colorScheme.onError
    }

    val border = if (variant == ButtonVariant.Outline) {
        ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary))
    } else if (variant == ButtonVariant.Secondary) {
        androidx.compose.foundation.BorderStroke(1.dp, ResQTheme.colors.glassBorder)
    } else null

    val decoratedModifier = modifier
        .defaultMinSize(minHeight = ResQSize.MinimumTouchTarget)
        .then(
            if (variant == ButtonVariant.Primary && enabled) {
                Modifier.shadow(
                        elevation = 8.dp,
                        shape = shape,
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                    )
            } else Modifier
        )

    Button(
        onClick = onClick,
        modifier = decoratedModifier,
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = if (variant == ButtonVariant.Primary) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            } else containerColor.copy(alpha = 0.5f),
            disabledContentColor = contentColor.copy(alpha = 0.5f)
        ),
        border = border,
        contentPadding = contentPadding,
        elevation = if (variant == ButtonVariant.Ghost || variant == ButtonVariant.Outline) null else ButtonDefaults.buttonElevation(),
        content = content
    )
}
