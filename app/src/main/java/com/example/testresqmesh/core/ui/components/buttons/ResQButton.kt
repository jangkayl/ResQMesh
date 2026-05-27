package com.example.testresqmesh.core.ui.components.buttons

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
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
    contentPadding: PaddingValues = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed && enabled) 0.97f else 1f, label = "ButtonScale")

    val containerColor = when (variant) {
        ButtonVariant.Primary -> if (enabled) PrimaryRed else LightGray
        ButtonVariant.Secondary -> if (enabled) OffWhite else OffWhite
        ButtonVariant.Outline -> Color.Transparent
        ButtonVariant.Ghost -> Color.Transparent
        ButtonVariant.Destructive -> if (enabled) PrimaryRed else LightGray
    }

    val contentColor = when (variant) {
        ButtonVariant.Primary -> if (enabled) WarmWhite else MediumGray
        ButtonVariant.Secondary -> if (enabled) PrimaryRed else MediumGray
        ButtonVariant.Outline, ButtonVariant.Ghost -> if (enabled) PrimaryRed else MediumGray
        ButtonVariant.Destructive -> if (enabled) WarmWhite else MediumGray
    }

    val shadowElevation = if (variant == ButtonVariant.Primary && enabled && !isPressed) 4.dp else 0.dp

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(shadowElevation, CircleShape)
            .clip(CircleShape)
            .background(containerColor)
            .then(
                if (variant == ButtonVariant.Outline) 
                    Modifier.border(BorderStroke(2.dp, if (enabled) PrimaryRed else LightGray), CircleShape)
                else Modifier
            )
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
