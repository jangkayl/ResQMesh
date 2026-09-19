package com.example.testresqmesh.core.ui.components.buttons

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import com.example.testresqmesh.R
import com.example.testresqmesh.core.ui.theme.ResQSize
import com.example.testresqmesh.core.ui.theme.ResQTheme
import kotlin.math.roundToInt

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Brush

private const val SOS_HOLD_DURATION_MILLIS = 2_000

/**
 * Modernized tactical SOS slider with an animated glowing trail, directional flow,
 * dynamic feedback, and tactile thumb physics.
 */
@Composable
fun SosSlideToSend(
    onSlideComplete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptics = LocalHapticFeedback.current
    val currentOnSlideComplete by rememberUpdatedState(onSlideComplete)
    var progress by remember { mutableStateOf(0f) }
    var completed by remember { mutableStateOf(false) }
    var halfHapticFired by remember { mutableStateOf(false) }
    val instruction = stringResource(R.string.sos_slide_instruction)
    val progressDescription = stringResource(R.string.sos_slide_progress, (progress * 100).toInt())
    val actionLabel = stringResource(R.string.sos_slide_action)

    val infiniteTransition = rememberInfiniteTransition(label = "sos slider pulse")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sos glow"
    )
    val arrowPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "arrow flow"
    )

    fun complete() {
        if (!completed && enabled) {
            completed = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            currentOnSlideComplete()
        }
    }

    val containerShape = RoundedCornerShape(36.dp)
    val trackBackground = Brush.horizontalGradient(
        listOf(
            Color(0xFF1F0B0E),
            Color(0xFF2C0F13),
            Color(0xFF381318)
        )
    )
    val glowingTrailGradient = Brush.horizontalGradient(
        listOf(
            Color(0xFFFF334B).copy(alpha = 0.85f),
            Color(0xFFFF5E3A).copy(alpha = 0.92f),
            Color(0xFFFF9F1C)
        )
    )

    val isNearEnd = progress >= 0.82f
    val thumbScale by animateFloatAsState(
        targetValue = if (isNearEnd) 1.08f else if (progress > 0.05f) 1.04f else 1f,
        animationSpec = spring(stiffness = 600f),
        label = "thumb scale"
    )

    val disabledBackground = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .shadow(
                elevation = if (enabled) 12.dp else 0.dp,
                shape = containerShape,
                ambientColor = ResQTheme.colors.sos.copy(alpha = pulseGlow * 0.45f),
                spotColor = ResQTheme.colors.sos.copy(alpha = pulseGlow * 0.65f)
            )
            .clip(containerShape)
            .background(if (enabled) trackBackground else disabledBackground)
            .border(
                BorderStroke(
                    width = 1.5.dp,
                    color = if (enabled) ResQTheme.colors.sos.copy(alpha = pulseGlow * 0.75f)
                    else MaterialTheme.colorScheme.outlineVariant
                ),
                shape = containerShape
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = instruction
                stateDescription = progressDescription
                if (enabled) {
                    onClick(label = actionLabel) {
                        complete()
                        true
                    }
                } else disabled()
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val thumbTravelPx = with(density) {
            (maxWidth - 64.dp).coerceAtLeast(1.dp).toPx()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled, thumbTravelPx) {
                    detectDragGestures(
                        onDragStart = {
                            completed = false
                            halfHapticFired = false
                            progress = 0f
                        },
                        onDragCancel = {
                            if (!completed) progress = 0f
                            halfHapticFired = false
                        },
                        onDragEnd = {
                            if (progress >= 0.88f) complete()
                            if (!completed) progress = 0f
                            halfHapticFired = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (enabled) {
                                progress = (progress + dragAmount.x / thumbTravelPx).coerceIn(0f, 1f)
                                if (progress >= 0.5f && !halfHapticFired) {
                                    halfHapticFired = true
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                if (progress >= 0.88f) complete()
                            }
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            ) {
                // Background track layer
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                )

                // Animated glowing trail that expands as thumb drags
                if (progress > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .clip(RoundedCornerShape(32.dp))
                            .background(glowingTrailGradient)
                    )
                }

                // Shimmering chevrons indicating slide direction
                if (progress < 0.72f && enabled) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = 24.dp)
                            .graphicsLayer { alpha = (1f - progress * 1.3f).coerceIn(0f, 0.75f) },
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        (0..2).forEach { index ->
                            val shiftedPhase = (arrowPhase + index * 0.33f) % 1f
                            val chevronAlpha = (0.2f + 0.8f * (1f - shiftedPhase)).coerceIn(0.2f, 1f)
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.White.copy(alpha = chevronAlpha)
                            )
                        }
                    }
                }

                // Dynamic guidance label
                Text(
                    text = if (isNearEnd) "RELEASE TO CONFIRM" else instruction,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 48.dp)
                        .graphicsLayer {
                            alpha = if (isNearEnd) 1f else (1f - progress * 1.2f).coerceIn(0.2f, 1f)
                        },
                    color = if (isNearEnd) Color(0xFFFFD166) else Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )

                // Tactile Draggable Slider Thumb
                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset((progress * thumbTravelPx).roundToInt(), 0) }
                        .size(56.dp)
                        .graphicsLayer {
                            scaleX = thumbScale
                            scaleY = thumbScale
                        },
                    shape = CircleShape,
                    color = if (isNearEnd) Color(0xFFFFD166) else Color.White,
                    contentColor = if (isNearEnd) Color(0xFF900C3F) else ResQTheme.colors.sos,
                    shadowElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isNearEnd) Icons.Default.Warning else Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SosHoldButton(
    onHoldComplete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptics = LocalHapticFeedback.current
    val currentOnHoldComplete by rememberUpdatedState(onHoldComplete)
    val progress = remember { Animatable(0f) }
    var pressed by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(stiffness = 520f, dampingRatio = 0.72f),
        label = "SOS press scale"
    )
    val instruction = stringResource(R.string.sos_hold_instruction)
    val accessibilityAction = stringResource(R.string.sos_accessibility_action)
    val progressDescription = stringResource(
        R.string.sos_hold_progress,
        (progress.value * 100).toInt()
    )

    fun completeHold() {
        if (!completed && enabled) {
            completed = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            currentOnHoldComplete()
        }
    }

    LaunchedEffect(pressed, enabled) {
        if (pressed && enabled) {
            completed = false
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(SOS_HOLD_DURATION_MILLIS, easing = LinearEasing)
            )
            if (pressed) completeHold()
        } else {
            progress.snapTo(0f)
            completed = false
        }
    }

    Box(
        modifier = modifier
            .size(ResQSize.SosAction)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .shadow(
                elevation = 16.dp,
                shape = CircleShape,
                ambientColor = ResQTheme.colors.sos.copy(alpha = 0.32f),
                spotColor = ResQTheme.colors.sos.copy(alpha = 0.38f)
            )
            .clip(CircleShape)
            .background(
                if (enabled) ResQTheme.colors.sos
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = instruction
                stateDescription = progressDescription
                if (enabled) {
                    onLongClick(label = accessibilityAction) {
                        completeHold()
                        true
                    }
                } else {
                    disabled()
                }
            }
            .pointerInput(enabled) {
                detectTapGestures(
                    onPress = {
                        if (!enabled) return@detectTapGestures
                        pressed = true
                        try {
                            tryAwaitRelease()
                        } finally {
                            pressed = false
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawArc(
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.95f),
                startAngle = -90f,
                sweepAngle = 360f * progress.value,
                useCenter = false,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Text(
            text = stringResource(R.string.sos),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (enabled) ResQTheme.colors.onSos
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }
}
