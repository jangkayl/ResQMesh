package com.example.testresqmesh.feature.comms.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.core.utils.MediaHelper
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.components.layout.ResQGlassSurface
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel
import com.example.testresqmesh.feature.comms.viewmodel.WalkieTalkieViewModel

@Composable
fun WalkieTalkieScreen(
    commsViewModel: CommunicationViewModel,
    walkieTalkieViewModel: WalkieTalkieViewModel,
    mediaHelper: MediaHelper
) {
    val receiverOn by walkieTalkieViewModel.isWalkieTalkieMode.collectAsState()
    val channel by walkieTalkieViewModel.currentChannelId.collectAsState()
    val speaker by walkieTalkieViewModel.currentSpeaker.collectAsState()
    var recording by remember { mutableStateOf(false) }
    var liveAudio by remember { mutableStateOf(false) }
    var channelsOpen by remember { mutableStateOf(false) }
    val signal = MaterialTheme.colorScheme.primary
    val microphoneContent = if (recording) ResQTheme.colors.onSos else MaterialTheme.colorScheme.onPrimary

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (recording) 1.6f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = if (recording) 0f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    Column(
        Modifier.fillMaxSize().padding(horizontal = Spacing.Medium),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(Spacing.Large))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { 
                Text("Voice console", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                Text("Voice for the current channel.", color = MaterialTheme.colorScheme.onSurfaceVariant) 
            }
            Box {
                TextButton(onClick = { channelsOpen = true }) { Text("CH $channel", fontWeight = FontWeight.Black) }
                DropdownMenu(expanded = channelsOpen, onDismissRequest = { channelsOpen = false }) { 
                    (1..5).forEach { number -> 
                        DropdownMenuItem(
                            text = { Text("Channel $number") }, 
                            onClick = { walkieTalkieViewModel.setChannel(number.toString()); channelsOpen = false }
                        ) 
                    } 
                }
            }
        }
        Spacer(Modifier.height(Spacing.Large))
        ResQGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            contentPadding = PaddingValues(Spacing.Large),
            shadowElevation = 8.dp
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) { 
                    Icon(Icons.Outlined.GraphicEq, null, tint = if (receiverOn) ResQTheme.colors.success else signal)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { 
                        Text("Voice receiver", fontWeight = FontWeight.Black)
                        Text(if (receiverOn) "Listening" else "Off", color = if (receiverOn) ResQTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant) 
                    }
                    Switch(checked = receiverOn, onCheckedChange = { walkieTalkieViewModel.toggleWalkieTalkieMode() }) 
                }
                if (speaker != null && receiverOn) { 
                    Spacer(Modifier.height(14.dp))
                    Text("$speaker is speaking", fontWeight = FontWeight.Bold, color = ResQTheme.colors.success) 
                }
            }
        }
        Spacer(Modifier.height(Spacing.Medium))
        Row(verticalAlignment = Alignment.CenterVertically) { 
            Text("Voice note", fontWeight = if (!liveAudio) FontWeight.Black else FontWeight.Normal)
            Switch(checked = liveAudio, onCheckedChange = { liveAudio = it }, modifier = Modifier.padding(horizontal = 10.dp))
            Text("Live audio", fontWeight = if (liveAudio) FontWeight.Black else FontWeight.Normal) 
        }
        
        Spacer(Modifier.weight(1f))
        
        Box(contentAlignment = Alignment.Center) {
            if (recording) {
                Box(
                    modifier = Modifier
                        .size(184.dp)
                        .scale(pulseScale)
                        .background(color = ResQTheme.colors.sos.copy(alpha = pulseAlpha), shape = CircleShape)
                )
            }
            Surface(
                modifier = Modifier.size(184.dp).semantics {
                    contentDescription = if (liveAudio) "Hold to send live audio" else "Hold to record a voice note"
                }.pointerInput(liveAudio) {
                    detectTapGestures(onPress = {
                        recording = true
                        if (liveAudio) walkieTalkieViewModel.startLiveAudio() else mediaHelper.startRecording()
                        tryAwaitRelease()
                        recording = false
                        if (liveAudio) walkieTalkieViewModel.stopLiveAudio() else mediaHelper.stopRecording()?.let { audio -> commsViewModel.sendPublicMessage("Voice message", null, audio) }
                    })
                },
                shape = CircleShape,
                color = if (recording) ResQTheme.colors.sos else signal,
                shadowElevation = 12.dp
            ) { 
                Box(contentAlignment = Alignment.Center) { 
                    Icon(Icons.Default.Mic, "Hold to talk", tint = microphoneContent, modifier = Modifier.size(72.dp)) 
                } 
            }
        }
        
        Spacer(Modifier.height(32.dp))
        Text(if (recording) "Release to send" else "Hold to talk", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = if (recording) ResQTheme.colors.sos else MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(if (liveAudio) "Live audio uses available nearby connections." else "Voice notes are sent to this channel.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.Large))
    }
}
