package com.example.testresqmesh.feature.incident.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.components.location.TacticalLocationCard
import com.example.testresqmesh.data.local.entity.DomainEventEntity
import com.example.testresqmesh.data.local.entity.IncidentEntity
import java.text.SimpleDateFormat
import java.util.*

@Composable
internal fun IncidentDetailDialog(
    incident: IncidentEntity,
    events: List<DomainEventEntity>,
    onDismiss: () -> Unit,
    onAcknowledge: () -> Unit,
    onAssign: () -> Unit,
    onStartResponse: () -> Unit,
    onResolve: () -> Unit,
    onCancel: () -> Unit,
    onReleaseAssignment: () -> Unit,
    localUserId: String?,
    onViewLocation: (Double, Double, String, String) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    var confirmation by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .padding(Spacing.Large)
                    .fillMaxSize()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${incident.incidentType} Incident",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ID: ${incident.incidentId} (v${incident.version})",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(Modifier.height(Spacing.Small))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(Spacing.Medium)) {
                        Text("Status: ${incident.status}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("Severity: ${incident.severity}")
                        if (incident.areaDescription.isNotBlank()) Text("Area: ${incident.areaDescription}")
                        if (incident.description.isNotBlank()) Text("Details: ${incident.description}")
                        Text("Reported by: ${incident.creatorName}")
                        incident.primaryResponderName?.let {
                            Text("Assigned to: $it", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.Medium))

                val isCreator = localUserId != null && localUserId == incident.creatorId
                val isPrimaryResponder = localUserId != null && localUserId == incident.primaryResponderId

                if (incident.latitude != null && incident.longitude != null) {
                    TacticalLocationCard(
                        latitude = incident.latitude,
                        longitude = incident.longitude,
                        senderName = incident.creatorName,
                        noteText = incident.locationCapturedAt?.let { "Reporter location • ${android.text.format.DateUtils.getRelativeTimeSpanString(it)}" },
                        isMine = isCreator,
                        onTrackOnMap = { onViewLocation(incident.latitude, incident.longitude, incident.creatorName, incident.description) }
                    )
                    incident.locationAccuracyMeters?.let {
                        Text("Reported accuracy: ${it.toInt()} m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(Spacing.Medium))
                }

                // Action buttons according to state machine and the authoritative repository policy.
                if (isCreator && incident.status !in setOf("RESOLVED", "CANCELLED")) {
                    Text("Waiting for another responder to acknowledge or claim this incident.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.Small))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (incident.status) {
                        "OPEN" -> if (!isCreator) {
                            Button(onClick = onAcknowledge, modifier = Modifier.weight(1f)) {
                                Text("Acknowledge", fontSize = 12.sp)
                            }
                            Button(onClick = onAssign, modifier = Modifier.weight(1f)) {
                                Text("Respond", fontSize = 12.sp)
                            }
                        }
                        "ACKNOWLEDGED" -> if (!isCreator) {
                            Button(onClick = onAssign, modifier = Modifier.weight(1f)) {
                                Text("Assign to Me", fontSize = 12.sp)
                            }
                        }
                        "ASSIGNED" -> {
                            if (isPrimaryResponder) {
                                Button(onClick = onStartResponse, modifier = Modifier.weight(1f)) {
                                    Text("Start Response", fontSize = 12.sp)
                                }
                                OutlinedButton(onClick = onReleaseAssignment, modifier = Modifier.weight(1f)) {
                                    Text("Release", fontSize = 12.sp)
                                }
                            }
                        }
                        "RESPONDING" -> {
                            if (isPrimaryResponder) {
                                Button(
                                    onClick = { confirmation = "resolve" },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = ResQTheme.colors.success)
                                ) {
                                    Text("Mark Resolved", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    if (isCreator && incident.status != "RESOLVED" && incident.status != "CANCELLED") {
                        OutlinedButton(
                            onClick = { confirmation = "cancel" },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Cancel", fontSize = 12.sp)
                        }
                    }
                }

                confirmation?.let { action ->
                    AlertDialog(
                        onDismissRequest = { confirmation = null },
                        title = { Text(if (action == "cancel") "Cancel incident?" else "Mark incident resolved?") },
                        text = { Text(if (action == "cancel") "This ends the active incident for all connected responders." else "This records that the response is complete.") },
                        confirmButton = { TextButton(onClick = { if (action == "cancel") onCancel() else onResolve(); confirmation = null }) { Text("Confirm") } },
                        dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Keep open") } }
                    )
                }

                Spacer(Modifier.height(Spacing.Medium))

                Text(
                    text = "Event Timeline (Convergence History)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(Spacing.Small))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(events) { evt ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = evt.eventType.replace("_", " "),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "By ${evt.actorName} (v${evt.logicalVersion})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = dateFormat.format(Date(evt.timestamp)),
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
