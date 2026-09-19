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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
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
import com.example.testresqmesh.core.ui.theme.ResQTheme
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
 * Minimalist & Secure SOS Broadcast Screen with full Dark and Light Mode support.
 *
 * Professional, calm, high-contrast emergency dispatch interface.
 * Replaces gamified elements with deliberate safety interactions:
 * - Clean 2x2 Minimalist Grid for fast emergency categorization
 * - Smooth "Slide to Broadcast" slider requiring intentional physical drag to send
 * - Minimalist hardware & mesh status indicators
 * - Seamless adaptation between Night Operations and Field Daylight themes
 */
@Composable
fun SOSBroadcastScreen(
    onCancel: () -> Unit,
    onSosTriggered: (String) -> Unit = {}
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

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

    // Ambient emergency glow and radiant light wave pulse
    val infiniteTransition = rememberInfiniteTransition(label = "ambientPulse")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = if (isLight) 0.28f else 0.18f,
        targetValue = if (isLight) 0.46f else 0.32f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlow"
    )

    val lightHaloScale by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lightHaloScale"
    )

    val haptics = LocalHapticFeedback.current
    val backgroundColor = if (isLight) Color(0xFFF8FAFC) else Color(0xFF0A0C10)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
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
                        color = if (isLight) Color.White else Color(0xFF141722),
                        border = BorderStroke(1.dp, if (isLight) animatedAccentColor.copy(alpha = 0.4f) else Color(0xFF22283A)),
                        shadowElevation = if (isLight) 3.dp else 0.dp
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
                                color = if (isLight) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.85f),
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    // Cancel Action
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isLight) Color.White else Color(0xFF191D28),
                        border = BorderStroke(1.dp, if (isLight) MaterialTheme.colorScheme.outlineVariant else Color(0xFF2E3547)),
                        shadowElevation = if (isLight) 3.dp else 0.dp,
                        onClick = onCancel
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel Emergency",
                                tint = if (isLight) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Cancel",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isLight) MaterialTheme.colorScheme.onSurface else Color.White
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
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = (-0.5).sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Select emergency type, then slide below to broadcast high-priority distress alerts to all mesh nodes within range.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            isLight = isLight,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                selectedIndex = 0
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        )
                        MinimalistEmergencyCard(
                            profile = emergencyTypes[1],
                            isSelected = selectedIndex == 1,
                            isLight = isLight,
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
                            isLight = isLight,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                selectedIndex = 2
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        )
                        MinimalistEmergencyCard(
                            profile = emergencyTypes[3],
                            isSelected = selectedIndex == 3,
                            isLight = isLight,
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
                    color = if (isLight) Color.White else Color(0xFF11141D),
                    border = BorderStroke(1.dp, if (isLight) MaterialTheme.colorScheme.outlineVariant else Color(0xFF1E2433)),
                    shadowElevation = if (isLight) 4.dp else 0.dp,
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
                                tint = ResQTheme.colors.success,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "GPS FIXED",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ResQTheme.colors.success
                            )
                        }

                        Box(
                            modifier = Modifier
                                .height(16.dp)
                                .width(1.dp)
                                .background(if (isLight) MaterialTheme.colorScheme.outlineVariant else Color(0xFF263045))
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
                                color = if (isLight) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.85f)
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
                    isLight = isLight,
                    onConfirm = {
                        onSosTriggered(currentProfile.title)
                    }
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Broadcasts will relay offline across all nearby civilian & rescuer devices.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
 * High contrast, legible, non-gamified, theme-adaptive.
 */
@Composable
private fun MinimalistEmergencyCard(
    profile: EmergencyProfile,
    isSelected: Boolean,
    isLight: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val cardColor = if (isSelected) {
        if (isLight) Color.White else Color(0xFF1C1318)
    } else {
        if (isLight) Color.White.copy(alpha = 0.90f) else Color(0xFF12151E)
    }

    val borderColor = if (isSelected) {
        profile.color
    } else {
        if (isLight) MaterialTheme.colorScheme.outlineVariant else Color(0xFF202636)
    }

    Surface(
        modifier = modifier
            .height(115.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = cardColor,
        border = BorderStroke(
            width = if (isSelected) 2.5.dp else 1.dp,
            color = borderColor
        ),
        shadowElevation = if (isSelected) 8.dp else (if (isLight) 3.dp else 0.dp)
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
                    color = if (isSelected) profile.color else (if (isLight) MaterialTheme.colorScheme.surfaceVariant else Color(0xFF1D2332))
                ) {
                    Text(
                        text = profile.code,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isSelected) Color.White else (if (isLight) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF8895A7)),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }

                Icon(
                    imageVector = profile.icon,
                    contentDescription = null,
                    tint = if (isSelected) profile.color else (if (isLight) MaterialTheme.colorScheme.outline else Color(0xFF707D91)),
                    modifier = Modifier.size(24.dp)
                )
            }

            Column {
                Text(
                    text = profile.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isSelected) (if (isLight) profile.color else Color.White) else MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = profile.subtitle,
                    fontSize = 10.5.sp,
                    color = if (isSelected) (if (isLight) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.75f)) else MaterialTheme.colorScheme.onSurfaceVariant,
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
 * preventing accidental touch activations. Adapts smoothly to Light/Dark modes.
 */
@Composable
private fun SlideToBroadcastSlider(
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

    val trackBgColor = if (isLight) Color.White else Color(0xFF141722)
    val trackBorderColor = if (isLight) (if (dragProgress > 0.1f) accentColor.copy(alpha = 0.6f) else Color(0xFFCBD5E1)) else Color(0xFF262D3D)
    val trackLabelColor = if (isLight) Color(0xFF1E293B) else Color.White

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

@Preview(name = "SOS — Minimalist & Secure Dark", widthDp = 390, heightDp = 844)
@Composable
private fun SOSBroadcastDarkPreview() {
    TestResQMeshTheme(darkTheme = true) {
        SOSBroadcastScreen(onCancel = {})
    }
}

@Preview(name = "SOS — Minimalist & Secure Light", widthDp = 390, heightDp = 844)
@Composable
private fun SOSBroadcastLightPreview() {
    TestResQMeshTheme(darkTheme = false) {
        SOSBroadcastScreen(onCancel = {})
    }
}
