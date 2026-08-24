package com.example.testresqmesh.feature.setup.ui

import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.components.GlassSurface
import com.example.testresqmesh.feature.setup.viewmodel.SetupViewModel
import kotlinx.coroutines.delay

@Composable
fun IdentitySetupScreen(viewModel: SetupViewModel, onIdentityGenerated: () -> Unit) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
    IdentitySetupContent(
        connectionStatus = uiState.connectionStatus,
        onIdentityGenerated = {
            viewModel.checkHardwareAndGoOnline(context, Build.MODEL, "NODE", "PUBLIC")
            onIdentityGenerated()
        }
    )
}

@Composable
fun IdentitySetupContent(connectionStatus: String, onIdentityGenerated: () -> Unit) {
    var isHolding by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    
    // Hash simulation
    var simulatedHash by remember { mutableStateOf("0xWAITING_FOR_FINGERPRINT") }

    LaunchedEffect(isHolding) {
        if (isHolding) {
            while (holdProgress < 1f && isHolding) {
                delay(50)
                holdProgress += 0.02f
                val randomHex = (1..16).map { "0123456789ABCDEF".random() }.joinToString("")
                simulatedHash = "0x$randomHex"
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            if (holdProgress >= 1f) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onIdentityGenerated()
            }
        } else {
            holdProgress = 0f
            simulatedHash = "0xWAITING_FOR_FINGERPRINT"
        }
    }

    // Blob animation scale
    val blobScale by animateFloatAsState(
        targetValue = if (isHolding) 1.5f + (holdProgress * 0.5f) else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "INITIATE MESH",
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 4.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Generate off-grid cryptographic key pair.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f),
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(1f))

        // Interactive Blob
        Box(
            modifier = Modifier
                .size(200.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isHolding = true
                            tryAwaitRelease()
                            isHolding = false
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Pulse rings
            if (isHolding) {
                CircularProgressIndicator(
                    progress = { holdProgress },
                    modifier = Modifier.size(180.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                    trackColor = Color.Transparent
                )
            }

            // Core Blob
            GlassSurface(
                shape = CircleShape,
                intensity = if (isHolding) 0.6f else 0.2f,
                modifier = Modifier
                    .size(120.dp)
                    .scale(blobScale)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isHolding) 0.5f else 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isHolding) "GENERATING..." else "HOLD",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Hash Visualizer
        Text(
            text = simulatedHash,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            color = if (isHolding) MaterialTheme.colorScheme.tertiary else Color.White.copy(alpha = 0.4f),
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(1f))

        GlassSurface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "> SECURE TERMINAL",
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (connectionStatus.contains("ERROR")) "ERR: $connectionStatus" else "STATUS: $connectionStatus",
                    color = if (connectionStatus.contains("ERROR")) MaterialTheme.colorScheme.error else Color.White.copy(alpha = 0.8f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        }
    }
}
