package com.example.testresqmesh.feature.incident.ui.components

import android.text.format.DateUtils
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.testresqmesh.core.ui.components.dialogs.ResQConfirmationDialog
import com.example.testresqmesh.core.ui.components.feedback.ResQStatusChip
import com.example.testresqmesh.core.ui.components.feedback.ResQStatusTone
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.data.local.entity.DomainEventEntity
import com.example.testresqmesh.data.local.entity.IncidentEntity
import com.example.testresqmesh.data.local.entity.IncidentOfferEntity
import com.example.testresqmesh.feature.comms.ui.components.TacticalLocationCard
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentDetailSheet(
    incident: IncidentEntity,
    events: List<DomainEventEntity>,
    localUserId: String?,
    offers: List<IncidentOfferEntity>,
    localSigningKey: String?,
    leadReachability: String,
    actionBusy: Boolean,
    actionMessage: String?,
    onOffer: (String) -> Unit,
    onWithdrawOffer: () -> Unit,
    onSelectLead: (String) -> Unit,
    onConfirmLead: () -> Unit,
    onDeclineLead: () -> Unit,
    onRevokeLead: () -> Unit,
    onDismiss: () -> Unit,
    onAcknowledge: () -> Unit,
    onAssign: () -> Unit,
    onStartResponse: () -> Unit,
    onResolve: () -> Unit,
    onCancel: () -> Unit,
    onReleaseAssignment: () -> Unit,
    onViewLocation: (Double, Double, String, String) -> Unit
) {
    val isCreator = localUserId != null && localUserId == incident.creatorId
    val isPrimaryResponder = localUserId != null && localUserId == incident.primaryResponderId
    val isSelectedHelper = incident.selectedHelperKey != null && incident.selectedHelperKey == localSigningKey
    val activeOffers = remember(offers) { offers.filter { !it.withdrawn } }
    val myOffer = remember(offers, localSigningKey) { offers.firstOrNull { it.helperKey == localSigningKey && !it.withdrawn } }
    var confirmAction by remember { mutableStateOf<String?>(null) }
    var selectedTabIndex by rememberSaveable(incident.incidentId) { mutableIntStateOf(0) }
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss • MMM dd", Locale.getDefault()) }

    val severityColor = when (incident.severity.lowercase()) {
        "critical" -> ResQTheme.colors.sos
        "serious" -> ResQTheme.colors.warning
        else -> MaterialTheme.colorScheme.primary
    }

    val (statusTone, statusLabel) = when (incident.status) {
        "OPEN" -> ResQStatusTone.Warning to if (incident.workflowVersion == 2) "OPEN FOR OFFERS" else "OPEN • UNCLAIMED"
        "AWAITING_HELPER" -> ResQStatusTone.Warning to "AWAITING HELPER"
        "ACKNOWLEDGED" -> ResQStatusTone.Information to "ACKNOWLEDGED"
        "ASSIGNED" -> ResQStatusTone.Information to "ASSIGNED"
        "RESPONDING" -> ResQStatusTone.Critical to if (incident.workflowVersion == 2) "HELP IN PROGRESS" else "RESPONDING"
        "RESOLVED" -> ResQStatusTone.Success to "RESOLVED"
        "CANCELLED" -> ResQStatusTone.Neutral to "CANCELLED"
        else -> ResQStatusTone.Neutral to incident.status
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 20.dp),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // 1. Top Tactical Header
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.Large, vertical = Spacing.Medium),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = severityColor.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, severityColor.copy(alpha = 0.4f)),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = getIncidentIcon(incident.incidentType),
                                        contentDescription = null,
                                        tint = severityColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "${incident.incidentType.uppercase()} EMERGENCY",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.3.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "ID: #${incident.incidentId.take(8).uppercase()} • ${DateUtils.getRelativeTimeSpanString(incident.createdAt)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ResQStatusChip(
                                label = statusLabel,
                                tone = statusTone,
                                modifier = Modifier.height(26.dp)
                            )

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .size(32.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // 3. Segmented Tactical Tab Row
                PrimaryTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.Assignment, contentDescription = null, modifier = Modifier.size(15.dp))
                                Text(
                                    "Briefing",
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Outlined.Handshake, contentDescription = null, modifier = Modifier.size(15.dp))
                                Text(
                                    "Helpers (${activeOffers.size})",
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal
                                )
                                if (activeOffers.isNotEmpty()) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${activeOffers.size}",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 9.sp
                                                ),
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    )
                    Tab(
                        selected = selectedTabIndex == 2,
                        onClick = { selectedTabIndex = 2 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(15.dp))
                                Text(
                                    "Timeline (${events.size})",
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTabIndex == 2) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    )
                }

                // 4. Tab Content Container (Scrollable)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Spacing.Large, vertical = Spacing.Medium),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
                    ) {
                        when (selectedTabIndex) {
                            0 -> {
                                // TAB 0: SITUATION BRIEFING & OVERVIEW
                                TacticalLifecycleProgress(status = incident.status, workflowVersion = incident.workflowVersion)

                                // Incident Briefing Card
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(Spacing.Large),
                                        verticalArrangement = Arrangement.spacedBy(Spacing.Small)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = severityColor.copy(alpha = 0.15f),
                                                border = BorderStroke(1.dp, severityColor.copy(alpha = 0.4f))
                                            ) {
                                                Text(
                                                    text = "${incident.severity.uppercase()} SEVERITY",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                                    color = severityColor,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }

                                            Text(
                                                text = "Created ${DateUtils.getRelativeTimeSpanString(incident.createdAt)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        if (incident.areaDescription.isNotBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.LocationOn,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = incident.areaDescription,
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }

                                        if (incident.description.isNotBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = incident.description,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )

                                        // Origin Attribution
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Reported By",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = if (isCreator) "${incident.creatorName} (You)" else incident.creatorName,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = if (isCreator) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Spacer(Modifier.width(12.dp))

                                            Column(
                                                modifier = Modifier.weight(1f),
                                                horizontalAlignment = Alignment.End
                                            ) {
                                                Text(
                                                    text = if (incident.workflowVersion == 2) "Lead Helper" else "Primary Responder",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = when {
                                                        incident.primaryResponderName == null -> "No lead selected"
                                                        isPrimaryResponder -> "${incident.primaryResponderName} (You)"
                                                        else -> incident.primaryResponderName
                                                    },
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = if (incident.primaryResponderName != null) MaterialTheme.colorScheme.primary else ResQTheme.colors.warning,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }

                                // Geographic Intelligence & Map
                                if (incident.latitude != null && incident.longitude != null) {
                                    Text(
                                        text = "GEOGRAPHIC INTELLIGENCE",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 0.8.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TacticalLocationCard(
                                        latitude = incident.latitude,
                                        longitude = incident.longitude,
                                        senderName = incident.creatorName,
                                        noteText = incident.locationCapturedAt?.let {
                                            "Location captured • ${DateUtils.getRelativeTimeSpanString(it)}"
                                        },
                                        isMine = isCreator,
                                        onTrackOnMap = {
                                            onViewLocation(
                                                incident.latitude,
                                                incident.longitude,
                                                incident.creatorName,
                                                "${incident.incidentType} Incident: ${incident.areaDescription.ifBlank { incident.description }}"
                                            )
                                        }
                                    )
                                    incident.locationAccuracyMeters?.let {
                                        Text(
                                            text = "Estimated GPS Fix Accuracy: ±${it.toInt()} meters",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 4.dp)
                                        )
                                    }
                                }

                                // Shortcut button to coordination tab
                                if (incident.workflowVersion == 2 && activeOffers.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = { selectedTabIndex = 1 },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Outlined.Handshake, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("View ${activeOffers.size} Volunteer Offers", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            1 -> {
                                // TAB 1: HELPERS & OFFERS COORDINATION HUB
                                if (incident.workflowVersion == 2) {
                                    IncidentCoordinationHub(
                                        incident = incident,
                                        offers = offers,
                                        events = events,
                                        localUserId = localUserId,
                                        localSigningKey = localSigningKey,
                                        leadReachability = leadReachability,
                                        busy = actionBusy,
                                        actionMessage = actionMessage,
                                        onOffer = onOffer,
                                        onWithdrawOffer = onWithdrawOffer,
                                        onSelectLead = onSelectLead,
                                        onConfirmLead = onConfirmLead,
                                        onDeclineLead = onDeclineLead,
                                        onRevokeLead = onRevokeLead
                                    )
                                } else {
                                    // Legacy v1 workflow controls
                                    LegacyWorkflowPanel(
                                        incident = incident,
                                        isCreator = isCreator,
                                        isPrimaryResponder = isPrimaryResponder,
                                        onAcknowledge = onAcknowledge,
                                        onAssign = onAssign,
                                        onStartResponse = onStartResponse,
                                        onReleaseAssignment = onReleaseAssignment,
                                        onResolveRequest = { confirmAction = "resolve" },
                                        onCancelRequest = { confirmAction = "cancel" }
                                    )
                                }
                            }
                            2 -> {
                                // TAB 2: INCIDENT AUDIT TIMELINE
                                Text(
                                    text = "OFFLINE DOMAIN EVENT LOG",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.8.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(Spacing.Medium)) {
                                        if (events.isEmpty()) {
                                            Text(
                                                text = "No recorded domain events yet.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        } else {
                                            events.forEachIndexed { index, evt ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        modifier = Modifier.weight(1f, fill = false),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        Surface(
                                                            shape = CircleShape,
                                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Icon(
                                                                    imageVector = getEventIcon(evt.eventType),
                                                                    contentDescription = null,
                                                                    modifier = Modifier.size(16.dp),
                                                                    tint = MaterialTheme.colorScheme.primary
                                                                )
                                                            }
                                                        }
                                                        Column {
                                                            Text(
                                                                text = formatEventName(evt.eventType),
                                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                            Text(
                                                                text = "By ${evt.actorName} • rev ${evt.logicalVersion}${if (!evt.signature.isNullOrBlank()) " • Signed ✓" else ""}",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }

                                                    Text(
                                                        text = dateFormat.format(Date(evt.timestamp)),
                                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (index < events.size - 1) {
                                                    HorizontalDivider(
                                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                                        modifier = Modifier.padding(vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(80.dp))
                    }
                }

                // 5. Persistent Docked Tactical Action Bar
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.Large, vertical = 10.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // High-Priority: Selected Helper Confirmation Prompt
                        if (incident.status == "AWAITING_HELPER" && isSelectedHelper) {
                            Text(
                                text = "You are designated Lead Helper. Confirm readiness to respond:",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = ResQTheme.colors.warning
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = onConfirmLead,
                                    enabled = !actionBusy,
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ResQTheme.colors.success)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Confirm Response", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = onDeclineLead,
                                    enabled = !actionBusy,
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Decline")
                                }
                            }
                        } else if (isCreator && incident.status == "RESPONDING") {
                            // Reporter Resolution
                            Button(
                                onClick = { confirmAction = "resolve" },
                                enabled = !actionBusy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ResQTheme.colors.success)
                            ) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Mark Incident Resolved", fontWeight = FontWeight.Bold)
                            }
                        } else if (isCreator && incident.status !in setOf("RESOLVED", "CANCELLED")) {
                            // Reporter Cancellation
                            OutlinedButton(
                                onClick = { confirmAction = "cancel" },
                                enabled = !actionBusy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Outlined.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Cancel Incident (End Alert)")
                            }
                        } else if (!isCreator && incident.status == "OPEN" && myOffer == null && incident.workflowVersion == 2) {
                            // Quick Action for prospective helper to jump to offers tab
                            Button(
                                onClick = { selectedTabIndex = 1 },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Outlined.Handshake, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Offer Assistance as Volunteer", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation Dialogs
    confirmAction?.let { action ->
        if (action == "resolve") {
            ResQConfirmationDialog(
                title = "Resolve Incident?",
                message = "Mark this request resolved on this phone? Other phones may update after reconnecting.",
                confirmText = "Mark Resolved",
                cancelText = "Keep Active",
                icon = Icons.Outlined.CheckCircle,
                isDestructive = false,
                onConfirm = {
                    onResolve()
                    confirmAction = null
                },
                onDismiss = { confirmAction = null }
            )
        } else if (action == "cancel") {
            ResQConfirmationDialog(
                title = "Cancel Incident?",
                message = "Cancel this request on this phone? Other phones may update after reconnecting.",
                confirmText = "Cancel Incident",
                cancelText = "Keep Open",
                icon = Icons.Outlined.WarningAmber,
                isDestructive = true,
                onConfirm = {
                    onCancel()
                    confirmAction = null
                },
                onDismiss = { confirmAction = null }
            )
        }
    }
}

@Composable
private fun TacticalLifecycleProgress(status: String, workflowVersion: Int = 2) {
    val stageIndex = when (status) {
        "OPEN" -> 1
        "ACKNOWLEDGED", "AWAITING_HELPER" -> 2
        "ASSIGNED", "RESPONDING" -> 3
        "RESOLVED" -> 4
        "CANCELLED" -> -1
        else -> 1
    }

    val stageTitle = when (status) {
        "OPEN" -> "Stage 1: Open for Assistance"
        "AWAITING_HELPER" -> "Stage 2: Lead Selected • Awaiting Confirmation"
        "ACKNOWLEDGED" -> "Stage 2: Acknowledged by Peers"
        "ASSIGNED" -> "Stage 3: Assigned to Primary Responder"
        "RESPONDING" -> "Stage 3: Active Response in Progress"
        "RESOLVED" -> "Stage 4: Incident Resolved & Closed"
        "CANCELLED" -> "Alert Cancelled"
        else -> status
    }

    val stageExplanation = when (status) {
        "OPEN" -> "Saved locally • Other phones may receive this request when a path is available."
        "AWAITING_HELPER" -> "Reporter selected a lead helper • Awaiting their confirmation on mesh."
        "ACKNOWLEDGED" -> "An acknowledgement was recorded locally • Waiting for an assignment."
        "ASSIGNED" -> "A participant claimed this incident • Connection and progress may change."
        "RESPONDING" -> "Lead helper confirmed field response underway."
        "RESOLVED" -> "Response actions complete • Emergency concluded."
        "CANCELLED" -> "Emergency alert was cancelled by the reporting user."
        else -> ""
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (status == "CANCELLED") {
            Row(
                modifier = Modifier.padding(Spacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Cancel,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = "INCIDENT CANCELLED • ALERT TERMINATED",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = stageExplanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.Medium),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stageTitle,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.3.sp
                    ),
                    color = if (status == "RESOLVED") ResQTheme.colors.success else MaterialTheme.colorScheme.primary
                )

                // 4 Tactical Progress Segments
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (i in 1..4) {
                        val isFilled = stageIndex > i
                        val isCurrent = stageIndex == i
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    when {
                                        isFilled -> ResQTheme.colors.success
                                        isCurrent -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                        )
                    }
                }

                Text(
                    text = stageExplanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LegacyWorkflowPanel(
    incident: IncidentEntity,
    isCreator: Boolean,
    isPrimaryResponder: Boolean,
    onAcknowledge: () -> Unit,
    onAssign: () -> Unit,
    onStartResponse: () -> Unit,
    onReleaseAssignment: () -> Unit,
    onResolveRequest: () -> Unit,
    onCancelRequest: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            when (incident.status) {
                "OPEN", "ACKNOWLEDGED" -> {
                    if (!isCreator) {
                        Button(
                            onClick = onAssign,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Outlined.NearMe, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Claim & Respond", fontWeight = FontWeight.Bold)
                        }
                        if (incident.status == "OPEN") {
                            OutlinedButton(
                                onClick = onAcknowledge,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Outlined.Done, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Acknowledge Incident", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                "ASSIGNED" -> {
                    if (isPrimaryResponder) {
                        Button(
                            onClick = onStartResponse,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ResQTheme.colors.warning)
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.DirectionsRun, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Start En Route / Responding", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onReleaseAssignment,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Release Assignment")
                        }
                    }
                }
                "RESPONDING" -> {
                    if (isPrimaryResponder) {
                        Button(
                            onClick = onResolveRequest,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ResQTheme.colors.success)
                        ) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Mark Incident Resolved", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private fun getEventIcon(eventType: String): ImageVector = when (eventType) {
    "INCIDENT_CREATED" -> Icons.Outlined.AddCircleOutline
    "INCIDENT_ACKNOWLEDGED" -> Icons.Outlined.Done
    "INCIDENT_ASSIGNED" -> Icons.Outlined.PersonAdd
    "INCIDENT_RESPONSE_STARTED" -> Icons.AutoMirrored.Outlined.DirectionsRun
    "INCIDENT_RESOLVED" -> Icons.Outlined.CheckCircle
    "INCIDENT_CANCELLED" -> Icons.Outlined.Cancel
    "INCIDENT_ASSIGNMENT_RELEASED" -> Icons.Outlined.PersonRemove
    "INCIDENT_OFFER_UPDATED" -> Icons.Outlined.Handshake
    "INCIDENT_OFFER_WITHDRAWN" -> Icons.Outlined.Cancel
    "INCIDENT_LEAD_SELECTED" -> Icons.Outlined.CheckCircle
    "INCIDENT_LEAD_CONFIRMED" -> Icons.Outlined.DoneAll
    "INCIDENT_LEAD_DECLINED" -> Icons.Outlined.Close
    "INCIDENT_LEAD_REVOKED" -> Icons.Outlined.PersonRemove
    else -> Icons.Outlined.History
}

private fun formatEventName(eventType: String): String = when (eventType) {
    "INCIDENT_CREATED" -> "Incident Created"
    "INCIDENT_ACKNOWLEDGED" -> "Receipt Acknowledged"
    "INCIDENT_ASSIGNED" -> "Responder Assigned"
    "INCIDENT_RESPONSE_STARTED" -> "Response In Progress"
    "INCIDENT_RESOLVED" -> "Incident Resolved"
    "INCIDENT_CANCELLED" -> "Incident Cancelled"
    "INCIDENT_ASSIGNMENT_RELEASED" -> "Assignment Released"
    "INCIDENT_OFFER_UPDATED" -> "Help Offer Updated"
    "INCIDENT_OFFER_WITHDRAWN" -> "Help Offer Withdrawn"
    "INCIDENT_LEAD_SELECTED" -> "Lead Helper Selected"
    "INCIDENT_LEAD_CONFIRMED" -> "Helper Confirmed"
    "INCIDENT_LEAD_DECLINED" -> "Helper Declined"
    "INCIDENT_LEAD_REVOKED" -> "Lead Selection Revoked"
    else -> eventType.replace("_", " ")
}
