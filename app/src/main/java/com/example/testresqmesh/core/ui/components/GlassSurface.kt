package com.example.testresqmesh.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    intensity: Float = 0.5f,
    content: @Composable () -> Unit
) {
    // Glassmorphism effect: surface background with an alpha and a subtle gradient border
    val backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = intensity)
    val borderColor = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.15f),
            Color.White.copy(alpha = 0.05f),
            Color.Transparent,
            Color.White.copy(alpha = 0.02f)
        )
    )

    Surface(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, borderColor, shape),
        color = Color.Transparent,
        content = content
    )
}
