package com.example.testresqmesh.feature.comms.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.utils.MediaHelper
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.components.layout.ResQGlassSurface
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel
import com.example.testresqmesh.feature.comms.viewmodel.WalkieTalkieViewModel
import kotlin.math.cos
import kotlin.math.sin

import androidx.compose.ui.graphics.luminance

@Composable
fun WalkieTalkieScreen(
    commsViewModel: CommunicationViewModel,
    walkieTalkieViewModel: WalkieTalkieViewModel,
    mediaHelper: MediaHelper
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val receiverOn by walkieTalkieViewModel.isWalkieTalkieMode.collectAsState()
    val channel by walkieTalkieViewModel.currentChannelId.collectAsState()
    val speaker by walkieTalkieViewModel.currentSpeaker.collectAsState()
    var recording by remember { mutableStateOf(false) }
    var liveAudio by remember { mutableStateOf(false) }
    var channelsOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    // Animations for idle and active states
    val infiniteTransition = rememberInfiniteTransition(label = "walkie talkie fx")
    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle pulse"
    )
    val waveScale1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave 1"
    )
    val waveScale2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, delayMillis = 250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave 2"
    )
    val waveAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave alpha"
    )

    // VU meter bar animations while recording
    val bar1 by infiniteTransition.animateFloat(initialValue = 8f, targetValue = 28f, animationSpec = infiniteRepeatable(tween(220), RepeatMode.Reverse), label = "b1")
    val bar2 by infiniteTransition.animateFloat(initialValue = 14f, targetValue = 36f, animationSpec = infiniteRepeatable(tween(180), RepeatMode.Reverse), label = "b2")
    val bar3 by infiniteTransition.animateFloat(initialValue = 10f, targetValue = 32f, animationSpec = infiniteRepeatable(tween(260), RepeatMode.Reverse), label = "b3")
    val bar4 by infiniteTransition.animateFloat(initialValue = 16f, targetValue = 38f, animationSpec = infiniteRepeatable(tween(190), RepeatMode.Reverse), label = "b4")
    val bar5 by infiniteTransition.animateFloat(initialValue = 6f, targetValue = 24f, animationSpec = infiniteRepeatable(tween(240), RepeatMode.Reverse), label = "b5")

    val pressScale by animateFloatAsState(
        targetValue = if (recording) 0.94f else 1f,
        animationSpec = spring(stiffness = 550f, dampingRatio = 0.65f),
        label = "ptt scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.Medium),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(Spacing.Large))

        // Screen Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Tactical Voice",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Channel $channel PTT Broadcast",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    onClick = { channelsOpen = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Radio,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("CH $channel", fontWeight = FontWeight.Black)
                    }
                }
                DropdownMenu(expanded = channelsOpen, onDismissRequest = { channelsOpen = false }) {
                    (1..5).forEach { number ->
                        DropdownMenuItem(
                            text = { Text("Channel $number") },
                            onClick = {
                                walkieTalkieViewModel.setChannel(number.toString())
                                channelsOpen = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.Medium))

        // Voice Receiver Status Panel
        ResQGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            contentPadding = PaddingValues(Spacing.Medium),
            shadowElevation = 8.dp
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (receiverOn) ResQTheme.colors.success.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.GraphicEq,
                                contentDescription = null,
                                tint = if (receiverOn) ResQTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Radio Monitor", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (receiverOn) "Active listener on CH $channel" else "Standby (Radio Muted)",
                            color = if (receiverOn) ResQTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = receiverOn, onCheckedChange = { walkieTalkieViewModel.toggleWalkieTalkieMode() })
                }
                if (speaker != null && receiverOn) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ResQTheme.colors.success.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(ResQTheme.colors.success)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "INCOMING RX: $speaker",
                                fontWeight = FontWeight.ExtraBold,
                                color = ResQTheme.colors.success,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.Small))

        // Mode Selector
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Burst Note",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Switch(
                    checked = false,
                    onCheckedChange = { /* Disabled: Planned feature */ },
                    enabled = false,
                    modifier = Modifier.padding(horizontal = 8.dp).scale(0.85f)
                )
                Text(
                    text = "Live Stream (Planned)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Active Status & Equalizer Display
        Box(
            modifier = Modifier.height(36.dp),
            contentAlignment = Alignment.Center
        ) {
            if (recording) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(ResQTheme.colors.sos)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "TX · TRANSMITTING LIVE",
                        color = ResQTheme.colors.sos,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(Modifier.width(12.dp))
                    // Bouncing audio bars
                    listOf(bar1, bar2, bar3, bar4, bar5).forEach { barHeight ->
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(barHeight.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(ResQTheme.colors.sos)
                        )
                    }
                }
            } else {
                Text(
                    text = if (receiverOn) "CH $channel STANDBY · PUSH TO TALK" else "CH $channel · HOLD TRIGGER TO TRANSMIT",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Center Rugged Tactical PTT Walkie-Talkie Button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(240.dp)
        ) {
            // Outward Sonic Waves when transmitting
            if (recording) {
                Box(
                    modifier = Modifier
                        .size(210.dp)
                        .scale(waveScale1)
                        .clip(CircleShape)
                        .background(ResQTheme.colors.sos.copy(alpha = waveAlpha * 0.4f))
                )
                Box(
                    modifier = Modifier
                        .size(210.dp)
                        .scale(waveScale2)
                        .clip(CircleShape)
                        .background(ResQTheme.colors.sos.copy(alpha = waveAlpha * 0.25f))
                )
            }

            // Outer Rubberized Industrial Bezel with hex bolt markers
            Surface(
                modifier = Modifier
                    .size(216.dp)
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .shadow(
                        elevation = if (recording) 24.dp else 14.dp,
                        shape = CircleShape,
                        ambientColor = if (recording) ResQTheme.colors.sos else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        spotColor = if (recording) ResQTheme.colors.sos else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    ),
                shape = CircleShape,
                color = if (isLight) Color(0xFFE2E8F0) else Color(0xFF161A20),
                border = BorderStroke(
                    width = 2.dp,
                    color = if (recording) ResQTheme.colors.sos
                    else MaterialTheme.colorScheme.primary.copy(alpha = if (isLight) idlePulse * 0.85f else idlePulse * 0.65f)
                )
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)

                    // Draw 8 hex bolt accents around the industrial perimeter
                    for (i in 0 until 8) {
                        val angle = Math.toRadians((i * 45).toDouble())
                        val boltDist = radius - 10.dp.toPx()
                        val bx = center.x + boltDist * cos(angle).toFloat()
                        val by = center.y + boltDist * sin(angle).toFloat()
                        drawCircle(
                            color = if (isLight) Color(0xFFCBD5E1) else Color(0xFF2C323B),
                            radius = 3.5.dp.toPx(),
                            center = Offset(bx, by)
                        )
                        drawCircle(
                            color = if (isLight) Color(0xFF94A3B8) else Color(0xFF0F1215),
                            radius = 2.dp.toPx(),
                            center = Offset(bx, by)
                        )
                    }
                }
            }

            // Inner Tactical PTT Trigger Button (Push-to-Talk Faceplate)
            Surface(
                modifier = Modifier
                    .size(174.dp)
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .clip(CircleShape)
                    .semantics {
                        contentDescription = if (liveAudio) "Push to talk live audio" else "Hold to record voice note"
                    }
                    .pointerInput(liveAudio) {
                        detectTapGestures(
                            onPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                recording = true
                                if (liveAudio) walkieTalkieViewModel.startLiveAudio() else mediaHelper.startRecording()
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    recording = false
                                    if (liveAudio) {
                                        walkieTalkieViewModel.stopLiveAudio()
                                    } else {
                                        mediaHelper.stopRecording()?.let { audio ->
                                            commsViewModel.sendPublicMessage("Voice message", null, audio)
                                        }
                                    }
                                }
                            }
                        )
                    },
                shape = CircleShape,
                color = if (recording) (if (isLight) Color(0xFFFFD9DE) else Color(0xFF900C3F))
                        else (if (isLight) Color(0xFFFFFFFF) else Color(0xFF1E242C)),
                shadowElevation = if (recording) 2.dp else (if (isLight) 4.dp else 8.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Knurled grip lines across the faceplate
                    Canvas(Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val lineCount = 5
                        val lineSpacing = 16.dp.toPx()
                        for (i in -lineCount..lineCount) {
                            val y = center.y + (i * lineSpacing)
                            drawLine(
                                color = if (isLight) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.04f),
                                start = Offset(center.x - 45.dp.toPx(), y),
                                end = Offset(center.x + 45.dp.toPx(), y),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }

                    // Tactical PTT Button Labeling & Mic Icon
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "PTT",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                            fontWeight = FontWeight.Black,
                            color = if (recording) (if (isLight) ResQTheme.colors.sos else Color.White) else MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                        )
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = if (recording) ResQTheme.colors.sos else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(54.dp),
                            shadowElevation = 6.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (recording) "ON AIR" else "TRANSMIT",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                            fontWeight = FontWeight.ExtraBold,
                            color = if (recording) (if (isLight) Color(0xFFB71C1C) else Color(0xFFFFD166)) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = if (recording) "RELEASE TO SEND AUDIO" else "PUSH AND HOLD TO TRANSMIT",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = if (recording) ResQTheme.colors.sos else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (liveAudio) "Audio is broadcast live to mesh nodes on Channel $channel."
            else "Voice note packet is compressed and sent to Channel $channel.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.Large))
    }
}

