package com.example.testresqmesh.feature.sos.ui.broadcast

import com.example.testresqmesh.feature.sos.ui.EmergencyProfile
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun BroadcastIllumination(
    currentProfile: EmergencyProfile,
    isLight: Boolean,
    pulseGlow: Float,
    lightHaloScale: Float,
    animatedAccentColor: Color
) {
    // Multi-Layered Emergency Light Illumination & Tactical Radar System (Light & Dark)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1. Base Gradient Layer
        drawRect(
            brush = Brush.verticalGradient(
                colors = if (isLight) {
                    listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFF8FAFC),
                        Color(0xFFF1F5F9)
                    )
                } else {
                    listOf(
                        Color(0xFF0F131C),
                        Color(0xFF0A0C10),
                        Color(0xFF06080C)
                    )
                }
            )
        )

        // 2. Primary High-Luminance Top-Centered Emergency Sunburst / Light Flare
        val topGlowAlpha = if (isLight) pulseGlow else pulseGlow * 0.85f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    animatedAccentColor.copy(alpha = topGlowAlpha),
                    animatedAccentColor.copy(alpha = topGlowAlpha * 0.45f),
                    Color.Transparent
                ),
                center = Offset(w / 2f, h * 0.20f),
                radius = w * 1.05f * lightHaloScale
            )
        )

        // 3. Wide Ambient Emergency Wash illuminating mid and lower screen
        val ambientAlpha = if (isLight) pulseGlow * 0.35f else pulseGlow * 0.40f
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    animatedAccentColor.copy(alpha = ambientAlpha),
                    animatedAccentColor.copy(alpha = ambientAlpha * 0.35f),
                    Color.Transparent
                ),
                center = Offset(w / 2f, h * 0.55f),
                radius = w * 1.35f
            )
        )

        // 4. Subtle Radial Light Anchor behind Slide to Broadcast area
        val anchorAlpha = if (isLight) pulseGlow * 0.25f else pulseGlow * 0.35f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    animatedAccentColor.copy(alpha = anchorAlpha),
                    Color.Transparent
                ),
                center = Offset(w / 2f, h * 0.88f),
                radius = w * 0.75f
            )
        )

        // 5. Distinct Luminous Beacon Rings (expanding emergency waves)
        val centerOffset = Offset(w / 2f, h * 0.38f)
        val ringRadii = listOf(w * 0.32f, w * 0.60f, w * 0.90f)
        ringRadii.forEachIndexed { index, radius ->
            val ringAlpha = if (isLight) {
                (0.18f - index * 0.04f).coerceAtLeast(0.08f)
            } else {
                (0.24f - index * 0.05f).coerceAtLeast(0.10f)
            }
            drawCircle(
                color = animatedAccentColor.copy(alpha = ringAlpha),
                radius = radius,
                center = centerOffset,
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                )
            )
        }

        // 6. Tactical Reticle Crosshairs with glowing focal tick marks
        val reticleAlpha = if (isLight) 0.14f else 0.18f
        drawLine(
            color = animatedAccentColor.copy(alpha = reticleAlpha),
            start = Offset(w * 0.08f, centerOffset.y),
            end = Offset(w * 0.92f, centerOffset.y),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 14f), 0f)
        )
        drawLine(
            color = animatedAccentColor.copy(alpha = reticleAlpha),
            start = Offset(centerOffset.x, h * 0.10f),
            end = Offset(centerOffset.x, h * 0.70f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 14f), 0f)
        )
    }

}
