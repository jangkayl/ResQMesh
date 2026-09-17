package com.example.testresqmesh.core.ui.components.layout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.core.ui.theme.ResQTheme

/**
 * A clean, elevated white surface used for cards, controls, and the floating navigation dock.
 * It stays intentionally simple and legible on every Android version the app supports.
 */
@Composable
fun ResQGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    shadowElevation: Dp = 16.dp,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = ResQTheme.colors
    Box(
        modifier = modifier
            .shadow(
                elevation = shadowElevation,
                shape = shape,
                ambientColor = colors.glassShadow,
                spotColor = colors.glassShadow
            )
            .clip(shape)
            .background(colors.glassFill)
            .border(
                border = BorderStroke(1.dp, colors.glassBorder),
                shape = shape
            )
            .padding(contentPadding),
        contentAlignment = contentAlignment,
        content = content
    )
}

@Composable
fun ResQAuroraBackground(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = ResQTheme.colors
    Box(
        modifier = modifier.drawBehind {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(colors.backgroundStart, colors.backgroundEnd)
                )
            )
        },
        contentAlignment = contentAlignment,
        content = content
    )
}

@Composable
fun ResQGradientOrb(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.primary)
                )
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}
