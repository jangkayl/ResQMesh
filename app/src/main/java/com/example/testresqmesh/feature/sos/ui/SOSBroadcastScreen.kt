package com.example.testresqmesh.feature.sos.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class EmergencyProfile(
    val code: String,
    val title: String,
    val subtitle: String,
    val color: Color,
    val icon: ImageVector
)

/**
 * Minimalist & Secure SOS Broadcast Screen.
 *
 * Professional, calm, high-contrast emergency dispatch interface.
 * Replaces gamified elements with deliberate safety interactions:
 * - Clean 2x2 Minimalist Grid for fast emergency categorization
 * - Smooth "Slide to Broadcast" slider requiring intentional physical drag to send
 * - Minimalist hardware & mesh status indicators
 */
@Composable
fun SOSBroadcastScreen(
    onCancel: () -> Unit,
    onSosTriggered: (String) -> Unit = {}
) {
    val emergencyTypes = remember {
        listOf(
            EmergencyProfile(
                code = "MED",
                title = "MEDICAL",
                subtitle = "Severe injury, trauma or illness",
                color = Color(0xFFFF334B),
                icon = Icons.Default.LocalHospital
            ),
            EmergencyProfile(
                code = "FIRE",
                title = "FIRE",
                subtitle = "Thermal hazard, smoke or flames",
                color = Color(0xFFFF6D00),
                icon = Icons.Default.LocalFireDepartment
            ),
            EmergencyProfile(
                code = "TRAP",
                title = "TRAPPED",
                subtitle = "Structural void, collapse or lost",
                color = Color(0xFFFFB300),
                icon = Icons.Default.Construction
            ),
            EmergencyProfile(
                code = "GEN",
                title = "GENERAL",
                subtitle = "Critical danger & rescue request",
                color = Color(0xFF00E5FF),
                icon = Icons.Default.Warning
            )
        )
    }

    var selectedIndex by remember { mutableIntStateOf(0) }
    val currentProfile = emergencyTypes[selectedIndex]

    val animatedAccentColor by animateColorAsState(
        targetValue = currentProfile.color,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "themeAccent"
    )

    // Subtle, slow ambient glow pulse
    val infiniteTransition = rememberInfiniteTransition(label = "ambientPulse")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlow"
    )

    val haptics = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C10))
    ) {
        // Minimalist Ambient Radial Vignette
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedAccentColor.copy(alpha = pulseGlow),
                        Color(0xFF0A0C10)
                    ),
                    center = Offset(w / 2f, h * 0.45f),
                    radius = w * 1.1f
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.Large, vertical = Spacing.Medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Upper Content Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Top Bar (Status Indicator + Clean Dismiss Button)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // System Mode Pill
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF141722),
                        border = BorderStroke(1.dp, Color(0xFF22283A))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(animatedAccentColor)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "EMERGENCY DISPATCH",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    // Cancel Action
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF191D28),
                        border = BorderStroke(1.dp, Color(0xFF2E3547)),
                        onClick = onCancel
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel Emergency",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Cancel",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // 2. Title & Instructions
                Text(
                    text = "Emergency SOS",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = (-0.5).sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Select emergency type, then slide below to broadcast high-priority distress alerts to all mesh nodes within range.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF8E9BAE),
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(Modifier.height(28.dp))

                // 3. Minimalist 2x2 Category Grid
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MinimalistEmergencyCard(
                            profile = emergencyTypes[0],
                            isSelected = selectedIndex == 0,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                selectedIndex = 0
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        )
                        MinimalistEmergencyCard(
                            profile = emergencyTypes[1],
                            isSelected = selectedIndex == 1,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                selectedIndex = 1
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MinimalistEmergencyCard(
                            profile = emergencyTypes[2],
                            isSelected = selectedIndex == 2,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                selectedIndex = 2
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        )
                        MinimalistEmergencyCard(
                            profile = emergencyTypes[3],
                            isSelected = selectedIndex == 3,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                selectedIndex = 3
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // 4. Hardware & Telemetry Diagnostic Strip
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF11141D),
                    border = BorderStroke(1.dp, Color(0xFF1E2433)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GpsFixed,
                                contentDescription = null,
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "GPS FIXED",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E676)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .height(16.dp)
                                .width(1.dp)
                                .background(Color(0xFF263045))
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = null,
                                tint = animatedAccentColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "DECENTRALIZED RELAY",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // Lower Action Section ("Slide to Broadcast")
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SlideToBroadcastSlider(
                    accentColor = animatedAccentColor,
                    emergencyTitle = currentProfile.title,
                    onConfirm = {
                        onSosTriggered(currentProfile.title)
                    }
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Broadcasts will relay offline across all nearby civilian & rescuer devices.",
                    fontSize = 11.sp,
                    color = Color(0xFF6B7686),
                    textAlign = TextAlign.Center,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

/**
 * Minimalist emergency category selection card.
 * High contrast, legible, non-gamified.
 */
@Composable
private fun MinimalistEmergencyCard(
    profile: EmergencyProfile,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(115.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) Color(0xFF1C1318) else Color(0xFF12151E),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) profile.color else Color(0xFF202636)
        ),
        shadowElevation = if (isSelected) 8.dp else 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSelected) profile.color else Color(0xFF1D2332)
                ) {
                    Text(
                        text = profile.code,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isSelected) Color.White else Color(0xFF8895A7),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }

                Icon(
                    imageVector = profile.icon,
                    contentDescription = null,
                    tint = if (isSelected) profile.color else Color(0xFF707D91),
                    modifier = Modifier.size(24.dp)
                )
            }

            Column {
                Text(
                    text = profile.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isSelected) Color.White else Color(0xFFD6DBE5),
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = profile.subtitle,
                    fontSize = 10.5.sp,
                    color = if (isSelected) Color.White.copy(alpha = 0.75f) else Color(0xFF677488),
                    lineHeight = 13.sp,
                    maxLines = 2
                )
            }
        }
    }
}

/**
 * Slide to Broadcast Track.
 * Requires an intentional full horizontal swipe to trigger the SOS,
 * preventing accidental touch activations.
 */
@Composable
private fun SlideToBroadcastSlider(
    accentColor: Color,
    emergencyTitle: String,
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(CircleShape)
            .background(Color(0xFF141722))
            .border(BorderStroke(1.5.dp, Color(0xFF262D3D)), CircleShape)
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
                    color = Color.White.copy(alpha = chevronAlpha)
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
                tint = if (dragProgress > 0.8f) Color.White else Color(0xFFD32F2F),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Preview(name = "SOS — Minimalist & Secure", widthDp = 390, heightDp = 844)
@Composable
private fun SOSBroadcastPreview() {
    TestResQMeshTheme {
        SOSBroadcastScreen(onCancel = {})
    }
}
