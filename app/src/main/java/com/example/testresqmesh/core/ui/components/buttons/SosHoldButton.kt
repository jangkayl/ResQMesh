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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
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

private const val SOS_HOLD_DURATION_MILLIS = 2_000

/**
 * The active SOS flow uses a horizontal slide instead of a timed hold. The old
 * [SosHoldButton] remains available for legacy entry points while this control
 * offers a clearer, deliberate one-motion confirmation.
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
    val density = LocalDensity.current
    val instruction = stringResource(R.string.sos_slide_instruction)
    val progressDescription = stringResource(R.string.sos_slide_progress, (progress * 100).toInt())
    val actionLabel = stringResource(R.string.sos_slide_action)

    fun complete() {
        if (!completed && enabled) {
            completed = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            currentOnSlideComplete()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(if (enabled) ResQTheme.colors.sos else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
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
            }
            .pointerInput(enabled) {
                detectDragGestures(
                    onDragStart = { completed = false },
                    onDragCancel = { if (!completed) progress = 0f },
                    onDragEnd = {
                        if (progress >= 0.9f) complete()
                        if (!completed) progress = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (enabled) {
                            val trackWidth = size.width.toFloat().coerceAtLeast(with(density) { 1.dp.toPx() })
                            progress = (progress + dragAmount.x / trackWidth).coerceIn(0f, 1f)
                            if (progress >= 0.9f) complete()
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = 0.16f))
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = 0.22f))
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(56.dp),
                shape = CircleShape,
                color = Color.White,
                contentColor = ResQTheme.colors.sos
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
                }
            }
            Text(
                text = instruction,
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
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
