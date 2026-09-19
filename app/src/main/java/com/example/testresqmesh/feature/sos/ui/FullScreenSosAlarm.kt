package com.example.testresqmesh.feature.sos.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.utils.MediaHelper
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun FullScreenSosAlarm(alertMessage: ChatMessage, onDismiss: () -> Unit, onViewMap: () -> Unit = {}) {
    val context = LocalContext.current
    val media = remember { MediaHelper(context) }
    DisposableEffect(Unit) {
        media.playEmergencySiren()
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
        }
        if (vibrator.hasVibrator()) {
            val pattern = longArrayOf(0, 500, 250, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, 0)
            }
        }
        onDispose {
            media.stopEmergencySiren()
            vibrator.cancel()
        }
    }

    val red = ResQTheme.colors.sos
    val hasLocation = alertMessage.locationLat != null && alertMessage.locationLng != null

    // Strobing animation for critical lockdown border
    val infiniteTransition = rememberInfiniteTransition(label = "defcon strobe")
    val strobeAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "strobe"
    )

    val radarWave by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "topo wave"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0305))
            .border(4.dp, red.copy(alpha = strobeAlpha))
    ) {
        // Topographical Wireframe Background Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2, h * 0.42f)

            // Draw concentric contour rings
            for (i in 1..6) {
                val radius = (w * 0.18f * i) * (0.85f + 0.15f * radarWave)
                drawCircle(
                    color = red.copy(alpha = (0.08f / i).coerceIn(0.01f, 0.12f)),
                    center = center,
                    radius = radius,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // Draw subtle elevation wave lines across background
            val path = Path()
            val waveCount = 5
            for (line in 0 until waveCount) {
                val yBase = h * 0.2f + line * (h * 0.14f)
                path.reset()
                path.moveTo(0f, yBase)
                val step = w / 20f
                for (j in 0..20) {
                    val x = j * step
                    val yOffset = (sin((x / w * 4 * Math.PI) + (radarWave * 2 * Math.PI) + line).toFloat()) * 18f
                    path.lineTo(x, yBase + yOffset)
                }
                drawPath(
                    path = path,
                    color = red.copy(alpha = 0.05f),
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // Crosshair overlay
            drawLine(
                color = red.copy(alpha = 0.15f),
                start = Offset(center.x, 0f),
                end = Offset(center.x, h),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = red.copy(alpha = 0.15f),
                start = Offset(0f, center.y),
                end = Offset(w, center.y),
                strokeWidth = 1.dp.toPx()
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.Large, vertical = Spacing.ExtraLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Banner
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = red.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, red)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = red,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "DEFCON 1 // EMERGENCY OVERRIDE",
                            color = red,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            letterSpacing = 1.5.sp
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "CRITICAL SOS INCOMING",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
                Text(
                    "HIGH-PRIORITY MESH FLOOD ALERT",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.55f),
                    letterSpacing = 1.sp
                )
            }

            // Sender & Alert Payload Card with Tactical Lock-On HUD
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                // Tactical HUD Corner Reticles
                Canvas(modifier = Modifier.matchParentSize()) {
                    val stroke = 2.5.dp.toPx()
                    val len = 20.dp.toPx()
                    val c = red.copy(alpha = strobeAlpha)

                    // Top Left
                    drawLine(c, Offset(0f, 0f), Offset(len, 0f), stroke, StrokeCap.Square)
                    drawLine(c, Offset(0f, 0f), Offset(0f, len), stroke, StrokeCap.Square)
                    // Top Right
                    drawLine(c, Offset(size.width, 0f), Offset(size.width - len, 0f), stroke, StrokeCap.Square)
                    drawLine(c, Offset(size.width, 0f), Offset(size.width, len), stroke, StrokeCap.Square)
                    // Bottom Left
                    drawLine(c, Offset(0f, size.height), Offset(len, size.height), stroke, StrokeCap.Square)
                    drawLine(c, Offset(0f, size.height), Offset(0f, size.height - len), stroke, StrokeCap.Square)
                    // Bottom Right
                    drawLine(c, Offset(size.width, size.height), Offset(size.width - len, size.height), stroke, StrokeCap.Square)
                    drawLine(c, Offset(size.width, size.height), Offset(size.width, size.height - len), stroke, StrokeCap.Square)
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1C0609).copy(alpha = 0.92f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, red.copy(alpha = 0.4f)),
                    shadowElevation = 16.dp
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Sender Node Badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = red.copy(alpha = 0.25f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = red,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    alertMessage.senderName.uppercase(),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Text(
                                    "ORIGINATING NODE",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = red.copy(alpha = 0.9f)
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = red.copy(alpha = 0.2f))
                        Spacer(Modifier.height(16.dp))

                        // Distress Message Content
                        Text(
                            text = alertMessage.text,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            color = Color.White,
                            lineHeight = 22.sp
                        )

                        Spacer(Modifier.height(18.dp))

                        // Telemetry Tag
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (hasLocation) Color(0xFF003822).copy(alpha = 0.7f) else Color.White.copy(alpha = 0.08f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (hasLocation) Color(0xFF00E676).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.15f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (hasLocation) Icons.Default.GpsFixed else Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = if (hasLocation) Color(0xFF00E676) else Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (hasLocation) {
                                        "COORDINATES ACQUIRED: %.4f, %.4f".format(alertMessage.locationLat, alertMessage.locationLng)
                                    } else {
                                        "GPS TELEMETRY UNAVAILABLE"
                                    },
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (hasLocation) Color(0xFF00E676) else Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }

            // Action Controls
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (hasLocation) {
                    Button(
                        onClick = onViewMap,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2B090E),
                            contentColor = Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, red.copy(alpha = 0.7f))
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, tint = red)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "PLOT TARGET ON TACTICAL MAP",
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Industrial Tactical Slider: "SLIDE TO ACKNOWLEDGE"
                AcknowledgeSlideSlider(
                    onAcknowledge = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun AcknowledgeSlideSlider(
    onAcknowledge: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    var progress by remember { mutableStateOf(0f) }
    var completed by remember { mutableStateOf(false) }
    var halfHapticFired by remember { mutableStateOf(false) }

    fun complete() {
        if (!completed) {
            completed = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onAcknowledge()
        }
    }

    val containerShape = RoundedCornerShape(28.dp)
    val sliderTrackBackground = Brush.horizontalGradient(
        listOf(
            Color(0xFF260508),
            Color(0xFF42090F),
            Color(0xFF1B0305)
        )
    )

    BoxWithConstraints(
        modifier = modifier
            .height(64.dp)
            .clip(containerShape)
            .background(sliderTrackBackground)
            .border(1.5.dp, ResQTheme.colors.sos.copy(alpha = 0.8f), containerShape)
            .shadow(12.dp, containerShape, ambientColor = ResQTheme.colors.sos),
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val thumbTravelPx = with(density) {
            (maxWidth - 58.dp).coerceAtLeast(1.dp).toPx()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
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
                            progress = (progress + dragAmount.x / thumbTravelPx).coerceIn(0f, 1f)
                            if (progress >= 0.5f && !halfHapticFired) {
                                halfHapticFired = true
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            if (progress >= 0.88f) complete()
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // Fill background with glowing red as thumb moves
            if (progress > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(ResQTheme.colors.sos.copy(alpha = 0.5f), ResQTheme.colors.sos)
                            )
                        )
                )
            }

            // Slider Label
            Text(
                text = if (progress >= 0.85f) "RELEASE TO SILENCE" else "SLIDE TO ACKNOWLEDGE",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp)
                    .graphicsLayer {
                        alpha = if (progress >= 0.85f) 1f else (1f - progress * 1.2f).coerceIn(0.2f, 1f)
                    },
                textAlign = TextAlign.Center,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                letterSpacing = 1.sp
            )

            // Slider Thumb
            Surface(
                modifier = Modifier
                    .offset { IntOffset((progress * thumbTravelPx).roundToInt(), 0) }
                    .size(52.dp)
                    .padding(4.dp),
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = ResQTheme.colors.sos,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
