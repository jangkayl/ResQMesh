package com.example.testresqmesh.core.ui.components.buttons

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.core.ui.theme.*

enum class ButtonVariant {
    Primary, Secondary, Outline, Ghost, Destructive
}

@Composable
fun ResQButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed && enabled) 0.96f else 1f, label = "ButtonScale")

    val backgroundBrush = when (variant) {
        ButtonVariant.Primary -> if (enabled) Brush.linearGradient(listOf(CyanPrimary, CyanSecondary)) else null
        ButtonVariant.Secondary -> if (enabled) Brush.linearGradient(listOf(Slate800, Slate700)) else null
        ButtonVariant.Destructive -> if (enabled) Brush.linearGradient(listOf(ErrorRed, Color(0xFFB91C1C))) else null
        else -> null
    }

    val backgroundColor = when (variant) {
        ButtonVariant.Primary -> if (!enabled) Slate800 else Color.Transparent
        ButtonVariant.Secondary -> if (!enabled) Slate900 else Color.Transparent
        ButtonVariant.Outline, ButtonVariant.Ghost -> Color.Transparent
        ButtonVariant.Destructive -> if (!enabled) Slate800 else Color.Transparent
    }

    val contentColor = when (variant) {
        ButtonVariant.Primary -> if (enabled) Slate950 else Slate400
        ButtonVariant.Secondary -> if (enabled) Slate50 else Slate400
        ButtonVariant.Outline, ButtonVariant.Ghost -> if (enabled) CyanPrimary else Slate700
        ButtonVariant.Destructive -> if (enabled) Color.White else Slate400
    }

    val border = when (variant) {
        ButtonVariant.Outline -> BorderStroke(1.dp, if (enabled) CyanPrimary.copy(alpha = 0.5f) else Slate700)
        else -> null
    }

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (backgroundBrush != null) Modifier.background(backgroundBrush)
                else Modifier.background(backgroundColor)
            )
            .then(if (border != null) Modifier.border(border, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Row(
                modifier = Modifier.padding(contentPadding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}
