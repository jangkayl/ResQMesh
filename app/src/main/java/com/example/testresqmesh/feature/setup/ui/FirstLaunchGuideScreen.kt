package com.example.testresqmesh.feature.setup.ui

import android.animation.ValueAnimator
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private data class GuidePage(@param:StringRes val title: Int, @param:StringRes val body: Int, @param:StringRes val note: Int)
private val pages = listOf(
    GuidePage(R.string.guide_purpose_title, R.string.guide_purpose_body, R.string.guide_purpose_note),
    GuidePage(R.string.guide_use_title, R.string.guide_use_body, R.string.guide_use_note),
    GuidePage(R.string.guide_sos_title, R.string.guide_sos_body, R.string.guide_sos_note)
)

@Composable
fun FirstLaunchGuideScreen(isReplay: Boolean, onDone: () -> Unit, onClose: () -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val accent = if (index == 2) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    BackHandler(enabled = index > 0 || isReplay) { if (index > 0) index-- else onClose() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
                    .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    pages.indices.forEach { step ->
                        Box(Modifier.size(if (step == index) 10.dp else 8.dp).clip(CircleShape)
                            .background(if (step == index) accent else MaterialTheme.colorScheme.outline))
                    }
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { if (index == pages.lastIndex) onDone() else index++ },
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = CircleShape
                ) {
                    Text(
                        stringResource(if (index == pages.lastIndex) {
                            if (isReplay) R.string.guide_done else R.string.guide_start_setup
                        } else R.string.guide_next),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val heroHeight = (maxHeight * 0.43f).coerceIn(190.dp, 350.dp)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onClose) {
                        Text(stringResource(if (isReplay) R.string.guide_close else R.string.guide_skip))
                    }
                }
                GuideIllustration(index, heroHeight, accent)
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.guide_progress, index + 1, pages.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(pages[index].title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(pages[index].body),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(pages[index].note),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun GuideIllustration(index: Int, height: Dp, accent: Color) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface
    val bubble = MaterialTheme.colorScheme.surfaceVariant
    val foreground = MaterialTheme.colorScheme.onSurfaceVariant
    val animationsEnabled = rememberAnimationsEnabled()
    Box(Modifier.fillMaxWidth().height(height).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        if (animationsEnabled) {
            key(index) { AnimatedGuideCanvas(index, accent, outline, surface, bubble, foreground) }
        } else {
            GuideCanvas(index, accent, outline, surface, bubble, foreground, phase = if (index == 1) 0.92f else 0.35f)
            GuideCenterpiece(index, accent, phase = 0.35f)
        }
    }
}

@Composable
private fun AnimatedGuideCanvas(
    index: Int,
    accent: Color,
    outline: Color,
    surface: Color,
    bubble: Color,
    foreground: Color
) {
    val transition = rememberInfiniteTransition(label = "guide-illustration")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (index == 1) 4200 else if (index == 0) 3600 else 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "guide-illustration-phase"
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GuideCanvas(index, accent, outline, surface, bubble, foreground, phase)
        GuideCenterpiece(index, accent, phase)
    }
}

@Composable
private fun GuideCenterpiece(index: Int, accent: Color, phase: Float) {
    when (index) {
        0 -> Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
            val pulse = 1f + 0.07f * sin(phase * 2f * Math.PI.toFloat())
            Image(
                painter = painterResource(R.drawable.resqmesh_logo),
                contentDescription = null,
                modifier = Modifier.size(70.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }
            )
            Icon(
                Icons.Outlined.Bluetooth,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.align(Alignment.BottomEnd).size(28.dp)
                    .background(MaterialTheme.colorScheme.background, CircleShape)
            )
        }
        2 -> Icon(Icons.Outlined.WarningAmber, null, tint = accent, modifier = Modifier.size(50.dp))
    }
}

@Composable
private fun rememberAnimationsEnabled(): Boolean = androidx.compose.runtime.remember {
    Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
}

@Composable
private fun GuideCanvas(
    index: Int,
    accent: Color,
    outline: Color,
    surface: Color,
    bubble: Color,
    foreground: Color,
    phase: Float
) {
    Canvas(Modifier.fillMaxSize()) {
        val scale = min(size.width / 320f, size.height / 320f)
        val cx = size.width / 2f
        val cy = size.height / 2f
        fun p(x: Float, y: Float) = Offset(cx + x * scale, cy + y * scale)
        fun ring(r: Float, color: Color) = drawCircle(color, r * scale, p(0f, 0f), style = Stroke(1.5f * scale))
        fun dot(x: Float, y: Float, r: Float, color: Color) = drawCircle(color, r * scale, p(x, y))
        when (index) {
            0 -> {
                ring(122f, outline); ring(82f, outline)
                dot(0f, 0f, 48f, accent.copy(alpha = 0.10f))
                val angle = phase * (2f * Math.PI.toFloat())
                val left = p(-102f + 10f * sin(angle), -50f + 8f * cos(angle))
                val right = p(104f + 10f * cos(angle), 52f + 8f * sin(angle))
                drawLine(accent.copy(alpha = 0.48f), left, p(0f, 0f), 2.5f * scale)
                drawLine(accent.copy(alpha = 0.48f), p(0f, 0f), right, 2.5f * scale)
                listOf(left, right).forEach { peer ->
                    drawCircle(surface, 21f * scale, peer)
                    drawCircle(accent, 21f * scale, peer, style = Stroke(2f * scale))
                    drawCircle(accent, 8f * scale, peer)
                }
                val firstHalf = (phase * 2f).coerceIn(0f, 1f)
                val secondHalf = ((phase - 0.5f) * 2f).coerceIn(0f, 1f)
                val packet = if (phase < 0.5f) {
                    Offset(left.x + (cx - left.x) * firstHalf, left.y + (cy - left.y) * firstHalf)
                } else {
                    Offset(cx + (right.x - cx) * secondHalf, cy + (right.y - cy) * secondHalf)
                }
                drawCircle(accent.copy(alpha = 0.22f), 16f * scale, packet)
                drawCircle(accent, 7f * scale, packet)
            }
            1 -> {
                drawRoundRect(outline, p(-102f, -135f), Size(204f * scale, 270f * scale), CornerRadius(28f * scale), style = Stroke(2f * scale))
                drawLine(outline, p(-20f, -122f), p(20f, -122f), 3f * scale)
                val outgoing = (phase / 0.15f).coerceIn(0f, 1f)
                val typing = ((phase - 0.22f) / 0.08f).coerceIn(0f, 1f) * (1f - ((phase - 0.55f) / 0.08f).coerceIn(0f, 1f))
                val incoming = ((phase - 0.57f) / 0.12f).coerceIn(0f, 1f)
                val followup = ((phase - 0.76f) / 0.12f).coerceIn(0f, 1f)
                drawMessageBubble(p(0f + (1f - outgoing) * 24f, -75f), 82f, 43f, accent.copy(alpha = outgoing))
                drawLine(Color.White.copy(alpha = 0.7f * outgoing), p(20f + (1f - outgoing) * 24f, -59f), p(68f + (1f - outgoing) * 24f, -59f), 2.4f * scale)
                drawLine(Color.White.copy(alpha = 0.5f * outgoing), p(20f + (1f - outgoing) * 24f, -48f), p(53f + (1f - outgoing) * 24f, -48f), 2.4f * scale)
                drawMessageBubble(p(-82f, -12f), 62f, 39f, bubble.copy(alpha = typing))
                repeat(3) { dotIndex ->
                    val bounce = (0.5f + 0.5f * sin(phase * 28f + dotIndex * 1.6f)) * 5f
                    dot(-66f + dotIndex * 15f, 8f - bounce, 3.5f, foreground.copy(alpha = typing))
                }
                drawMessageBubble(p(-82f - (1f - incoming) * 24f, -12f), 102f, 45f, bubble.copy(alpha = incoming))
                drawLine(foreground.copy(alpha = 0.65f * incoming), p(-65f - (1f - incoming) * 24f, 3f), p(2f - (1f - incoming) * 24f, 3f), 2.4f * scale)
                drawLine(foreground.copy(alpha = 0.45f * incoming), p(-65f - (1f - incoming) * 24f, 16f), p(-18f - (1f - incoming) * 24f, 16f), 2.4f * scale)
                drawMessageBubble(p(8f + (1f - followup) * 24f, 56f), 74f, 39f, accent.copy(alpha = followup))
                drawLine(Color.White.copy(alpha = 0.7f * followup), p(26f + (1f - followup) * 24f, 75f), p(64f + (1f - followup) * 24f, 75f), 2.4f * scale)
            }
            else -> {
                ring(122f, outline); ring(88f, outline)
                dot(0f, 0f, 55f, accent.copy(alpha = 0.13f))
                val radius = 48f + 76f * phase
                drawCircle(accent.copy(alpha = 0.72f * (1f - phase)), radius * scale, p(0f, 0f), style = Stroke(2f * scale))
                drawCircle(accent.copy(alpha = 0.22f), 44f * scale, p(0f, 0f), style = Stroke(2f * scale))
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMessageBubble(
    topLeft: Offset,
    width: Float,
    height: Float,
    color: Color
) {
    val scale = min(size.width / 320f, size.height / 320f)
    drawRoundRect(color, topLeft, Size(width * scale, height * scale), CornerRadius(18f * scale))
}
