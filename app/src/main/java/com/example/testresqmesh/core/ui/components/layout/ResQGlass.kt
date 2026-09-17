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
 * Cross-version frosted-glass surface. The translucent gradient, highlight border, and soft
 * shadow are intentionally stable back to API 24; screens can add richer effects later without
 * making readability or layout depend on Android 12-only blur support.
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
            .background(
                Brush.linearGradient(
                    colors = listOf(colors.glassFill, colors.glassTint)
                )
            )
            .border(
                border = BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(colors.glassBorder, colors.glassBorder.copy(alpha = 0.28f))
                    )
                ),
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
            val primaryRadius = size.minDimension * 0.72f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.glowPrimary, colors.glowPrimary.copy(alpha = 0f)),
                    center = Offset(size.width * 0.12f, size.height * 0.12f),
                    radius = primaryRadius
                ),
                radius = primaryRadius,
                center = Offset(size.width * 0.12f, size.height * 0.12f)
            )
            val secondaryRadius = size.minDimension * 0.66f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.glowSecondary, colors.glowSecondary.copy(alpha = 0f)),
                    center = Offset(size.width * 0.88f, size.height * 0.72f),
                    radius = secondaryRadius
                ),
                radius = secondaryRadius,
                center = Offset(size.width * 0.88f, size.height * 0.72f)
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
