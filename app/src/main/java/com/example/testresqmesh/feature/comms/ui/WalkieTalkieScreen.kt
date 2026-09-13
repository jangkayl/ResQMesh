package com.example.testresqmesh.feature.comms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.theme.ErrorRed
import com.example.testresqmesh.core.ui.theme.SuccessGreen
import com.example.testresqmesh.core.utils.MediaHelper
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel
import com.example.testresqmesh.feature.comms.viewmodel.WalkieTalkieViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalkieTalkieScreen(
    commsViewModel: CommunicationViewModel,
    walkieTalkieViewModel: WalkieTalkieViewModel,
    mediaHelper: MediaHelper
) {
    val isWalkieTalkieMode by walkieTalkieViewModel.isWalkieTalkieMode.collectAsState()
    var isRecording by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Walkie-Talkie", fontWeight = FontWeight.Bold, color = Color.White)
                },
                actions = {
                    val currentChannel by walkieTalkieViewModel.currentChannelId.collectAsState()
                    var expanded by remember { mutableStateOf(false) }

                    Box {
                        TextButton(onClick = { expanded = true }) {
                            Text("CH $currentChannel", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            for (i in 1..5) {
                                DropdownMenuItem(
                                    text = { Text("Channel $i") },
                                    onClick = {
                                        walkieTalkieViewModel.setChannel(i.toString())
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    IconButton(onClick = { walkieTalkieViewModel.toggleWalkieTalkieMode() }) {
                        Icon(
                            Icons.Outlined.SettingsInputAntenna,
                            contentDescription = "Toggle Auto-Play",
                            tint = if (isWalkieTalkieMode) SuccessGreen else Color.Gray,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (isWalkieTalkieMode) "AUTO-PLAY RECEIVER: ACTIVE" else "AUTO-PLAY RECEIVER: OFF",
                color = if (isWalkieTalkieMode) SuccessGreen else Color.Gray,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            val currentSpeaker by walkieTalkieViewModel.currentSpeaker.collectAsState()
            
            if (currentSpeaker != null && isWalkieTalkieMode) {
                Text(
                    text = "🔊 $currentSpeaker is talking...",
                    color = SuccessGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(56.dp))
            }
            
            var volumeGain by remember { mutableStateOf(1.0f) }
            Text("Software Volume Boost: ${"%.1f".format(volumeGain)}x", color = Color.Gray, fontSize = 12.sp)
            androidx.compose.material3.Slider(
                value = volumeGain,
                onValueChange = { 
                    volumeGain = it
                    walkieTalkieViewModel.setVolumeGain(it)
                },
                valueRange = 1.0f..5.0f,
                modifier = Modifier.padding(horizontal = 64.dp, vertical = 8.dp)
            )

            var isLiveMode by remember { mutableStateOf(false) }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 32.dp)) {
                Text("Voice Note", color = if (!isLiveMode) Color.White else Color.Gray, fontWeight = FontWeight.Bold)
                Switch(
                    checked = isLiveMode,
                    onCheckedChange = { isLiveMode = it },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Text("Live Audio", color = if (isLiveMode) Color.White else Color.Gray, fontWeight = FontWeight.Bold)
            }

            // BIG PTT BUTTON
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(if (isRecording) ErrorRed else MaterialTheme.colorScheme.primary)
                    .border(
                        width = 8.dp, 
                        color = if (isRecording) ErrorRed.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), 
                        shape = CircleShape
                    )
                    .pointerInput(isLiveMode) {
                        detectTapGestures(
                            onPress = {
                                isRecording = true
                                if (isLiveMode) {
                                    walkieTalkieViewModel.startLiveAudio()
                                } else {
                                    mediaHelper.startRecording()
                                }
                                
                                kotlinx.coroutines.withTimeoutOrNull(if (isLiveMode) Long.MAX_VALUE else 10000L) {
                                    tryAwaitRelease()
                                }
                                
                                if (isRecording) {
                                    isRecording = false
                                    if (isLiveMode) {
                                        val audioBase64 = walkieTalkieViewModel.stopLiveAudio()
                                        if (audioBase64 != null) {
                                            commsViewModel.sendPublicMessage(
                                                text = "Live Stream Archive",
                                                imageBase64 = null,
                                                audioBase64 = audioBase64
                                            )
                                        }
                                    } else {
                                        val audioBase64 = mediaHelper.stopRecording()
                                        if (audioBase64 != null) {
                                            commsViewModel.sendPublicMessage(
                                                text = "Voice Message",
                                                imageBase64 = null,
                                                audioBase64 = audioBase64
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Hold to Talk",
                    modifier = Modifier.size(80.dp),
                    tint = Color.White
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = if (isRecording) "RECORDING... RELEASE TO BROADCAST" else "HOLD TO TALK",
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Transmits to all nearby nodes on Public Channel",
                color = Color.Gray,
                fontSize = 14.sp
            )
        }
    }
}
