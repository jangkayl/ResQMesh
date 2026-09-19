package com.example.testresqmesh.feature.sos.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.utils.MediaHelper
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.components.layout.ResQSosBackground

@Composable
fun FullScreenSosAlarm(alertMessage: ChatMessage, onDismiss: () -> Unit, onViewMap: () -> Unit = {}) {
    val context = LocalContext.current
    val media = remember { MediaHelper(context) }
    DisposableEffect(Unit) {
        media.playEmergencySiren()
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
        if (vibrator.hasVibrator()) {
            val pattern = longArrayOf(0, 500, 250, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0)) else @Suppress("DEPRECATION") vibrator.vibrate(pattern, 0)
        }
        onDispose { media.stopEmergencySiren(); vibrator.cancel() }
    }
    val red = ResQTheme.colors.sos
    val hasLocation = alertMessage.locationLat != null && alertMessage.locationLng != null
    ResQSosBackground(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(Spacing.Large), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(Modifier.size(96.dp), CircleShape, color = red.copy(alpha = .12f)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Warning, "Emergency alert", tint = red, modifier = Modifier.size(48.dp)) } }
        Spacer(Modifier.height(20.dp))
        Text("Emergency alert", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
        Text("From ${alertMessage.senderName}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Surface(Modifier.fillMaxWidth(), RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceVariant, shadowElevation = 4.dp) {
            Column(Modifier.padding(22.dp)) {
                Text(alertMessage.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.LocationOn, null, tint = if (hasLocation) ResQTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(8.dp)); Text(if (hasLocation) "Location shared" else "No location shared", fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(28.dp))
        if (hasLocation) { Button(onClick = onViewMap, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(22.dp)) { Text("View map", fontWeight = FontWeight.Bold) }; Spacer(Modifier.height(10.dp)) }
        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(22.dp)) { Text("Dismiss", fontWeight = FontWeight.Bold) }
        }
    }
}
