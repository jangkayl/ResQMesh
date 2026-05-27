package com.example.testresqmesh.feature.sos.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.theme.*
import com.example.testresqmesh.core.ui.components.layout.ResQCard
import kotlin.math.roundToInt

@Composable
fun SOSBroadcastScreen(onCancel: () -> Unit, onSosTriggered: (String) -> Unit = {}) {
    var selectedContext by remember { mutableStateOf("GENERAL") }

    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimaryRed)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Cancel Button
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            IconButton(onClick = onCancel, modifier = Modifier.size(48.dp)) {
                Surface(modifier = Modifier.fillMaxSize(), shape = CircleShape, color = WarmWhite.copy(alpha = 0.2f)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = WarmWhite)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Large Animated Indicator
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(140.dp).scale(pulseScale),
                shape = CircleShape,
                color = WarmWhite.copy(alpha = 0.15f)
            ) {}
            Surface(
                modifier = Modifier.size(100.dp),
                shape = CircleShape,
                color = WarmWhite,
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PriorityHigh, contentDescription = null, tint = PrimaryRed, modifier = Modifier.size(48.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Emergency SOS",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = WarmWhite,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Slide to alert all nearby devices instantly.",
            style = MaterialTheme.typography.bodyLarge,
            color = WarmWhite.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Context Grid
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SOSOptionCard(Icons.Default.LocalHospital, "Medical", selectedContext == "MEDICAL", { selectedContext = "MEDICAL" }, Modifier.weight(1f))
                SOSOptionCard(Icons.Default.Whatshot, "Fire", selectedContext == "FIRE", { selectedContext = "FIRE" }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SOSOptionCard(Icons.Default.Security, "Police", selectedContext == "GENERAL", { selectedContext = "GENERAL" }, Modifier.weight(1f))
                SOSOptionCard(Icons.Default.Groups, "Other", selectedContext == "OTHER", { selectedContext = "OTHER" }, Modifier.weight(1f))
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(64.dp))

        // Large Modern Slide to SOS
        ModernSOSSlider(onSlideComplete = { onSosTriggered(selectedContext) })

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun SOSOptionCard(icon: ImageVector, label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ResQCard(
        modifier = modifier.height(100.dp).clickable { onClick() },
        backgroundColor = if (isSelected) WarmWhite else WarmWhite.copy(alpha = 0.1f),
        elevation = if (isSelected) 4.dp else 0.dp,
        cornerRadius = 24.dp
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = if (isSelected) PrimaryRed else WarmWhite, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (isSelected) PrimaryRed else WarmWhite)
        }
    }
}

@Composable
fun ModernSOSSlider(onSlideComplete: () -> Unit) {
    val sliderWidth = 320.dp
    val thumbSize = 64.dp
    val density = LocalDensity.current
    val sliderWidthPx = with(density) { sliderWidth.toPx() }
    val thumbSizePx = with(density) { thumbSize.toPx() }
    val maxDragPx = sliderWidthPx - thumbSizePx - with(density) { 8.dp.toPx() }
    
    var offsetX by remember { mutableStateOf(0f) }

    Surface(
        modifier = Modifier.width(sliderWidth).height(thumbSize + 12.dp),
        color = WarmWhite.copy(alpha = 0.2f),
        shape = CircleShape
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "Slide to Start Broadcast",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = WarmWhite.copy(alpha = 0.6f)
            )
            
            Box(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                Surface(
                    modifier = Modifier
                        .size(thumbSize)
                        .offset(x = with(density) { offsetX.toDp() })
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (offsetX >= maxDragPx * 0.9f) {
                                        offsetX = maxDragPx
                                        onSlideComplete()
                                    } else {
                                        offsetX = 0f
                                    }
                                }
                            ) { change, dragAmount ->
                                change.consume()
                                offsetX = (offsetX + dragAmount).coerceIn(0f, maxDragPx)
                            }
                        },
                    shape = CircleShape,
                    color = WarmWhite,
                    shadowElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = PrimaryRed, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}
