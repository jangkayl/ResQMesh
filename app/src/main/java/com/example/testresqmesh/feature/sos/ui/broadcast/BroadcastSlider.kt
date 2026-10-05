package com.example.testresqmesh.feature.sos.ui.broadcast

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun SlideToBroadcastSlider(
    accentColor: Color,
    emergencyTitle: String,
    isLight: Boolean,
    onConfirm: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val thumbSizeDp = 58.dp
    val thumbSizePx = with(density) { thumbSizeDp.toPx() }
    val trackPaddingPx = with(density) { 5.dp.toPx() }

    val maxDragPx = (trackWidthPx - thumbSizePx - (trackPaddingPx * 2)).coerceAtLeast(0f)
    var offsetX by remember { mutableFloatStateOf(0f) }

    var passed25 by remember { mutableStateOf(false) }
    var passed50 by remember { mutableStateOf(false) }
    var passed75 by remember { mutableStateOf(false) }

    val dragProgress = if (maxDragPx > 0f) (offsetX / maxDragPx).coerceIn(0f, 1f) else 0f

    // Animated chevron hint pulse
    val infiniteTransition = rememberInfiniteTransition(label = "chevronPulse")
    val chevronAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chevronAlpha"
    )

    val trackBgColor = if (isLight) Color.White else Color(0xFF19191C)
    val trackBorderColor = if (isLight) (if (dragProgress > 0.1f) accentColor.copy(alpha = 0.6f) else Color(0xFFDCD5CE)) else Color(0xFF303036)
    val trackLabelColor = if (isLight) Color(0xFF181817) else Color.White

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .shadow(elevation = if (isLight) 6.dp else 0.dp, shape = CircleShape)
            .clip(CircleShape)
            .background(trackBgColor)
            .border(BorderStroke(1.5.dp, trackBorderColor), CircleShape)
            .onGloballyPositioned { coordinates ->
                trackWidthPx = coordinates.size.width.toFloat()
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Drag Trail Fill
        if (offsetX > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(with(density) { (offsetX + thumbSizePx + trackPaddingPx).toDp() })
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                accentColor.copy(alpha = 0.25f),
                                accentColor.copy(alpha = 0.65f)
                            )
                        )
                    )
            )
        }

        // Center Track Label
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha((1f - dragProgress * 1.6f).coerceIn(0f, 1f)),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "SLIDE TO BROADCAST SOS",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    letterSpacing = 1.5.sp,
                    color = trackLabelColor.copy(alpha = chevronAlpha)
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = accentColor.copy(alpha = chevronAlpha),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Draggable Knob Thumb
        Box(
            modifier = Modifier
                .padding(start = 5.dp)
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .size(thumbSizeDp)
                .shadow(elevation = 8.dp, shape = CircleShape, ambientColor = accentColor, spotColor = accentColor)
                .clip(CircleShape)
                .background(
                    if (dragProgress > 0.8f) accentColor else Color.White
                )
                .then(
                    if (isLight && dragProgress <= 0.8f) {
                        Modifier.border(BorderStroke(2.dp, accentColor.copy(alpha = 0.4f)), CircleShape)
                    } else Modifier
                )
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        val newOffset = (offsetX + delta).coerceIn(0f, maxDragPx)
                        offsetX = newOffset

                        val p = if (maxDragPx > 0f) newOffset / maxDragPx else 0f
                        if (p > 0.25f && !passed25) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            passed25 = true
                        }
                        if (p > 0.50f && !passed50) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            passed50 = true
                        }
                        if (p > 0.75f && !passed75) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            passed75 = true
                        }
                    },
                    onDragStopped = {
                        if (maxDragPx > 0f && offsetX >= maxDragPx * 0.82f) {
                            // Successfully confirmed
                            coroutineScope.launch {
                                val anim = Animatable(offsetX)
                                anim.animateTo(maxDragPx, tween(80, easing = LinearEasing))
                                offsetX = maxDragPx
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onConfirm()
                            }
                        } else {
                            // Spring back to start
                            coroutineScope.launch {
                                val anim = Animatable(offsetX)
                                anim.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                                offsetX = 0f
                                passed25 = false
                                passed50 = false
                                passed75 = false
                            }
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Slide Arrow",
                tint = if (dragProgress > 0.8f) Color.White else accentColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
