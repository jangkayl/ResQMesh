package com.example.testresqmesh.feature.comms.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.R
import com.example.testresqmesh.core.utils.MediaHelper

@Composable
fun ModernVoicePlayer(
    audioBase64: String,
    mediaHelper: MediaHelper,
    modifier: Modifier = Modifier
) {
    val activePlayingAudio by mediaHelper.currentlyPlayingAudio.collectAsState()
    val isPlaying = activePlayingAudio == audioBase64

    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val wavePulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wavePulse"
    )

    // A dark, modern, high-contrast pill container that pops off the orange background
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { mediaHelper.togglePlayVoiceMail(audioBase64) },
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF1E242B),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play/Pause Button
            Surface(
                modifier = Modifier.size(38.dp),
                shape = CircleShape,
                color = if (isPlaying) Color(0xFFFF7043) else Color(0xFF2E3842)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (isPlaying) "Pause voice note" else "Play voice note",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Dynamic Waveform Visualizer
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(26.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val barHeights = listOf(
                    8.dp, 16.dp, 12.dp, 24.dp, 18.dp, 22.dp, 10.dp, 
                    19.dp, 24.dp, 14.dp, 20.dp, 16.dp, 12.dp, 8.dp
                )

                barHeights.forEachIndexed { index, height ->
                    val dynamicHeight = if (isPlaying) {
                        val factor = if (index % 2 == 0) wavePulse else (1.3f - wavePulse)
                        (height.value * factor).coerceIn(4f, 26f).dp
                    } else {
                        height
                    }
                    val barColor = if (isPlaying) {
                        if (index % 3 == 0) Color(0xFFFF7043) else Color(0xFFE2E8F0)
                    } else {
                        Color(0xFF64748B)
                    }

                    Box(
                        modifier = Modifier
                            .width(3.5.dp)
                            .height(dynamicHeight)
                            .clip(RoundedCornerShape(2.dp))
                            .background(barColor)
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Text(
                text = if (isPlaying) "PLAYING" else "VOICE",
                color = if (isPlaying) Color(0xFFFF7043) else Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
