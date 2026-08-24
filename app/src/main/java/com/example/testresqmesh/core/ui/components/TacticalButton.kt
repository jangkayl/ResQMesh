package com.example.testresqmesh.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class TacticalButtonVariant {
    Primary, Danger, Ghost, Outline
}

@Composable
fun TacticalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: TacticalButtonVariant = TacticalButtonVariant.Primary,
    cornerRadius: Dp = 12.dp,
    content: @Composable RowScope.() -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "button_scale"
    )

    if (isPressed) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    val (bgColor, contentColor, borderStroke) = when (variant) {
        TacticalButtonVariant.Primary -> Triple(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary,
            null
        )
        TacticalButtonVariant.Danger -> Triple(
            MaterialTheme.colorScheme.error,
            Color.White,
            null
        )
        TacticalButtonVariant.Ghost -> Triple(
            Color.Transparent,
            MaterialTheme.colorScheme.primary,
            null
        )
        TacticalButtonVariant.Outline -> Triple(
            Color.Transparent,
            MaterialTheme.colorScheme.primary,
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        )
    }

    val clickableModifier = modifier
        .scale(scale)
        .clip(RoundedCornerShape(cornerRadius))
        .clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.material3.ripple(color = contentColor),
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
        )
        .then(
            if (borderStroke != null) Modifier.border(borderStroke, RoundedCornerShape(cornerRadius))
            else Modifier
        )
        .background(bgColor)
        .padding(vertical = 14.dp, horizontal = 24.dp)

    Row(
        modifier = clickableModifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
