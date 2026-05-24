package com.example.testresqmesh.feature.sos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.testresqmesh.feature.sos.utils.MapDownloadManager
import com.example.testresqmesh.feature.sos.utils.MapDownloadManager.DownloadState

@Composable
fun OfflineMapPromptModal(
    downloadManager: MapDownloadManager,
    onDismiss: () -> Unit
) {
    val downloadState by downloadManager.downloadState.collectAsState()
    val progress by downloadManager.progress.collectAsState()
    val totalTiles by downloadManager.totalTiles.collectAsState()

    Dialog(onDismissRequest = { 
        if (downloadState != DownloadState.DOWNLOADING) onDismiss() 
    }) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E1E1E),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                
                when (downloadState) {
                    DownloadState.IDLE, DownloadState.ERROR -> {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Download",
                            tint = Color(0xFF2196F3),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Download Offline Map?",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "To ensure the SOS Tracker works during power outages or network blackouts, please download the local map of Cebu City.\n\nSize: ~25 MB",
                            color = Color.LightGray,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        if (downloadState == DownloadState.ERROR) {
                            Text(
                                text = "Download failed. Check internet and try again.",
                                color = Color.Red,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            TextButton(onClick = onDismiss) {
                                Text("Later", color = Color.Gray)
                            }
                            Button(
                                onClick = { downloadManager.startCebuDownload() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                            ) {
                                Text("Download", color = Color.White)
                            }
                        }
                    }
                    
                    DownloadState.DOWNLOADING -> {
                        CircularProgressIndicator(
                            color = Color(0xFF2196F3),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Downloading Cebu Map...",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val percentage = if (totalTiles > 0) (progress.toFloat() / totalTiles) * 100 else 0f
                        
                        LinearProgressIndicator(
                            progress = { if (totalTiles > 0) progress.toFloat() / totalTiles else 0f },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = Color(0xFF2196F3),
                            trackColor = Color.DarkGray,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "$progress / $totalTiles tiles (${String.format("%.1f", percentage)}%)",
                            color = Color.LightGray,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Please keep the app open.",
                            color = Color.Yellow,
                            fontSize = 12.sp
                        )
                    }
                    
                    DownloadState.COMPLETE -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Complete",
                            tint = Color.Green,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Download Complete!",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "The SOS map tracker will now work perfectly offline.",
                            color = Color.LightGray,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
                        ) {
                            Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
