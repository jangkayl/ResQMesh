package com.example.testresqmesh.feature.incident.ui.components

import android.text.format.DateUtils
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.SafetyOrange
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.theme.TacticalBlack
import com.example.testresqmesh.core.ui.theme.TacticalCarbon
import com.example.testresqmesh.data.local.entity.DomainEventEntity
import com.example.testresqmesh.data.local.entity.IncidentEntity
import com.example.testresqmesh.data.local.entity.IncidentOfferEntity
import com.example.testresqmesh.feature.incident.viewmodel.IncidentAction
import com.example.testresqmesh.feature.incident.viewmodel.incidentPresentation
import kotlinx.coroutines.launch
import org.json.JSONObject

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
    onViewLocation: (Double, Double, String, String) -> Unit,
    identityLoading: Boolean = localUserId == null,
    identityError: String? = null
) {
    val presentation = remember(incident, offers, localUserId, localSigningKey, events, identityLoading, identityError) {
        incidentPresentation(incident, offers, localUserId, localSigningKey, events, identityLoading, identityError)
    }

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    var showOfferSheet by rememberSaveable { mutableStateOf(false) }
    var offerDraftNote by rememberSaveable { mutableStateOf("") }
    var showAllOffers by rememberSaveable { mutableStateOf(false) }
    var showActivitySection by rememberSaveable { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    // Dialog state: null, "choose:<offerId>:<name>:<note>", "revoke", "withdraw", "resolve", "cancel", "decline"
    var confirmDialogAction by remember { mutableStateOf<String?>(null) }

    val isNight = MaterialTheme.colorScheme.background == TacticalBlack || MaterialTheme.colorScheme.surface == TacticalCarbon
    val statusBg = if (isNight) Color(0xFF1E293B) else Color(0xFF2C5282)

    val urgencyColor = when (incident.severity.lowercase()) {
        "critical" -> ResQTheme.colors.sos
        "serious" -> ResQTheme.colors.warning
        else -> MaterialTheme.colorScheme.primary
    }

    val appliedEvents = remember(events) {
        events.filter { it.applied }.sortedBy { it.timestamp }
    }

    val formattedReachability = when {
        leadReachability.equals("Local Device", ignoreCase = true) || leadReachability.equals("This phone", ignoreCase = true) -> "This phone"
        leadReachability.equals("Direct", ignoreCase = true) -> "Direct ready connection"
        leadReachability.equals("Relayed", ignoreCase = true) -> "Reachable through another phone"
        leadReachability.equals("Checking", ignoreCase = true) -> "Checking connection"
        else -> "Unavailable on mesh"
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(2.dp)
            ) {
                Box(modifier = Modifier.size(width = 36.dp, height = 4.dp))
            }
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Large, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Incident details",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (presentation.secondary.isNotEmpty()) {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            presentation.secondary.forEach { action ->
                                DropdownMenuItem(
                                    text = { Text(action.label) },
                                    onClick = {
                                        showMoreMenu = false
                                        when (action) {
                                            IncidentAction.CANCEL -> confirmDialogAction = "cancel"
                                            IncidentAction.REVOKE -> confirmDialogAction = "revoke"
                                            IncidentAction.WITHDRAW -> confirmDialogAction = "withdraw"
                                            IncidentAction.DECLINE -> confirmDialogAction = "decline"
                                            IncidentAction.RESOLVE -> confirmDialogAction = "resolve"
                                            IncidentAction.RELEASE -> onReleaseAssignment()
                                            IncidentAction.ACKNOWLEDGE -> onAcknowledge()
                                            else -> Unit
                                        }
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .widthIn(max = 680.dp)
                    .verticalScroll(scrollState)
                    .padding(horizontal = Spacing.Large, vertical = Spacing.Small),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Action / Sync message banner
                if (actionMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = actionMessage,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Warning banner (stale selection, withdrawn helper, removed helper)
                if (presentation.warning != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = presentation.warning,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // 1. Author Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = incident.creatorName.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Reported as ${incident.creatorName}",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (presentation.isReporter) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = "You reported this",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }

                            val relativeTime = DateUtils.getRelativeTimeSpanString(
                                incident.updatedAt,
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_RELATIVE
                            ).toString()

                            Text(
                                text = "$relativeTime · ${incident.incidentType}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = urgencyColor
                    ) {
                        Text(
                            text = incident.severity.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                // 2. Post Headline (Title)
                Text(
                    text = presentation.title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 28.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )

                // 3. Post Body (Natural, Unboxed Emergency Details)
                if (incident.description.isNotBlank()) {
                    Text(
                        text = incident.description,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 4. Attached Location (Media Attachment Banner)
                val hasCoordinates = incident.latitude != null && incident.longitude != null
                val hasLandmark = incident.areaDescription.isNotBlank()
                if (hasLandmark || hasCoordinates) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = ResQTheme.colors.sos
                                )
                                Text(
                                    text = if (hasLandmark) incident.areaDescription else "Attached GPS Location",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (hasCoordinates) {
                                Button(
                                    onClick = {
                                        onViewLocation(
                                            incident.latitude!!,
                                            incident.longitude!!,
                                            incident.creatorName,
                                            incident.description
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SafetyOrange
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Map,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "VIEW ON MAP",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp
                                        ),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. Context & Guidance Bar (Social Thread Status)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = presentation.status,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                            )
                        }

                        if (presentation.myOffer != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ResQTheme.colors.successContainer.copy(alpha = 0.8f),
                                border = BorderStroke(0.5.dp, ResQTheme.colors.success.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "You offered",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = ResQTheme.colors.onSuccessContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                                )
                            }
                        }
                    }

                    if (presentation.nextStep.isNotBlank()) {
                        Text(
                            text = presentation.nextStep,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 17.sp
                            ),
                            color = if (isNight) SafetyOrange else MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    thickness = 0.5.dp
                )

                    // ==========================================
                    // CARD 5: SELECTED HELPER SPOTLIGHT (IF ANY)
                    // ==========================================
                    val selectedHelper = presentation.selectedOffer
                    if (selectedHelper != null || (incident.workflowVersion != 2 && incident.primaryResponderName != null)) {
                        val helperName = selectedHelper?.helperName ?: incident.primaryResponderName ?: "Helper"
                        val isConfirmed = incident.status == "RESPONDING" || incident.selectionConfirmedAt != null
                        val statusLabel = if (isConfirmed) "Confirmed" else "Awaiting confirmation"
                        val statusBadgeColor = if (isConfirmed) ResQTheme.colors.success else ResQTheme.colors.warning

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.5.dp, statusBadgeColor),
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isConfirmed) Icons.Outlined.CheckCircle else Icons.Outlined.Schedule,
                                            contentDescription = null,
                                            tint = statusBadgeColor,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = if (isConfirmed) "CONFIRMED RESPONDER" else "ASSIGNED RESPONDER",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.8.sp
                                            ),
                                            color = statusBadgeColor
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = statusBadgeColor.copy(alpha = 0.14f),
                                        border = BorderStroke(0.5.dp, statusBadgeColor.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = statusLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = statusBadgeColor,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                        color = statusBadgeColor.copy(alpha = 0.15f),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = helperName.take(1).uppercase(),
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = statusBadgeColor
                                            )
                                        }
                                    }

                                    Column {
                                        Text(
                                            text = helperName,
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Mesh radio: $formattedReachability",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (presentation.secondary.contains(IncidentAction.REVOKE)) {
                                    OutlinedButton(
                                        onClick = { confirmDialogAction = "revoke" },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 40.dp)
                                    ) {
                                        Text("Select a different helper", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }

                    // People offering help section
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.People,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "People offering help (${presentation.helpers.size})",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.semantics { heading() }
                                )
                            }
                        }

                        if (presentation.helpers.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier.padding(Spacing.Large),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No offers yet on this phone.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            val displayedHelpers = if (showAllOffers || presentation.helpers.size <= 3) {
                                presentation.helpers
                            } else {
                                presentation.helpers.take(3)
                            }

                            displayedHelpers.forEach { helper ->
                                TacticalHelperOfferCard(
                                    helper = helper,
                                    onChoose = if (helper.canChoose) {
                                        {
                                            confirmDialogAction = "choose:${helper.offer.offerId}:${helper.offer.helperName}:${helper.offer.note}"
                                        }
                                    } else null,
                                    onEdit = if (helper.canEditOffer) {
                                        {
                                            offerDraftNote = helper.offer.note
                                            showOfferSheet = true
                                        }
                                    } else null,
                                    onWithdraw = if (helper.isMe && IncidentAction.WITHDRAW in presentation.secondary) {
                                        { confirmDialogAction = "withdraw" }
                                    } else null
                                )
                            }

                            if (presentation.helpers.any { it.isMe && !it.canEditOffer && it.offer.offerId == incident.selectionOfferId } &&
                                !presentation.terminal) {
                                Text("This offer is selected. Withdraw before changing your assistance.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (presentation.helpers.size > 3) {
                                TextButton(
                                    onClick = { showAllOffers = !showAllOffers },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (showAllOffers) "Show fewer offers" else "Show all ${presentation.helpers.size} offers"
                                    )
                                }
                            }
                        }
                    }

                    // Activity section
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(Spacing.Medium)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showActivitySection = !showActivitySection }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Activity (${appliedEvents.size})",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }

                                Icon(
                                    imageVector = if (showActivitySection) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (showActivitySection) {
                                Spacer(Modifier.height(8.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    appliedEvents.forEach { evt ->
                                        val eventDescription = when (evt.eventType) {
                                            "INCIDENT_CREATED" -> "${evt.actorName} reported this incident"
                                            "INCIDENT_OFFER_UPDATED" -> "${evt.actorName} offered help"
                                            "INCIDENT_OFFER_WITHDRAWN" -> "${evt.actorName} withdrew their offer"
                                            "INCIDENT_LEAD_SELECTED" -> {
                                                val target = runCatching { JSONObject(evt.payloadJson).optString("helperName") }.getOrNull()?.takeIf { it.isNotBlank() } ?: "a helper"
                                                "${evt.actorName} selected $target"
                                            }
                                            "INCIDENT_LEAD_CONFIRMED" -> "${evt.actorName} confirmed they can help"
                                            "INCIDENT_LEAD_DECLINED" -> "${evt.actorName} declined the selection"
                                            "INCIDENT_LEAD_REVOKED" -> "${evt.actorName} removed the selected helper"
                                            "INCIDENT_RESOLVED" -> "${evt.actorName} marked the incident resolved"
                                            "INCIDENT_CANCELLED" -> "${evt.actorName} cancelled the incident"
                                            "INCIDENT_ACKNOWLEDGED" -> "${evt.actorName} acknowledged the request"
                                            "INCIDENT_ASSIGNED" -> "${evt.actorName} volunteered to respond"
                                            "INCIDENT_RESPONSE_STARTED" -> "${evt.actorName} started responding"
                                            "INCIDENT_ASSIGNMENT_RELEASED" -> "${evt.actorName} released assignment"
                                            else -> "${evt.actorName} performed an update"
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = eventDescription,
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = DateUtils.getRelativeTimeSpanString(evt.timestamp).toString(),
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }

            // Docked primary action bar (pinned at bottom of sheet)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.Large)
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .widthIn(max = 680.dp)
                ) {
                    when (val primary = presentation.primary) {
                        IncidentAction.OFFER -> {
                            Button(
                                onClick = {
                                    offerDraftNote = ""
                                    showOfferSheet = true
                                },
                                enabled = !actionBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 56.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isNight) SafetyOrange else Color(0xFF244F7A),
                                    contentColor = if (isNight) TacticalBlack else Color.White
                                )
                            ) {
                                Text("Offer help", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        IncidentAction.EDIT_OFFER -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        offerDraftNote = presentation.myOffer?.note ?: ""
                                        showOfferSheet = true
                                    },
                                    enabled = !actionBusy,
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 56.dp)
                                ) {
                                    Text("Edit my offer", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                }
                                OutlinedButton(
                                    onClick = { confirmDialogAction = "withdraw" },
                                    enabled = !actionBusy,
                                    modifier = Modifier.defaultMinSize(minHeight = 56.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("Withdraw")
                                }
                            }
                        }
                        IncidentAction.REVIEW -> {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        scrollState.animateScrollTo(scrollState.maxValue)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 56.dp)
                            ) {
                                Text("Review helpers", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        IncidentAction.REVIEW_SELECTED -> {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        scrollState.animateScrollTo(scrollState.maxValue)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 56.dp)
                            ) {
                                Text("Review selected helper", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        IncidentAction.CONFIRM -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onConfirmLead,
                                    enabled = !actionBusy,
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 56.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ResQTheme.colors.success
                                    )
                                ) {
                                    Text("Confirm I can help", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                }
                                OutlinedButton(
                                    onClick = { confirmDialogAction = "decline" },
                                    enabled = !actionBusy,
                                    modifier = Modifier.defaultMinSize(minHeight = 56.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("I can’t help")
                                }
                            }
                        }
                        IncidentAction.RESOLVE -> {
                            Button(
                                onClick = { confirmDialogAction = "resolve" },
                                enabled = !actionBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ResQTheme.colors.success
                                )
                            ) {
                                Text("Mark resolved", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        // Legacy actions
                        IncidentAction.ACKNOWLEDGE -> {
                            Button(
                                onClick = onAcknowledge,
                                enabled = !actionBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 56.dp)
                            ) {
                                Text("Acknowledge request", fontSize = 16.sp)
                            }
                        }
                        IncidentAction.ASSIGN -> {
                            Button(
                                onClick = onAssign,
                                enabled = !actionBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 56.dp)
                            ) {
                                Text("Volunteer to respond", fontSize = 16.sp)
                            }
                        }
                        IncidentAction.START -> {
                            Button(
                                onClick = onStartResponse,
                                enabled = !actionBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ResQTheme.colors.sos
                                )
                            ) {
                                Text("Start responding", fontSize = 16.sp)
                            }
                        }
                        null -> {
                            if (presentation.terminal) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier.padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (incident.status == "RESOLVED") "Incident is resolved (read-only)" else "Incident was cancelled (read-only)",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else if (presentation.isReporter) {
                                // Reporter with no offers or waiting
                                OutlinedButton(
                                    onClick = {},
                                    enabled = false,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 56.dp)
                                ) {
                                    Text("Waiting for helper offers on mesh", fontSize = 15.sp)
                                }
                            } else if (incident.status == "RESPONDING" && presentation.selectedOffer?.helperKey == localSigningKey) {
                                // Confirmed helper explanation
                                Button(
                                    onClick = {},
                                    enabled = false,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 56.dp)
                                ) {
                                    Text("You are confirmed to help", fontSize = 15.sp)
                                }
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }

        // Offer Help / Edit Offer Dialog
        if (showOfferSheet) {
            var localNote by rememberSaveable { mutableStateOf(offerDraftNote) }
            val isNoteValid = localNote.trim().isNotEmpty() && localNote.trim().length <= 240
            var showError by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showOfferSheet = false },
                title = {
                    Text(
                        text = "Your help offer",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Describe the supplies or assistance you can provide.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = localNote,
                            onValueChange = { localNote = it },
                            label = { Text("How can you help? *") },
                            placeholder = { Text("e.g. I have a first-aid kit and can assist within 10 minutes.") },
                            minLines = 3,
                            maxLines = 5,
                            isError = showError && !isNoteValid,
                            supportingText = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    if (showError && !isNoteValid) {
                                        Text("Offer note must be 1–240 characters", color = MaterialTheme.colorScheme.error)
                                    } else {
                                        Text("Describe what assistance or equipment you have")
                                    }
                                    Text("${localNote.trim().length}/240")
                                }
                            },
                            colors = incidentTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (!isNoteValid) {
                                showError = true
                            } else {
                                onOffer(localNote.trim())
                                showOfferSheet = false
                            }
                        },
                        enabled = !actionBusy
                    ) {
                        Text("Save help offer")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showOfferSheet = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Confirmation Dialogs
        confirmDialogAction?.let { actionStr ->
            when {
                actionStr.startsWith("choose:") -> {
                    val parts = actionStr.split(":", limit = 4)
                    val offerId = parts.getOrNull(1) ?: ""
                    val helperName = parts.getOrNull(2) ?: "this helper"
                    val offerNote = parts.getOrNull(3) ?: ""

                    AlertDialog(
                        onDismissRequest = { confirmDialogAction = null },
                        title = { Text("Choose $helperName?") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("$helperName offered: \"$offerNote\"")
                                Text("This helper must confirm they can assist before they are confirmed.")
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                onSelectLead(offerId)
                                confirmDialogAction = null
                            }) {
                                Text("Choose helper")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmDialogAction = null }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
                actionStr == "revoke" -> {
                    AlertDialog(
                        onDismissRequest = { confirmDialogAction = null },
                        title = { Text("Remove selection?") },
                        text = { Text("This removes the selected helper from this incident so you can review offers again.") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    onRevokeLead()
                                    confirmDialogAction = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Remove selection")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmDialogAction = null }) {
                                Text("Keep helper")
                            }
                        }
                    )
                }
                actionStr == "withdraw" -> {
                    AlertDialog(
                        onDismissRequest = { confirmDialogAction = null },
                        title = { Text("Withdraw offer?") },
                        text = { Text("Are you sure you want to withdraw your help offer?") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    onWithdrawOffer()
                                    confirmDialogAction = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Withdraw offer")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmDialogAction = null }) {
                                Text("Keep offer")
                            }
                        }
                    )
                }
                actionStr == "decline" -> {
                    AlertDialog(
                        onDismissRequest = { confirmDialogAction = null },
                        title = { Text("Decline selection?") },
                        text = { Text("Let the reporter know that you cannot help with this incident right now.") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    onDeclineLead()
                                    confirmDialogAction = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("I can’t help")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmDialogAction = null }) {
                                Text("Back")
                            }
                        }
                    )
                }
                actionStr == "resolve" -> {
                    AlertDialog(
                        onDismissRequest = { confirmDialogAction = null },
                        title = { Text("Mark incident resolved?") },
                        text = { Text("This records on this phone that help was completed and closes the incident.") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    onResolve()
                                    confirmDialogAction = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ResQTheme.colors.success)
                            ) {
                                Text("Mark resolved")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmDialogAction = null }) {
                                Text("Keep open")
                            }
                        }
                    )
                }
                actionStr == "cancel" -> {
                    AlertDialog(
                        onDismissRequest = { confirmDialogAction = null },
                        title = { Text("Cancel incident?") },
                        text = { Text("This will cancel the emergency request on this phone.") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    onCancel()
                                    confirmDialogAction = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Cancel incident")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmDialogAction = null }) {
                                Text("Keep incident")
                            }
                        }
                    )
                }
            }
        }
    }
}
