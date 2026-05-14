package com.example.testresqmesh.ui.screens.setup

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.ui.theme.InboxBackground
import com.example.testresqmesh.ui.theme.InboxAccentBlue
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(3000)
        onTimeout()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(InboxBackground),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Mock Logo (The lightning bolt in a white square)
        Surface(
            modifier = Modifier.size(100.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("⚡", fontSize = 48.sp) // Representing the logo in the image
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "OFFLINE EMERGENCY MESH",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(180.dp))

        // Progress Bar
        Box(
            modifier = Modifier
                .width(280.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            var progress by remember { mutableStateOf(0f) }
            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = tween(durationMillis = 2500, easing = LinearEasing)
            )
            
            LaunchedEffect(Unit) {
                progress = 1f
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .background(InboxAccentBlue)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "INITIALIZING P2P PROTOCOL",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.4f)
        )
        Text(
            text = "Scanning local nodes...",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.3f)
        )

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "v1.0.4-BETA • SECURE OFFLINE NODES",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.3f),
            fontSize = 10.sp
        )
    }
}
