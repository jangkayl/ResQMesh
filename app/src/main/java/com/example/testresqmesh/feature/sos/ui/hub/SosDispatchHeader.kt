package com.example.testresqmesh.feature.sos.ui.hub

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.platform.testTag
import com.example.testresqmesh.feature.comms.model.sosTransmissionLabel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.data.local.entity.SosAlertEntity
import com.example.testresqmesh.feature.comms.model.SosMeshStatus
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SosDispatchHeader(
    meshStatus: SosMeshStatus,
    meshAccent: Color,
    isLight: Boolean,
    userOwnsActiveSos: Boolean,
    myActiveSos: SosAlertEntity?,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit
) {
    // Tactical Top Header
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to app"
            )
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.widthIn(max = 260.dp)) {
            Text(
                text = "Emergency Dispatch",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "Offline Mesh Distress Monitoring",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isLight) MaterialTheme.colorScheme.surface else meshAccent.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, if (isLight) MaterialTheme.colorScheme.outlineVariant else meshAccent.copy(alpha = 0.35f)),
            shadowElevation = if (isLight) 1.dp else 0.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(meshAccent)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = meshStatus.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("sos_mesh_status")
                )
            }
        }
    }

    // Emergency Call-To-Action Button / Banner
    if (userOwnsActiveSos && myActiveSos != null) {
        Surface(
            onClick = { onOpen(myActiveSos.sosId) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = if (isLight) Color(0xFFFEE2E2) else Color(0xFF26080B),
            border = BorderStroke(
                1.2.dp,
                if (isLight) ResQTheme.colors.sos.copy(alpha = 0.55f) else ResQTheme.colors.sos.copy(alpha = 0.85f)
            ),
            shadowElevation = if (isLight) 1.dp else 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = if (isLight) Color(0xFFDC2626) else Color(0xFFB42318)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "YOUR SOS IS ACTIVE",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = if (isLight) Color(0xFF991B1B) else Color.White
                    )
                    Text(
                        text = "${myActiveSos.emergencyType} · ${sosTransmissionLabel(myActiveSos.transmission)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = if (isLight) Color(0xFFB91C1C) else Color(0xFFFCA5A5)
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = if (isLight) Color(0xFF991B1B) else ResQTheme.colors.sos
                )
            }
        }
    } else {
        Button(
            onClick = onCreate,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFB42318),
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "BROADCAST EMERGENCY SOS",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
        }
    }

    Spacer(Modifier.height(14.dp))

    Text(
        text = "Each emergency maintains an isolated thread. Silencing affects only this device.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(Modifier.height(14.dp))

}
