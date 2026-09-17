package com.example.testresqmesh.feature.sos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.testresqmesh.feature.sos.utils.MapDownloadManager

@Composable
fun OfflineMapPromptModal(downloadManager: MapDownloadManager, onDismiss: () -> Unit) {
    val state by downloadManager.downloadState.collectAsState()
    val progress by downloadManager.progress.collectAsState()
    val total by downloadManager.totalTiles.collectAsState()
    Dialog(onDismissRequest = { if (state != MapDownloadManager.DownloadState.DOWNLOADING) onDismiss() }) {
        Surface(shape = RoundedCornerShape(28.dp), color = Color.White, tonalElevation = 4.dp) {
            Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                when (state) {
                    MapDownloadManager.DownloadState.IDLE, MapDownloadManager.DownloadState.ERROR -> {
                        Icon(Icons.Default.CloudDownload, null, tint = Color(0xFF7442C8), modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(14.dp)); Text("Offline map", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(8.dp)); Text("Download the available Cebu map area for use when tiles are not cached.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state == MapDownloadManager.DownloadState.ERROR) Text("Download failed. Try again when online.", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp))
                        Spacer(Modifier.height(20.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onDismiss) { Text("Later") }; Spacer(Modifier.width(8.dp)); Button(onClick = downloadManager::startCebuDownload) { Text("Download") } }
                    }
                    MapDownloadManager.DownloadState.DOWNLOADING -> {
                        Text("Downloading map", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black); Spacer(Modifier.height(16.dp)); LinearProgressIndicator(progress = { if (total > 0) progress.toFloat() / total else 0f }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(10.dp)); Text("$progress of $total tiles", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    MapDownloadManager.DownloadState.COMPLETE -> {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF18A66A), modifier = Modifier.size(48.dp)); Spacer(Modifier.height(14.dp)); Text("Map downloaded", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black); Spacer(Modifier.height(8.dp)); Text("Cached map tiles are ready for this area.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(20.dp)); Button(onClick = onDismiss) { Text("Done") }
                    }
                }
            }
        }
    }
}
