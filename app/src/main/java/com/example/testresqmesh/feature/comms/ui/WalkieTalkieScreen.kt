package com.example.testresqmesh.feature.comms.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.core.utils.MediaHelper
import com.example.testresqmesh.core.ui.theme.Spacing
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
    val purple = Color(0xFF7442C8)

    Column(
        Modifier.fillMaxSize().background(Color(0xFFFBFAFF)).padding(horizontal = Spacing.Medium),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(Spacing.Large))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Walkie-talkie", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black); Text("Voice for the current channel.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Box {
                TextButton(onClick = { channelsOpen = true }) { Text("CH $channel", fontWeight = FontWeight.Black) }
                DropdownMenu(expanded = channelsOpen, onDismissRequest = { channelsOpen = false }) { (1..5).forEach { number -> DropdownMenuItem(text = { Text("Channel $number") }, onClick = { walkieTalkieViewModel.setChannel(number.toString()); channelsOpen = false }) } }
            }
        }
        Spacer(Modifier.height(Spacing.Large))
        Surface(Modifier.fillMaxWidth(), RoundedCornerShape(28.dp), color = Color.White, shadowElevation = 4.dp) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.GraphicEq, null, tint = if (receiverOn) Color(0xFF18A66A) else purple); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("Voice receiver", fontWeight = FontWeight.Black); Text(if (receiverOn) "On" else "Off", color = if (receiverOn) Color(0xFF18A66A) else MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked = receiverOn, onCheckedChange = { walkieTalkieViewModel.toggleWalkieTalkieMode() }) }
                if (speaker != null && receiverOn) { Spacer(Modifier.height(14.dp)); Text("$speaker is speaking", fontWeight = FontWeight.Bold, color = Color(0xFF18A66A)) }
            }
        }
        Spacer(Modifier.height(Spacing.Medium))
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Voice note", fontWeight = if (!liveAudio) FontWeight.Black else FontWeight.Normal); Switch(checked = liveAudio, onCheckedChange = { liveAudio = it }, modifier = Modifier.padding(horizontal = 10.dp)); Text("Live audio", fontWeight = if (liveAudio) FontWeight.Black else FontWeight.Normal) }
        Spacer(Modifier.weight(1f))
        Surface(
            modifier = Modifier.size(184.dp).pointerInput(liveAudio) {
                detectTapGestures(onPress = {
                    recording = true
                    if (liveAudio) walkieTalkieViewModel.startLiveAudio() else mediaHelper.startRecording()
                    tryAwaitRelease()
                    recording = false
                    if (liveAudio) walkieTalkieViewModel.stopLiveAudio() else mediaHelper.stopRecording()?.let { audio -> commsViewModel.sendPublicMessage("Voice message", null, audio) }
                })
            },
            shape = CircleShape,
            color = if (recording) Color(0xFFE5484D) else purple,
            shadowElevation = 12.dp
        ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Mic, "Hold to talk", tint = Color.White, modifier = Modifier.size(72.dp)) } }
        Spacer(Modifier.height(20.dp))
        Text(if (recording) "Release to send" else "Hold to talk", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(6.dp))
        Text(if (liveAudio) "Live audio uses available nearby connections." else "Voice notes are sent to this channel.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(184.dp))
    }
}
