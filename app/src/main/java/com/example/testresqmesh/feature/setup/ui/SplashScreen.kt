package com.example.testresqmesh.feature.setup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    
    val terminalLines = listOf(
        "INIT RESQMESH KERNEL v2.0.4...",
        "MOUNTING ENCRYPTED SECURE STORAGE... [OK]",
        "CHECKING HARDWARE INTERFACES...",
        "   > BLUETOOTH LE (PAwR) ... [OK]",
        "   > WI-FI DIRECT PHY ... [OK]",
        "LOADING MESH ROUTING PROTOCOL...",
        "   > ECDH KEY EXCHANGE READY",
        "SYSTEM ONLINE. OFF-GRID MODE ENGAGED."
    )

    LaunchedEffect(Unit) {
        for (i in terminalLines.indices) {
            delay((200..600).random().toLong()) // Simulate loading times
            step = i
        }
        delay(1000)
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "ResQMesh Terminal // SECURE BOOT",
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            for (i in 0..step) {
                Text(
                    text = "> ${terminalLines[i]}",
                    color = if (i == terminalLines.size - 1) MaterialTheme.colorScheme.tertiary else Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // Blinking cursor
            var cursorVisible by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(500)
                    cursorVisible = !cursorVisible
                }
            }

            if (step < terminalLines.size - 1) {
                Text(
                    text = if (cursorVisible) "> _" else ">",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}
