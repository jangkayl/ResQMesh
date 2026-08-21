package com.example.testresqmesh.feature.sos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.theme.Spacing
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SOSBroadcastScreen(onCancel: () -> Unit, onSosTriggered: (String) -> Unit = {}) {
    val sosRed = MaterialTheme.colorScheme.error
    var selectedContext by remember { mutableStateOf("GENERAL") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(sosRed)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cancel", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            modifier = Modifier.size(100.dp),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.2f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Surface(modifier = Modifier.size(60.dp), shape = CircleShape, color = Color.White) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("!", fontSize = 32.sp, fontWeight = FontWeight.Black, color = sosRed)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "EMERGENCY\nSOS",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black,
            color = Color.White,
            textAlign = TextAlign.Center,
            lineHeight = 44.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Select context and slide to flood the local mesh network.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Context Grid
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SOSContextCard(Icons.Default.LocalHospital, "MEDICAL", selectedContext == "MEDICAL", { selectedContext = "MEDICAL" }, Modifier.weight(1f))
                SOSContextCard(Icons.Default.Whatshot, "FIRE", selectedContext == "FIRE", { selectedContext = "FIRE" }, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SOSContextCard(Icons.Default.BackHand, "TRAPPED", selectedContext == "TRAPPED", { selectedContext = "TRAPPED" }, Modifier.weight(1f))
                SOSContextCard(Icons.Default.Groups, "SEARCH", selectedContext == "SEARCH", { selectedContext = "SEARCH" }, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SOSContextCard(Icons.Default.Warning, "GENERAL", selectedContext == "GENERAL", { selectedContext = "GENERAL" }, Modifier.weight(1f))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Warning Box
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.Black.copy(alpha = 0.2f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(Spacing.Medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Wifi, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "WARNING: BYPASSES ROUTING LIMITS TO FLOOD LOCAL MESH. CURRENT SIGNAL HOPS: ∞ (UNLIMITED)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Swipe to Broadcast Slider
        SOSSlider(onSlideComplete = {
            onSosTriggered(selectedContext)
        })

        Spacer(modifier = Modifier.height(24.dp))

        // Footer Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FooterStatusItem(Icons.Default.Wifi, "WIFI DIRECT")
            Spacer(modifier = Modifier.width(16.dp))
            Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.4f)))
            Spacer(modifier = Modifier.width(16.dp))
            FooterStatusItem(Icons.Default.WifiTethering, "BLE MESH")
            Spacer(modifier = Modifier.width(16.dp))
            Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.4f)))
            Spacer(modifier = Modifier.width(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onPrimary)) // Assuming onPrimary looks good on red
                Spacer(modifier = Modifier.width(8.dp))
                Text("READY", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SOSContextCard(
    icon: ImageVector, 
    label: String, 
    isSelected: Boolean, 
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(100.dp).clickable { onClick() },
        color = if (isSelected) MaterialTheme.colorScheme.error else Color.White.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.error else Color.White.copy(alpha = 0.2f))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.White)
        }
    }
}

@Composable
fun SOSSlider(onSlideComplete: () -> Unit) {
    val sliderWidth = 300.dp
    val thumbSize = 64.dp
    
    val density = LocalDensity.current
    val sliderWidthPx = with(density) { sliderWidth.toPx() }
    val thumbSizePx = with(density) { thumbSize.toPx() }
    val maxDragPx = sliderWidthPx - thumbSizePx - with(density) { 8.dp.toPx() }
    
    var offsetX by remember { mutableStateOf(0f) }
    
    Surface(
        modifier = Modifier.width(sliderWidth).height(thumbSize + 8.dp),
        color = Color.White.copy(alpha = 0.2f),
        shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "SLIDE TO BROADCAST SOS",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = Color.White.copy(alpha = 0.6f),
                letterSpacing = 1.sp
            )
            
            Box(modifier = Modifier.fillMaxSize().padding(4.dp)) {
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
                    color = Color.White,
                    shadowElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun FooterStatusItem(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, fontSize = 8.sp)
    }
}
