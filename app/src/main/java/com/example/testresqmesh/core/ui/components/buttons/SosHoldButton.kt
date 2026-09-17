package com.example.testresqmesh.core.ui.components.buttons

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.R
import com.example.testresqmesh.core.ui.theme.ResQSize
import com.example.testresqmesh.core.ui.theme.ResQTheme

private const val SOS_HOLD_DURATION_MILLIS = 2_000

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
