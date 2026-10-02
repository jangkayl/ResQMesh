package com.example.testresqmesh.core.ui.components.layout

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.core.ui.theme.ResQTheme

@Composable
fun ActiveSosReminder(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "capsuleBeacon")
    val beaconAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beaconAlpha"
    )

    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val containerColor = if (isLight) Color(0xFFFEE2E2) else Color(0xFF26080B)
    val contentColor = if (isLight) Color(0xFF991B1B) else Color.White
    val subtitleColor = if (isLight) Color(0xFFB91C1C) else Color(0xFFFCA5A5)
    val borderColor = if (isLight) ResQTheme.colors.sos.copy(alpha = 0.55f) else ResQTheme.colors.sos.copy(alpha = 0.85f)

    Surface(
        onClick = onClick,
        modifier = modifier
            .testTag("sos_reminder")
            .heightIn(min = 38.dp)
            .shadow(
                elevation = if (isLight) 2.dp else 8.dp,
                shape = RoundedCornerShape(19.dp),
                ambientColor = if (isLight) Color.Transparent else ResQTheme.colors.sos.copy(alpha = 0.4f),
                spotColor = ResQTheme.colors.sos.copy(alpha = if (isLight) 0.2f else 0.7f)
            ),
        shape = RoundedCornerShape(19.dp),
        color = containerColor,
        border = BorderStroke(1.2.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(ResQTheme.colors.sos.copy(alpha = beaconAlpha))
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = ResQTheme.colors.sos,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "YOUR SOS IS ACTIVE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = contentColor
                )
                Text(
                    text = " · Tap to manage",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = ResQTheme.colors.sos,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** Reserves a strip above content; inherited system insets are consumed here. */
@Composable
fun SosReminderHost(showReminder: Boolean, onOpen: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        if (showReminder) {
            Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                ActiveSosReminder(onClick = onOpen, modifier = Modifier.fillMaxWidth())
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().then(if (showReminder) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier)) { content() }
    }
}
