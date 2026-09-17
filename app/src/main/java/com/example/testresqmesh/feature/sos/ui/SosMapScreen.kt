package com.example.testresqmesh.feature.sos.ui

import android.preference.PreferenceManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.feature.sos.utils.MapDownloadManager
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

@Composable
fun SosMapScreen(alertMessage: ChatMessage, onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { MapDownloadManager(context) }
    var showDownload by remember { mutableStateOf(false) }
    val lat = alertMessage.locationLat
    val lng = alertMessage.locationLng
    Column(Modifier.fillMaxSize().background(Color(0xFFFBFAFF))) {
        Row(Modifier.fillMaxWidth().padding(Spacing.Medium), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) { Text("SOS map", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black); Text(alertMessage.senderName, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (!manager.isMapDownloaded()) IconButton(onClick = { showDownload = true }) { Icon(Icons.Default.CloudDownload, "Download offline map") }
        }
        if (lat == null || lng == null) {
            Box(Modifier.fillMaxSize().padding(Spacing.Large), contentAlignment = Alignment.Center) {
                Surface(shape = RoundedCornerShape(28.dp), color = Color.White, shadowElevation = 4.dp) { Text("This alert did not include a location.", modifier = Modifier.padding(28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        } else {
            Surface(Modifier.fillMaxSize().padding(horizontal = Spacing.Medium, vertical = Spacing.Small), RoundedCornerShape(28.dp), color = Color.White, shadowElevation = 4.dp) {
                AndroidView(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)), factory = { ctx ->
                    val base = File(ctx.filesDir, "osmdroid").apply { mkdirs() }
                    Configuration.getInstance().apply { load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx)); userAgentValue = ctx.packageName; osmdroidBasePath = base; osmdroidTileCache = File(base, "tiles").apply { mkdirs() } }
                    MapView(ctx).apply { setMultiTouchControls(true); val point = GeoPoint(lat, lng); controller.setZoom(16.0); controller.setCenter(point); overlays.add(Marker(this).apply { position = point; title = "SOS: ${alertMessage.senderName}" }) }
                })
            }
        }
    }
    if (showDownload) OfflineMapPromptModal(manager) { showDownload = false }
}
