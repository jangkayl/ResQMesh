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
 * A restrained raised surface for key controls, summaries, and the navigation dock.
 * It deliberately uses opaque themed layers instead of a platform blur so critical text
 * remains readable in daylight and on dark field displays.
 */
@Composable
fun ResQGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    shadowElevation: Dp = 6.dp,
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

/**
 * A solid, high-contrast card for information that must stay readable at a glance.
 * Use this for messages, connectivity, forms, and SOS information; reserve glass for
 * navigation, overlays, and high-level summaries.
 */
@Composable
fun ResQContentSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    shadowElevation: Dp = 2.dp,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(elevation = shadowElevation, shape = shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
            drawRect(brush = Brush.verticalGradient(colors = listOf(colors.backgroundStart, colors.backgroundEnd)))
            // Decorative signal field only: it never represents device range or topology.
            drawCircle(colors.glowPrimary.copy(alpha = 0.055f), radius = size.minDimension * 0.62f, center = Offset(size.width * 1.05f, size.height * 0.03f))
            drawCircle(colors.glowSecondary.copy(alpha = 0.04f), radius = size.minDimension * 0.48f, center = Offset(size.width * -0.08f, size.height * 0.72f))
        },
        contentAlignment = contentAlignment,
        content = content
    )
}

/** A focused, solid emergency canvas. Red is reserved for actual SOS and failure flows. */
@Composable
fun ResQSosBackground(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = ResQTheme.colors
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier.drawBehind {
            drawRect(brush = Brush.verticalGradient(listOf(colors.sosContainer, background)))
            drawCircle(colors.sos.copy(alpha = 0.08f), size.minDimension * 0.72f, Offset(size.width * 0.5f, size.height * 0.08f))
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
