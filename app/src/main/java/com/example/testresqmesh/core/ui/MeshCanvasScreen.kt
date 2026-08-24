package com.example.testresqmesh.core.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.components.GlassSurface
import com.example.testresqmesh.core.utils.MediaHelper
import com.example.testresqmesh.feature.comms.ui.ActiveChatScreen
import com.example.testresqmesh.feature.comms.ui.ChatContainerScreen
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel
import com.example.testresqmesh.feature.radar.viewmodel.RadarViewModel
import com.example.testresqmesh.feature.setup.viewmodel.SetupViewModel
import com.example.testresqmesh.feature.sos.ui.ActiveSOSMonitoringScreen
import com.example.testresqmesh.feature.sos.ui.FullScreenSosAlarm
import com.example.testresqmesh.feature.sos.ui.SOSBroadcastScreen
import com.example.testresqmesh.feature.sos.ui.SosMapScreen
import kotlin.math.cos
import kotlin.math.sin
import java.util.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshCanvasScreen(
    setupViewModel: SetupViewModel,
    radarViewModel: RadarViewModel,
    commsViewModel: CommunicationViewModel,
    mediaHelper: MediaHelper,
    onActiveChatSet: (String) -> Unit
) {
    val radarState by radarViewModel.uiState.collectAsState()
    val commsState by commsViewModel.uiState.collectAsState()

    val haptic = LocalHapticFeedback.current
    var isSosExpanding by remember { mutableStateOf(false) }
    var sosProgress by remember { mutableStateOf(0f) }
    var showSosBroadcast by remember { mutableStateOf(false) }

    val bottomSheetState = rememberBottomSheetScaffoldState()
    
    val connectedCount = radarState.connectedDevices.size
    val totalNodes = connectedCount + radarState.scannedDevices.size
    
    // Map Canvas Background Animation
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val radarSweep by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SweepAnimation"
    )

    if (showSosBroadcast) {
        val context = LocalContext.current
        SOSBroadcastScreen(
            onCancel = { showSosBroadcast = false },
            onSosTriggered = { type ->
                commsViewModel.sendEmergencySOS(context, type)
                showSosBroadcast = false
            }
        )
        BackHandler { showSosBroadcast = false }
        return
    }

    BottomSheetScaffold(
        scaffoldState = bottomSheetState,
        sheetContent = {
            // LAYER 3: Comms Sheet
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .background(Color.Black.copy(alpha = 0.9f))
            ) {
                ChatContainerScreen(
                    viewModel = commsViewModel,
                    mediaHelper = mediaHelper,
                    onChatSelected = onActiveChatSet
                )
            }
        },
        sheetPeekHeight = 80.dp,
        sheetContainerColor = Color.Transparent,
        containerColor = Color.Black
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // LAYER 0: The Living Radar Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = size.center
                val primaryColor = Color(0xFF00E5FF) // Cyber Neon Blue

                // Grid lines for Tactical feel
                val gridSize = 50.dp.toPx()
                for (i in 0..(size.width / gridSize).toInt()) {
                    drawLine(Color.White.copy(alpha = 0.05f), start = Offset(i * gridSize, 0f), end = Offset(i * gridSize, size.height))
                }
                for (i in 0..(size.height / gridSize).toInt()) {
                    drawLine(Color.White.copy(alpha = 0.05f), start = Offset(0f, i * gridSize), end = Offset(size.width, i * gridSize))
                }

                // Range Circles
                val maxRadius = size.width * 0.9f
                drawCircle(color = primaryColor.copy(alpha = 0.1f), radius = maxRadius * 0.75f, style = Stroke(1.dp.toPx()))
                drawCircle(color = primaryColor.copy(alpha = 0.1f), radius = maxRadius * 0.5f, style = Stroke(1.dp.toPx()))
                drawCircle(color = primaryColor.copy(alpha = 0.1f), radius = maxRadius * 0.25f, style = Stroke(1.dp.toPx()))

                // Radar Sweep Arc
                drawArc(
                    color = primaryColor.copy(alpha = 0.3f),
                    startAngle = radarSweep,
                    sweepAngle = 90f,
                    useCenter = true,
                    size = androidx.compose.ui.geometry.Size(maxRadius * 2, maxRadius * 2),
                    topLeft = Offset(center.x - maxRadius, center.y - maxRadius)
                )

                // Me (Center)
                drawCircle(color = Color.White, radius = 6.dp.toPx(), center = center)
                drawCircle(color = primaryColor, radius = 12.dp.toPx(), center = center, style = Stroke(2.dp.toPx()))

                // Nodes
                val allNodes = radarState.connectedDevices.map { it.name } + radarState.scannedDevices.map { it.name }
                allNodes.forEach { name ->
                    val random = Random(name.hashCode().toLong())
                    val angle = random.nextFloat() * 360f
                    val distance = (0.2f + random.nextFloat() * 0.7f) * maxRadius
                    val x = center.x + distance * cos(Math.toRadians(angle.toDouble())).toFloat()
                    val y = center.y + distance * sin(Math.toRadians(angle.toDouble())).toFloat()
                    
                    drawCircle(
                        color = primaryColor, 
                        radius = 4.dp.toPx(), 
                        center = Offset(x, y)
                    )
                    // Glow
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.4f), 
                        radius = 8.dp.toPx(), 
                        center = Offset(x, y)
                    )
                }
            }

            // LAYER 1: Dynamic Island
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                GlassSurface(
                    shape = RoundedCornerShape(32.dp),
                    intensity = 0.4f,
                    modifier = Modifier.wrapContentSize()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (connectedCount > 0) Color(0xFF00FF88) else Color.Gray)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (connectedCount > 0) "MESH ACTIVE: $totalNodes NODES" else "SCANNING HORIZON...",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = "Signal",
                            tint = if (connectedCount > 0) Color(0xFF00FF88) else Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // LAYER 2: SOS Core
            val sosButtonSize by animateDpAsState(if (isSosExpanding) 120.dp else 80.dp)
            val sosAlpha by animateFloatAsState(if (isSosExpanding) 0.8f else 0.4f)
            
            LaunchedEffect(isSosExpanding) {
                if (isSosExpanding) {
                    while (sosProgress < 1f && isSosExpanding) {
                        kotlinx.coroutines.delay(50)
                        sosProgress += 0.05f
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    if (sosProgress >= 1f) {
                        showSosBroadcast = true
                        isSosExpanding = false
                        sosProgress = 0f
                    }
                } else {
                    sosProgress = 0f
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 32.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isSosExpanding = true
                                    tryAwaitRelease()
                                    isSosExpanding = false
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Progress Ring
                    if (isSosExpanding) {
                        CircularProgressIndicator(
                            progress = { sosProgress },
                            modifier = Modifier.size(130.dp),
                            color = Color.Red,
                            strokeWidth = 4.dp,
                            trackColor = Color.Transparent
                        )
                    }

                    // Button
                    GlassSurface(
                        shape = CircleShape,
                        intensity = sosAlpha,
                        modifier = Modifier.size(sosButtonSize)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Red.copy(alpha = if (isSosExpanding) 0.8f else 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "SOS",
                                tint = Color.White,
                                modifier = Modifier.size(if (isSosExpanding) 48.dp else 32.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
