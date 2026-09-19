package com.example.testresqmesh.feature.radar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.core.ui.components.buttons.ResQButton
import com.example.testresqmesh.core.ui.components.buttons.ButtonVariant
import com.example.testresqmesh.core.ui.components.feedback.ResQStatusChip
import com.example.testresqmesh.core.ui.components.feedback.ResQStatusTone
import com.example.testresqmesh.core.ui.components.layout.ResQGlassSurface
import com.example.testresqmesh.core.ui.theme.ResQSize
import com.example.testresqmesh.core.ui.theme.Spacing

/**
 * A presentation-only peer detail screen. It deliberately avoids making range, route, or
 * encryption claims that are not supplied by its caller.
 */
@Composable
fun ResponderTrackerScreen(nodeName: String, onBack: () -> Unit, onChat: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.Medium, vertical = Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(ResQSize.MinimumTouchTarget)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Peer details", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text("Current mesh information", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        ResQGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.Large),
            shadowElevation = 8.dp
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                MeshSignalIllustration()
                Text(nodeName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                ResQStatusChip("Reachability updates in Mesh", ResQStatusTone.Information, icon = Icons.Outlined.Hub)
                Text(
                    "Distance, direction, and next-hop delivery are not shown until the mesh provides verified evidence.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(modifier = Modifier.padding(Spacing.Medium), verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(44.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) }
                }
                Spacer(Modifier.width(Spacing.Medium))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Location sharing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Shown in an SOS map only when the sender included a location.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.weight(1f))
        ResQButton(onClick = onChat, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null)
            Spacer(Modifier.width(Spacing.Small))
            Text("Message peer", fontWeight = FontWeight.Bold)
        }
        ResQButton(onClick = onBack, modifier = Modifier.fillMaxWidth(), variant = ButtonVariant.Outline) {
            Text("Back to Mesh", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MeshSignalIllustration() {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    Box(
        modifier = Modifier
            .size(164.dp)
            .drawBehind {
                val radius = size.minDimension / 2
                drawCircle(primary.copy(alpha = 0.07f), radius)
                drawCircle(primary.copy(alpha = 0.20f), radius * 0.68f, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                drawCircle(secondary.copy(alpha = 0.30f), radius * 0.36f, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(modifier = Modifier.size(52.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Hub, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        }
    }
}
