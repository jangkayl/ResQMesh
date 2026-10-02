package com.example.testresqmesh.feature.comms.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import com.example.testresqmesh.feature.comms.model.SosMeshState
import com.example.testresqmesh.feature.comms.model.sosTransmissionLabel
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.testresqmesh.core.ui.components.layout.ResQAuroraBackground
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.model.ConversationPolicy
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.utils.MediaHelper
import com.example.testresqmesh.data.local.entity.SosAlertEntity
import com.example.testresqmesh.data.repository.asMessage
import com.example.testresqmesh.feature.comms.ui.components.ChatBubble
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel
import kotlinx.coroutines.flow.first

private fun getEmergencyColor(emergencyType: String): Color {
    val upper = emergencyType.uppercase()
    return when {
        upper.contains("MED") -> Color(0xFFFF334B)
        upper.contains("FIRE") -> Color(0xFFFF6D00)
        upper.contains("TRAP") -> Color(0xFFFFB300)
        else -> Color(0xFF8B5CF6)
    }
}

private fun getEmergencyIcon(emergencyType: String): ImageVector {
    val upper = emergencyType.uppercase()
    return when {
        upper.contains("MED") -> Icons.Default.LocalHospital
        upper.contains("FIRE") -> Icons.Default.LocalFireDepartment
        upper.contains("TRAP") -> Icons.Default.Construction
        else -> Icons.Default.Warning
    }
}

@Composable
fun RadioHistoryScreen(vm: CommunicationViewModel, media: MediaHelper, onBack: () -> Unit) {
    val all by vm.allPublicMessages.collectAsState()
    val states by vm.conversationStates.collectAsState()
    var selected by remember { mutableStateOf<String?>(null) }
    BackHandler { if (selected != null) selected = null else onBack() }

    if (selected != null) {
        PublicConversationScreen(
            vm = vm,
            media = media,
            kind = "RADIO",
            channel = selected!!,
            title = "Radio Channel $selected",
            readOnly = false,
            onBack = { selected = null }
        )
        return
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Tactical Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Radio"
                    )
                }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Radio Conversations",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Offline voice notes & chatter by channel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items((1..5).map { it.toString() }) { ch ->
                    val messages = ConversationPolicy.messages(all, "RADIO", ch)
                    val key = ConversationPolicy.key("RADIO", ch)
                    val read = states.firstOrNull { it.conversationId == key }?.lastReadAt ?: 0L
                    val unreadCount = messages.count { !it.isMine && it.timestamp > read }
                    val latestMsg = messages.lastOrNull()
                    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

                    Surface(
                        onClick = { selected = ch },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (unreadCount > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else if (isLight) MaterialTheme.colorScheme.outlineVariant
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        shadowElevation = if (isLight) 1.dp else 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "CH $ch",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(Modifier.width(14.dp))

                            Column(Modifier.widthIn(max = 260.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Channel $ch",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
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
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = latestMsg?.let { msg ->
                                        val sender = NodeIdentity.displayNameOf(msg.senderName)
                                        val preview = if (msg.audioBase64 != null) "🎤 Voice note" else msg.text
                                        "$sender: $preview"
                                    } ?: "No recorded messages yet",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (latestMsg != null) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Icon(
                                imageVector = Icons.Outlined.ChevronRight,
                                contentDescription = "Open Channel $ch",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

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

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SosThreadScreen(
    vm: CommunicationViewModel,
    media: MediaHelper,
    id: String,
    onBack: () -> Unit,
    onMap: (com.example.testresqmesh.core.model.ChatMessage) -> Unit
) {
    val alerts by vm.sosAlerts.collectAsState()
    val alert = alerts.firstOrNull { it.sosId == id }
    var confirmEnd by remember(id) { mutableStateOf(false) }
    var showActivity by remember(id) { mutableStateOf(false) }
    val activity by remember(id) { vm.sosRepository.events(id) }.collectAsState(initial = emptyList())
    val leave = { vm.silenceSos(id); onBack() }
    BackHandler(onBack = leave)

    if (showActivity) {
        AlertDialog(
            onDismissRequest = { showActivity = false },
            title = {
                Text(
                    text = "SOS Audit History",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            confirmButton = {
                TextButton(onClick = { showActivity = false }) {
                    Text("Close")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Events retained on this phone. Reconnect sync repairs the latest SOS state.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(
                        Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(activity, key = { it.eventId }) { row ->
                            val event = com.example.testresqmesh.data.repository.SosProtocol.json
                                .decodeFromString<com.example.testresqmesh.data.repository.SosEvent>(row.eventJson)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "REV ${row.revision}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = if (event.ended) "SOS ended" else if (event.revision == 1L) "SOS created" else "Location updated",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(event.updatedAt)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (event.latitude != null) {
                                        Text(
                                            text = "Accuracy: ${event.accuracyMeters?.toInt() ?: "unknown"} m",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    if (confirmEnd && alert != null) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = ResQTheme.colors.sos,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "End your SOS?",
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "Reachable devices on the mesh will be instructed to stop this emergency alert. Disconnected devices will learn it has ended upon reconnecting.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.endSos(id)
                        confirmEnd = false
                    },
                    modifier = Modifier.testTag("sos_confirm_end"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB42318))
                ) {
                    Text("End my SOS", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEnd = false }, modifier = Modifier.testTag("sos_keep_active")) {
                    Text("Keep active")
                }
            }
        )
    }

    val categoryColor = alert?.emergencyType?.let { getEmergencyColor(it) } ?: ResQTheme.colors.sos
    val categoryIcon = alert?.emergencyType?.let { getEmergencyIcon(it) } ?: Icons.Default.Warning

    PublicConversationScreen(
        vm = vm,
        media = media,
        kind = "SOS",
        sosId = id,
        title = alert?.let { "${it.originName} · ${it.emergencyType}" } ?: "Loading SOS",
        readOnly = alert?.ended != false,
        onBack = leave,
        header = { isCompact ->
            if (alert != null) {
                if (isCompact) {
                    CompactSosHeader(
                        alert = alert,
                        categoryColor = categoryColor,
                        categoryIcon = categoryIcon,
                        onMap = {
                            vm.silenceSos(id)
                            onMap(alert.asMessage())
                        },
                        onEnd = { confirmEnd = true },
                        onSilence = { vm.silenceSos(id) },
                        onActivity = { showActivity = true },
                        isOwner = vm.sosRepository.owns(alert)
                    )
                } else {
                    ExpandedSosHeader(
                        alert = alert,
                        categoryColor = categoryColor,
                        categoryIcon = categoryIcon,
                        onMap = {
                            vm.silenceSos(id)
                            onMap(alert.asMessage())
                        },
                        onEnd = { confirmEnd = true },
                        onSilence = { vm.silenceSos(id) },
                        onActivity = { showActivity = true },
                        isOwner = vm.sosRepository.owns(alert)
                    )
                }
            }
        }
    )
}

internal fun formatSosHeaderTime(timestamp: Long): String {
    if (timestamp < 1_000_000_000_000L) {
        return java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
            .format(java.util.Date(System.currentTimeMillis()))
    }
    return runCatching {
        java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
            .format(java.util.Date(timestamp))
    }.getOrDefault("Just now")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpandedSosHeader(
    alert: SosAlertEntity,
    categoryColor: Color,
    categoryIcon: ImageVector,
    onMap: () -> Unit,
    onEnd: () -> Unit,
    onSilence: () -> Unit,
    onActivity: () -> Unit,
    isOwner: Boolean
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val statusTimestamp = when {
        alert.ended -> alert.updatedAt.takeIf { it > 1_000_000_000_000L } ?: alert.createdAt
        alert.updatedAt > 1_000_000_000_000L -> alert.updatedAt
        alert.createdAt > 1_000_000_000_000L -> alert.createdAt
        else -> System.currentTimeMillis()
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.5.dp,
            if (alert.ended) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            else categoryColor.copy(alpha = 0.7f)
        ),
        shadowElevation = if (isLight) 3.dp else 6.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Row 1: Left: Badges; Right: Audit History Action (Balances the card header)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category & Status Badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = categoryColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, categoryColor.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = categoryIcon,
                                contentDescription = null,
                                tint = categoryColor,
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
                        shape = RoundedCornerShape(8.dp),
                        color = when {
                            alert.ended -> MaterialTheme.colorScheme.surfaceVariant
                            alert.locallySilenced -> Color(0xFFFFB300).copy(alpha = 0.15f)
                            else -> Color(0xFFDC2626).copy(alpha = 0.15f)
                        },
                        border = BorderStroke(
                            1.dp,
                            when {
                                alert.ended -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                alert.locallySilenced -> Color(0xFFFFB300).copy(alpha = 0.4f)
                                else -> Color(0xFFDC2626).copy(alpha = 0.4f)
                            }
                        )
                    ) {
                        Text(
                            text = when {
                                alert.ended -> "ENDED"
                                alert.locallySilenced -> "SILENCED LOCALLY"
                                else -> "ACTIVE SOS"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = when {
                                alert.ended -> MaterialTheme.colorScheme.onSurfaceVariant
                                alert.locallySilenced -> if (isLight) Color(0xFFB45309) else Color(0xFFFDE68A)
                                else -> if (isLight) Color(0xFFDC2626) else Color(0xFFFCA5A5)
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // History button in top-right corner to balance header
                Surface(
                    onClick = onActivity,
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.testTag("sos_activity")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Activity history",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "History",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Telemetry Box (Tactical HUD, spans 100% of card width)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isLight) 0.35f else 0.25f)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // Row 1: Transmission status (Left) + Timestamp (Right)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (alert.ended) Icons.Default.CheckCircle else Icons.Default.Sensors,
                                contentDescription = null,
                                tint = if (alert.ended) ResQTheme.colors.success else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (alert.ended) "Emergency ended" else sosTransmissionLabel(alert.transmission),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = formatSosHeaderTime(statusTimestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    // Row 2: Location telemetry (Spans full width)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = if (alert.latitude != null) ResQTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = when {
                                alert.latitude != null -> "Pinned (${String.format(java.util.Locale.US, "%.4f, %.4f", alert.latitude, alert.longitude)}) · ±${alert.accuracyMeters?.toInt() ?: "?"}m"
                                alert.ended -> "Location telemetry unavailable"
                                else -> "Acquiring GPS location..."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Action Buttons: Symmetrically distributed across 100% width!
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onMap,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Map", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }

                if (!alert.ended && isOwner) {
                    Button(
                        onClick = onEnd,
                        modifier = Modifier
                            .weight(1.4f)
                            .heightIn(min = 44.dp)
                            .testTag("sos_end"),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB42318))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("End my SOS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                } else if (!alert.ended) {
                    OutlinedButton(
                        onClick = onSilence,
                        modifier = Modifier
                            .weight(1.4f)
                            .heightIn(min = 44.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (alert.locallySilenced) "Silenced on this phone" else "Silence on this phone",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("sos_silence"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactSosHeader(
    alert: SosAlertEntity,
    categoryColor: Color,
    categoryIcon: ImageVector,
    onMap: () -> Unit,
    onEnd: () -> Unit,
    onSilence: () -> Unit,
    onActivity: () -> Unit,
    isOwner: Boolean
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (alert.ended) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            else categoryColor.copy(alpha = 0.65f)
        ),
        shadowElevation = if (isLight) 2.dp else 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Badge
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = categoryColor.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, categoryColor.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = null,
                        tint = categoryColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = alert.emergencyType,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            // Status Pill
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = when {
                    alert.ended -> MaterialTheme.colorScheme.surfaceVariant
                    alert.locallySilenced -> Color(0xFFFFB300).copy(alpha = 0.15f)
                    else -> ResQTheme.colors.sos.copy(alpha = 0.15f)
                }
            ) {
                Text(
                    text = when {
                        alert.ended -> "ENDED"
                        alert.locallySilenced -> "SILENCED"
                        else -> "ACTIVE"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }

            Spacer(Modifier.width(6.dp))

            // Compact Location Telemetry
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = if (alert.latitude != null) "±${alert.accuracyMeters?.toInt() ?: "?"}m" else if (alert.ended) "No GPS" else "Acquiring...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.weight(1f))

            // Action Buttons
            IconButton(
                onClick = onMap,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = "Map",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }

            if (!alert.ended && isOwner) {
                Button(
                    onClick = onEnd,
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("sos_end"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB42318))
                ) {
                    Text(
                        "End",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (!alert.ended) {
                OutlinedButton(
                    onClick = onSilence,
                    modifier = Modifier.height(30.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsOff,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        if (alert.locallySilenced) "Silenced" else "Silence",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.testTag("sos_silence")
                    )
                }
            }

            IconButton(
                onClick = onActivity,
                modifier = Modifier.size(32.dp).testTag("sos_activity")
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "Activity history",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun PublicConversationScreen(
    vm: CommunicationViewModel,
    media: MediaHelper,
    kind: String,
    channel: String = "",
    sosId: String = "",
    title: String,
    readOnly: Boolean = false,
    onBack: () -> Unit,
    header: @Composable (isCompact: Boolean) -> Unit = {}
) {
    val all by vm.allPublicMessages.collectAsState()
    val states by vm.conversationStates.collectAsState()
    val key = ConversationPolicy.key(kind, channel, sosId)
    val messages = ConversationPolicy.messages(all, kind, channel, sosId)
    val displayMessages = remember(messages) { messages.asReversed() }
    var input by remember(key) { mutableStateOf(states.firstOrNull { it.conversationId == key }?.draft.orEmpty()) }
    var restored by remember(key) { mutableStateOf(false) }

    LaunchedEffect(states, key) {
        val saved = states.firstOrNull { it.conversationId == key }
        if (!restored && saved != null) {
            if (input.isEmpty()) input = saved.draft
            restored = true
        }
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val latestMessageId = messages.lastOrNull()?.id
    LaunchedEffect(latestMessageId, key) {
        vm.read(key)
        messages.filter { !it.isMine && it.seenBy.isEmpty() }.forEach { vm.markMessageAsSeen(it.id, false) }
        if (latestMessageId != null) {
            listState.scrollToItem(0)
        }
    }

    @Composable
    fun ConversationContainer(content: @Composable () -> Unit) {
        if (kind == "RADIO") {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                content = content
            )
        } else {
            ResQAuroraBackground(Modifier.fillMaxSize()) {
                content()
            }
        }
    }

    ConversationContainer {
        BoxWithConstraints(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        val compactLayout = maxHeight < 520.dp || LocalDensity.current.fontScale > 1.3f
        val compactControls = kind == "SOS" && compactLayout
        val density = LocalDensity.current
        val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
        var headerHeightDp by remember { mutableStateOf(160.dp) }

        Column(Modifier.fillMaxSize()) {
            // Content Area: Full-height message stream with floating header overlay
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Layer 1: Message Stream (Scrolls underneath floating header)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(
                        top = headerHeightDp + 8.dp,
                        bottom = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom)
                ) {
                    items(displayMessages, key = { it.id }) { message ->
                        ChatBubble(message, media)
                    }
                    if (messages.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (kind == "SOS") "No replies yet." else "No Radio notes yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // Layer 2: Floating Header (Solid Top Bar Mask + Solid Details Card)
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            val heightInDp = with(density) { coordinates.size.height.toDp() }
                            if (headerHeightDp != heightInDp) {
                                headerHeightDp = heightInDp
                            }
                        }
                ) {
                    // Solid Top Bar Mask (Non-transparent, anchors title, back button, and status bar)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = if (isLight) 1.5.dp else 2.5.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBack, modifier = Modifier.testTag("conversation_back")) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = when (kind) {
                                        "RADIO" -> "Channel $channel Broadcast"
                                        "SOS" -> "Emergency Distress Thread"
                                        else -> "Broadcast Conversation"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Floating Details Card Slot (Solid card with outer margin for floating effect)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        header(compactControls)
                    }
                }
            }

            // Bottom Composer / Status Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                if (readOnly) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = if (kind == "SOS") "Read-only history · This emergency conversation has ended." else "Read-only history",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp).testTag("conversation_read_only"),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = {
                                input = it
                                restored = true
                                vm.draft(key, it)
                            },
                            placeholder = {
                                Text(if (kind == "RADIO") "Message this Radio channel…" else "Reply to this SOS…")
                            },
                            modifier = Modifier.weight(1f).testTag("conversation_input"),
                            maxLines = if (compactLayout) 2 else 4,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )

                        FilledIconButton(
                            onClick = {
                                if (input.isNotBlank()) {
                                    vm.sendConversation(kind, channel, sosId, input.trim())
                                    input = ""
                                }
                            },
                            enabled = input.isNotBlank(),
                            modifier = Modifier.size(50.dp).testTag("conversation_send"),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send Message",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    }
}
