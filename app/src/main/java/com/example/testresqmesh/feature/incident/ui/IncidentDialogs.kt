package com.example.testresqmesh.feature.incident.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.data.location.DefaultLocationClient
import com.example.testresqmesh.feature.comms.ui.components.TacticalLocationCard
import com.example.testresqmesh.data.local.entity.DomainEventEntity
import com.example.testresqmesh.data.local.entity.IncidentEntity
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun IncidentCard(
    incident: IncidentEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (incident.status) {
        "OPEN" -> ResQTheme.colors.warning
        "ACKNOWLEDGED" -> Color(0xFF2196F3)
        "ASSIGNED" -> Color(0xFF9C27B0)
        "RESPONDING" -> ResQTheme.colors.sos
        "RESOLVED" -> ResQTheme.colors.success
        else -> Color.Gray
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Column(modifier = Modifier.padding(Spacing.Medium)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = incident.incidentType.uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = incident.status,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        ),
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (incident.description.isNotBlank()) {
                Text(
                    text = incident.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
                Spacer(Modifier.height(6.dp))
            }

            if (incident.areaDescription.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = incident.areaDescription,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(6.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Reporter: ${incident.creatorName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                if (incident.primaryResponderName != null) {
                    Text(
                        text = "Responder: ${incident.primaryResponderName}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun CreateIncidentDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String, Double?, Double?, Long?, Float?) -> Unit
) {
    val context = LocalContext.current
    val locationClient = remember { DefaultLocationClient(context) }
    var emergencyType by remember { mutableStateOf("Medical") }
    var severity by remember { mutableStateOf("Critical") }
    var description by remember { mutableStateOf("") }
    var areaDescription by remember { mutableStateOf("") }
    var location by remember { mutableStateOf<android.location.Location?>(null) }
    var isAcquiringLocation by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }
    fun acquireLocation() {
        isAcquiringLocation = true
        locationError = null
        locationClient.requestPinpointLocation { result ->
            location = result
            isAcquiringLocation = false
            if (result == null) locationError = "Unable to get a location. You can still send this incident."
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) acquireLocation()
        else {
            isAcquiringLocation = false
            locationError = "Location permission was not granted."
        }
    }

    val types = listOf("Medical", "Trapped", "Fire", "Injury", "Flood", "Other")
    val severities = listOf("Moderate", "Serious", "Critical")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(Spacing.Large)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Broadcast SOS Incident",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Creates a trackable transactional emergency across the mesh.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(Spacing.Medium))

                Text("Emergency Type", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    types.take(3).forEach { t ->
                        FilterChip(
                            selected = emergencyType == t,
                            onClick = { emergencyType = t },
                            label = { Text(t, fontSize = 11.sp) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    types.drop(3).forEach { t ->
                        FilterChip(
                            selected = emergencyType == t,
                            onClick = { emergencyType = t },
                            label = { Text(t, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.Small))

                Text("Severity", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    severities.forEach { s ->
                        FilterChip(
                            selected = severity == s,
                            onClick = { severity = s },
                            label = { Text(s, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.Small))

                OutlinedTextField(
                    value = areaDescription,
                    onValueChange = { areaDescription = it },
                    label = { Text("Area / Landmark") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(Spacing.Small))
                if (location == null) {
                    OutlinedButton(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            ) acquireLocation()
                            else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        },
                        enabled = !isAcquiringLocation,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isAcquiringLocation) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.LocationOn, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isAcquiringLocation) "Getting location…" else "Add current location")
                    }
                    locationError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Current location attached", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { location = null }) { Text("Remove") }
                    }
                }

                Spacer(Modifier.height(Spacing.Small))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Situation Description") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(Spacing.Large))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSubmit(
                                emergencyType, severity, description, areaDescription,
                                location?.latitude, location?.longitude, location?.time,
                                location?.takeIf { it.hasAccuracy() }?.accuracy
                            )
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Broadcast SOS")
                    }
                }
            }
        }
    }
}

@Composable
fun IncidentDetailDialog(
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
