package com.example.testresqmesh.feature.sos.ui

import com.example.testresqmesh.feature.sos.ui.broadcast.BroadcastIllumination
import com.example.testresqmesh.feature.sos.ui.EmergencyProfile
import com.example.testresqmesh.feature.sos.ui.broadcast.MinimalistEmergencyCard
import com.example.testresqmesh.feature.sos.ui.broadcast.SlideToBroadcastSlider
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme

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
                color = Color(0xFF8B5CF6),
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
        BroadcastIllumination(currentProfile, isLight, pulseGlow, lightHaloScale, animatedAccentColor)
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

/**
 * Slide to Broadcast Track.
 * Requires an intentional full horizontal swipe to trigger the SOS,
 * preventing accidental touch activations. Adapts smoothly to Light/Dark modes.
 */

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
