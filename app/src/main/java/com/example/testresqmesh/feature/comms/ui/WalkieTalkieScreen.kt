package com.example.testresqmesh.feature.comms.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.testresqmesh.core.model.ConversationPolicy
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
import androidx.compose.ui.graphics.luminance

@Composable
fun WalkieTalkieScreen(
    commsViewModel: CommunicationViewModel,
    walkieTalkieViewModel: WalkieTalkieViewModel,
    mediaHelper: MediaHelper,
    onHistory: () -> Unit = {}
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val receiverOn by walkieTalkieViewModel.isWalkieTalkieMode.collectAsState()
    val channel by walkieTalkieViewModel.currentChannelId.collectAsState()
    val speaker by walkieTalkieViewModel.currentSpeaker.collectAsState()
    val latestChannel by androidx.compose.runtime.rememberUpdatedState(channel)
    var recording by remember { mutableStateOf(false) }
    var recordingDestination by remember { mutableStateOf(channel) }
    var channelsOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    val allPublicMessages by commsViewModel.allPublicMessages.collectAsState()
    val conversationStates by commsViewModel.conversationStates.collectAsState()
    val totalRadioUnread = remember(allPublicMessages, conversationStates) {
        (1..5).sumOf { chNum ->
            val ch = chNum.toString()
            val messages = ConversationPolicy.messages(allPublicMessages, "RADIO", ch)
            val key = ConversationPolicy.key("RADIO", ch)
            val read = conversationStates.firstOrNull { it.conversationId == key }?.lastReadAt ?: 0L
            messages.count { !it.isMine && it.timestamp > read }
        }
    }

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

    BoxWithConstraints(Modifier.fillMaxSize()) {
    val compact = maxHeight < 640.dp || LocalDensity.current.fontScale > 1.3f
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier)
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
                    text = "Mesh Walkie-Talkie",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Channel $channel · Recorded voice notes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
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
                        Text(
                            text = "CH $channel",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
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

        TacticalChannelHistoryButton(
            unreadCount = totalRadioUnread,
            onClick = onHistory,
            isLight = isLight
        )

        Spacer(Modifier.height(Spacing.Medium))

    // Voice Receiver / Radio Monitor Panel
        ResQGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            contentPadding = PaddingValues(16.dp),
            shadowElevation = 4.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
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
                    Text(
                        text = "Channel Monitor",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (receiverOn) "New CH $channel notes autoplay" else "Muted · Tap switch to listen",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = receiverOn,
                    onCheckedChange = { walkieTalkieViewModel.toggleWalkieTalkieMode() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ResQTheme.colors.success,
                        checkedTrackColor = ResQTheme.colors.success.copy(alpha = 0.35f)
                    )
                )
            }
        }

        Spacer(Modifier.height(Spacing.Medium))

        // Active Speaker / Channel Status Card (Who's Talking)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (speaker != null && receiverOn) ResQTheme.colors.success.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                if (speaker != null && receiverOn) ResQTheme.colors.success.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            ),
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (speaker != null && receiverOn) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(ResQTheme.colors.success)
                    )
                    Icon(
                        imageVector = Icons.Outlined.GraphicEq,
                        contentDescription = null,
                        tint = ResQTheme.colors.success,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Playing: $speaker",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (receiverOn) ResQTheme.colors.success.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )
                    Text(
                        text = if (receiverOn) "Monitoring CH $channel · New notes autoplay" else "Monitor muted · Channel $channel",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Active Status & Equalizer Display
        Box(
            modifier = Modifier.height(40.dp),
            contentAlignment = Alignment.Center
        ) {
            if (recording) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(ResQTheme.colors.sos)
                    )
                    Text(
                        text = "TRANSMITTING LIVE",
                        color = ResQTheme.colors.sos,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(Modifier.width(8.dp))
                    // Bouncing audio bars
                    listOf(bar1, bar2, bar3, bar4, bar5).forEach { barHeight ->
                        Box(
                            modifier = Modifier
                                .width(3.5.dp)
                                .height(barHeight.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(ResQTheme.colors.sos)
                        )
                    }
                }
            } else {
                Text(
                    text = if (receiverOn) "READY · HOLD BUTTON TO TALK" else "CHANNEL $channel STANDBY",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Center Modern Push-To-Talk Button with Gradient & Concentric Glow Ripples
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(240.dp)
        ) {
            // Outward Glowing Concentric Ripples when transmitting
            if (recording) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .scale(waveScale1)
                        .clip(CircleShape)
                        .background(ResQTheme.colors.sos.copy(alpha = waveAlpha * 0.35f))
                )
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .scale(waveScale2)
                        .clip(CircleShape)
                        .background(ResQTheme.colors.sos.copy(alpha = waveAlpha * 0.2f))
                )
            } else {
                // Subtle Ambient Pulse Ring in idle
                Box(
                    modifier = Modifier
                        .size(210.dp)
                        .clip(CircleShape)
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = idlePulse * 0.4f),
                            shape = CircleShape
                        )
                )
            }

            // Modern Floating Audio Hub Circle Button
            val primaryColor = MaterialTheme.colorScheme.primary
            val activeColor = ResQTheme.colors.sos
            val buttonGradient = remember(recording, primaryColor, activeColor) {
                if (recording) {
                    Brush.radialGradient(
                        colors = listOf(activeColor, Color(0xFFD32F2F))
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(primaryColor, Color(0xFFFF7A29))
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .size(180.dp)
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .shadow(
                        elevation = if (recording) 28.dp else 16.dp,
                        shape = CircleShape,
                        ambientColor = if (recording) ResQTheme.colors.sos else primaryColor.copy(alpha = 0.5f),
                        spotColor = if (recording) ResQTheme.colors.sos else primaryColor.copy(alpha = 0.7f)
                    )
                    .clip(CircleShape)
                    .semantics {
                        contentDescription = "Hold to record voice note"
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                val recordingChannel = latestChannel
                                recordingDestination = recordingChannel
                                recording = true
                                mediaHelper.startRecording()
                                var releasedNormally = false
                                try {
                                    releasedNormally = tryAwaitRelease()
                                } finally {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    recording = false
                                    val audio = mediaHelper.stopRecording()
                                    // Scrolling or leaving the screen cancels the press; it must not send a partial note.
                                    if (releasedNormally && audio != null) {
                                        commsViewModel.sendConversation("RADIO", channel = recordingChannel, text = "Voice message", audio = audio)
                                    }
                                }
                            }
                        )
                    },
                shape = CircleShape,
                color = Color.Transparent
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(buttonGradient)
                        .border(
                            width = 3.dp,
                            color = Color.White.copy(alpha = if (recording) 0.5f else 0.25f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(46.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (recording) "RECORDING" else "HOLD TO TALK",
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = if (recording) "Release button to broadcast voice" else "Push and hold to transmit voice",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (recording) ResQTheme.colors.sos else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Voice note will be sent to Channel ${if (recording) recordingDestination else channel}.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(96.dp))
    }
}

}

@Composable
private fun TacticalChannelHistoryButton(
    unreadCount: Int,
    onClick: () -> Unit,
    isLight: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Open channel conversations and history" },
        shape = RoundedCornerShape(18.dp),
        color = if (isLight) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, if (isLight) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        shadowElevation = if (isLight) 1.dp else 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = "Channel History & Notes",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Recorded audio & chatter on CH 1–5",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (unreadCount > 0) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ResQTheme.colors.sos
                ) {
                    Text(
                        text = "$unreadCount new",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
            }

            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
