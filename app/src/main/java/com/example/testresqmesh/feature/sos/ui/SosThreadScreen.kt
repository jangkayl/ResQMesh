package com.example.testresqmesh.feature.sos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.utils.MediaHelper
import com.example.testresqmesh.data.repository.asMessage
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel
import com.example.testresqmesh.feature.comms.ui.PublicConversationScreen
import com.example.testresqmesh.feature.sos.ui.components.CompactSosHeader
import com.example.testresqmesh.feature.sos.ui.components.ExpandedSosHeader
import com.example.testresqmesh.feature.sos.ui.components.getEmergencyColor
import com.example.testresqmesh.feature.sos.ui.components.getEmergencyIcon

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
