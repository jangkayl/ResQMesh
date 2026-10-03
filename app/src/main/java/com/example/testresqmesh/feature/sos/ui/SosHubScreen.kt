package com.example.testresqmesh.feature.sos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.platform.testTag
import com.example.testresqmesh.feature.comms.model.SosMeshState
import com.example.testresqmesh.feature.comms.model.sosTransmissionLabel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.model.ConversationPolicy
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.utils.MediaHelper
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel
import com.example.testresqmesh.feature.sos.ui.components.getEmergencyColor
import com.example.testresqmesh.feature.sos.ui.components.getEmergencyIcon

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SosHubScreen(
    vm: CommunicationViewModel,
    media: MediaHelper,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit
) {
    val meshStatus by vm.sosMeshStatus.collectAsState()
    val meshAccent = if (meshStatus.state == SosMeshState.CONNECTED) ResQTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant
    val alerts by vm.sosAlerts.collectAsState()
    val all by vm.allPublicMessages.collectAsState()
    val states by vm.conversationStates.collectAsState()
    BackHandler(onBack = onBack)

    val userOwnsActiveSos = alerts.any { !it.ended && vm.sosRepository.owns(it) }
    val myActiveSos = alerts.firstOrNull { !it.ended && vm.sosRepository.owns(it) }
    val activeAlerts = remember(alerts) { alerts.filter { !it.ended }.sortedByDescending { it.createdAt } }
    val endedAlerts = remember(alerts) { alerts.filter { it.ended }.sortedByDescending { it.createdAt } }
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
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

            // Section 1: Active Emergencies
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = if (activeAlerts.isNotEmpty()) {
                        if (isLight) Color(0xFFFEE2E2) else Color(0xFF450A0A)
                    } else {
                        if (isLight) Color(0xFFECFDF5) else Color(0xFF062D24)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (activeAlerts.isNotEmpty()) Color(0xFFEF4444).copy(alpha = 0.5f)
                        else Color(0xFF10B981).copy(alpha = 0.35f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (activeAlerts.isNotEmpty()) Color(0xFFDC2626) else Color(0xFF10B981))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "ACTIVE EMERGENCIES (${activeAlerts.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = if (activeAlerts.isNotEmpty()) {
                                if (isLight) Color(0xFF991B1B) else Color(0xFFFCA5A5)
                            } else {
                                if (isLight) Color(0xFF065F46) else Color(0xFFA7F3D0)
                            },
                            letterSpacing = 1.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (activeAlerts.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFDC2626)
                            ) {
                                Text(
                                    text = "LIVE DISTRESS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isLight) Color(0xFFD1FAE5) else Color(0xFF064E3B),
                                border = BorderStroke(1.dp, if (isLight) Color(0xFF6EE7B7) else Color(0xFF047857))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (isLight) Color(0xFF065F46) else Color(0xFFA7F3D0),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "ALL CLEAR",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = if (isLight) Color(0xFF065F46) else Color(0xFFA7F3D0)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (activeAlerts.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = if (isLight) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(
                            1.2.dp,
                            if (isLight) Color(0xFF10B981).copy(alpha = 0.35f) else Color(0xFF10B981).copy(alpha = 0.25f)
                        ),
                        shadowElevation = if (isLight) 1.dp else 2.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                modifier = Modifier.size(54.dp),
                                shape = CircleShape,
                                color = if (isLight) Color(0xFFD1FAE5) else Color(0xFF064E3B)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "All Clear",
                                        tint = if (isLight) Color(0xFF059669) else Color(0xFF34D399),
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "All Clear · No Active Distress",
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Your phone is actively listening on offline mesh radio frequencies. Any emergency SOS alert broadcast in your area will appear here immediately.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Spacer(Modifier.height(14.dp))
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isLight) Color(0xFFECFDF5) else Color(0xFF062D24),
                                border = BorderStroke(1.dp, if (isLight) Color(0xFFA7F3D0) else Color(0xFF047857))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sensors,
                                        contentDescription = null,
                                        tint = if (isLight) Color(0xFF059669) else Color(0xFF34D399),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Mesh Monitor Active · ${meshStatus.label}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isLight) Color(0xFF065F46) else Color(0xFFA7F3D0)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                items(activeAlerts, key = { it.sosId }) { alert ->
                    val messages = ConversationPolicy.messages(all, "SOS", sosId = alert.sosId)
                    val read = states.firstOrNull { it.conversationId == "SOS:${alert.sosId}" }?.lastReadAt ?: 0L
                    val unreadCount = messages.count { !it.isMine && it.timestamp > read }
                    val accentColor = getEmergencyColor(alert.emergencyType)
                    val accentIcon = getEmergencyIcon(alert.emergencyType)

                    Surface(
                        onClick = { onOpen(alert.sosId) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = if (isLight) Color(0xFFFFF8F8) else Color(0xFF201416),
                        border = BorderStroke(
                            2.dp,
                            if (alert.locallySilenced) Color(0xFFD97706) else accentColor
                        ),
                        shadowElevation = if (isLight) 3.dp else 5.dp
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            // Row 1: Category on Left, High-Visibility Status Pill on Right
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = accentColor.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = accentIcon,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = alert.emergencyType,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (alert.locallySilenced) Color(0xFFD97706) else Color(0xFFDC2626)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(Color.White)
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            text = if (alert.locallySilenced) "SILENCED" else "ACTIVE SOS",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            Text(
                                text = "${alert.originName} · ${sosTransmissionLabel(alert.transmission)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(Modifier.height(4.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = if (alert.latitude != null) ResQTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (alert.latitude != null) "Coordinates pinned (±${alert.accuracyMeters?.toInt() ?: "?"}m)"
                                    else "Location unavailable",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = messages.lastOrNull()?.text ?: "No replies in thread",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (unreadCount > 0) {
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFB42318)
                                    ) {
                                        Text(
                                            text = "$unreadCount new",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Outlined.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Section 2: Ended Alerts
            if (endedAlerts.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "ENDED ALERTS (${endedAlerts.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                }

                items(endedAlerts, key = { it.sosId }) { alert ->
                    val messages = ConversationPolicy.messages(all, "SOS", sosId = alert.sosId)
                    val read = states.firstOrNull { it.conversationId == "SOS:${alert.sosId}" }?.lastReadAt ?: 0L
                    val unreadCount = messages.count { !it.isMine && it.timestamp > read }

                    Surface(
                        onClick = { onOpen(alert.sosId) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = if (isLight) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, if (isLight) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        shadowElevation = if (isLight) 1.dp else 2.dp
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                Text(
                                    text = "${alert.originName} · ${alert.emergencyType}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                ) {
                                    Text(
                                        text = "ENDED",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${messages.size} total messages · ${messages.lastOrNull()?.text ?: "No replies"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (unreadCount > 0) {
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFB42318)
                                    ) {
                                        Text(
                                            text = "$unreadCount new",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Outlined.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

        }
    }
}
