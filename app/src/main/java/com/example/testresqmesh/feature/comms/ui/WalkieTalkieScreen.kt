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
                modifier = Modifier.padding(bottom = 64.dp)
            )

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
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isRecording = true
                                mediaHelper.startRecording()
                                
                                kotlinx.coroutines.withTimeoutOrNull(10000L) {
                                    tryAwaitRelease()
                                }
                                
                                if (isRecording) {
                                    isRecording = false
                                    val audioBase64 = mediaHelper.stopRecording()
                                    if (audioBase64 != null) {
                                        // Send to broadcast (null target)
                                        commsViewModel.sendPublicMessage(
                                            text = "Voice Message",
                                            imageBase64 = null,
                                            audioBase64 = audioBase64
                                        )
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
