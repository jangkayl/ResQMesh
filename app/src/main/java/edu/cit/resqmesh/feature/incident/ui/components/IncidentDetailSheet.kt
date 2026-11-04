package edu.cit.resqmesh.feature.incident.ui.components

import edu.cit.resqmesh.feature.incident.ui.detail.IncidentIntroduction
import edu.cit.resqmesh.feature.incident.ui.detail.IncidentLocationGuidance
import edu.cit.resqmesh.feature.incident.ui.detail.SelectedIncidentHelper
import edu.cit.resqmesh.feature.incident.ui.detail.IncidentActivity
import edu.cit.resqmesh.feature.incident.ui.detail.IncidentPrimaryActions
import edu.cit.resqmesh.feature.incident.ui.detail.IncidentConfirmationDialogs
import edu.cit.resqmesh.feature.incident.ui.detail.helperChatTarget
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.cit.resqmesh.core.ui.theme.ResQTheme
import edu.cit.resqmesh.core.ui.theme.Spacing
import edu.cit.resqmesh.core.ui.theme.TacticalBlack
import edu.cit.resqmesh.core.ui.theme.TacticalCarbon
import edu.cit.resqmesh.core.model.DomainEvent
import edu.cit.resqmesh.core.model.Incident
import edu.cit.resqmesh.core.model.IncidentOffer
import edu.cit.resqmesh.feature.incident.model.HelperPresentation
import edu.cit.resqmesh.feature.incident.model.IncidentAction
import edu.cit.resqmesh.feature.incident.model.IncidentPresentation
import edu.cit.resqmesh.feature.incident.model.incidentPresentation
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IncidentDetailSheet(
    incident: Incident,
    events: List<DomainEvent>,
    localUserId: String?,
    offers: List<IncidentOffer>,
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
    identityError: String? = null,
    onDirectChat: (String) -> Unit = {},
    offerWaitSeconds: Int = 0
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

    val formattedReachability = reachabilityLabel(leadReachability)

    BackHandler(onBack = onDismiss)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Incident details",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IncidentMoreActionsMenu(
                        secondaryActions = presentation.secondary.filter { it != presentation.primary },
                        expanded = showMoreMenu,
                        onExpandedChange = { showMoreMenu = it },
                        enabled = !actionBusy
                    ) { action ->
                        when (action) {
                            IncidentAction.CANCEL -> confirmDialogAction = "cancel"
                            IncidentAction.REVOKE -> confirmDialogAction = "revoke"
                            IncidentAction.WITHDRAW -> confirmDialogAction = "withdraw"
                            IncidentAction.DECLINE -> confirmDialogAction = "decline"
                            IncidentAction.RESOLVE -> confirmDialogAction = "resolve"
                            IncidentAction.RELEASE -> onReleaseAssignment()
                            IncidentAction.ACKNOWLEDGE -> onAcknowledge()
                            IncidentAction.ASSIGN -> onAssign()
                            IncidentAction.START -> onStartResponse()
                            else -> Unit
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .widthIn(max = 680.dp)
                        .verticalScroll(scrollState)
                        .padding(horizontal = Spacing.Large, vertical = Spacing.Small),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                IncidentIntroduction(incident, presentation, actionMessage, urgencyColor)

                IncidentLocationGuidance(incident, presentation, isNight, onViewLocation)

                SelectedIncidentHelper(incident, presentation, localSigningKey, formattedReachability, onDirectChat, { confirmDialogAction = "revoke" })

                    IncidentHelperOffers(
                        incident = incident,
                        presentation = presentation,
                        showAllOffers = showAllOffers,
                        onToggleShowAll = { showAllOffers = !showAllOffers },
                        callbacks = HelperOfferCallbacks(
                            onChoose = { offer ->
                                confirmDialogAction = "choose:${offer.offerId}:${offer.helperName}:${offer.note}"
                            },
                            onEdit = { offer ->
                                offerDraftNote = offer.note
                                showOfferSheet = true
                            },
                            onWithdraw = { confirmDialogAction = "withdraw" },
                            onDirectChat = onDirectChat
                        )
                    )

                    IncidentActivity(appliedEvents, showActivitySection, { showActivitySection = !showActivitySection })

                    // In-page secondary actions (Compact & De-emphasized)
                    if (IncidentAction.RESOLVE in presentation.secondary && presentation.primary != IncidentAction.RESOLVE) {
                        CompactSecondaryButton("Mark resolved", enabled = !actionBusy) {
                            confirmDialogAction = "resolve"
                        }
                    }
                    if (IncidentAction.ACKNOWLEDGE in presentation.secondary &&
                        presentation.primary != IncidentAction.ACKNOWLEDGE) {
                        CompactSecondaryButton("I saw this request", enabled = !actionBusy, onClick = onAcknowledge)
                    }
                    if (IncidentAction.RELEASE in presentation.secondary) {
                        CompactSecondaryButton(
                            "Release assignment", enabled = !actionBusy, onClick = onReleaseAssignment
                        )
                    }

                    // Compact destructive action at the end of content
                    if (IncidentAction.CANCEL in presentation.secondary) {
                        CancelIncidentButton(enabled = !actionBusy) { confirmDialogAction = "cancel" }
                    }

                    Spacer(Modifier.height(12.dp))
                }

            IncidentPrimaryActions(incident, presentation, actionBusy, localSigningKey,
                onEditOffer = { note -> offerDraftNote = note; showOfferSheet = true },
                onConfirmAction = { action -> confirmDialogAction = action },
                onReviewHelpers = { coroutineScope.launch { scrollState.animateScrollTo(scrollState.maxValue) } },
                onConfirmLead = onConfirmLead, onAcknowledge = onAcknowledge,
                onAssign = onAssign, onStartResponse = onStartResponse)
        }
    }

        // Offer Help / Edit Offer Sheet
        if (showOfferSheet) {
            TacticalHelpOfferSheet(
                incidentTitle = incident.title,
                incidentType = incident.incidentType,
                initialNote = offerDraftNote,
                isEditing = presentation.myOffer != null,
                actionBusy = actionBusy,
                waitSeconds = offerWaitSeconds,
                onDismiss = { showOfferSheet = false },
                onSubmit = { note ->
                    onOffer(note)
                    showOfferSheet = false
                },
                onWithdraw = if (presentation.myOffer != null && IncidentAction.WITHDRAW in presentation.secondary) {
                    {
                        showOfferSheet = false
                        confirmDialogAction = "withdraw"
                    }
                } else null
            )
        }

        IncidentConfirmationDialogs(confirmDialogAction, onSelectLead, onRevokeLead, onWithdrawOffer, onDeclineLead, onResolve, onCancel, { confirmDialogAction = null })
}

/** Offers listed before "Show all" expands the section. */
private const val COLLAPSED_OFFER_COUNT = 3

private fun reachabilityLabel(leadReachability: String): String = when {
    leadReachability.equals("Local Device", ignoreCase = true) ||
        leadReachability.equals("This phone", ignoreCase = true) -> "This phone"
    leadReachability.equals("Direct", ignoreCase = true) -> "Direct ready connection"
    leadReachability.equals("Relayed", ignoreCase = true) -> "Reachable through another phone"
    leadReachability.equals("Checking", ignoreCase = true) -> "Checking connection"
    else -> "Unavailable on mesh"
}

@Composable
private fun IncidentMoreActionsMenu(
    secondaryActions: List<IncidentAction>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    enabled: Boolean,
    onSelect: (IncidentAction) -> Unit
) {
    if (secondaryActions.isEmpty()) return
    IconButton(onClick = { onExpandedChange(true) }) {
        Icon(Icons.Default.MoreVert, contentDescription = "More actions")
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { onExpandedChange(false) }
    ) {
        secondaryActions.forEach { action ->
            DropdownMenuItem(
                text = { Text(action.label) },
                onClick = {
                    onExpandedChange(false)
                    onSelect(action)
                },
                enabled = enabled
            )
        }
    }
}

/** What a helper offer card can trigger; each is shown only when the presentation allows it. */
private class HelperOfferCallbacks(
    val onChoose: (IncidentOffer) -> Unit,
    val onEdit: (IncidentOffer) -> Unit,
    val onWithdraw: () -> Unit,
    val onDirectChat: (String) -> Unit
)

@Composable
private fun IncidentHelperOffers(
    incident: Incident,
    presentation: IncidentPresentation,
    showAllOffers: Boolean,
    onToggleShowAll: () -> Unit,
    callbacks: HelperOfferCallbacks
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HelperOffersHeading(presentation.helpers.size)
        if (presentation.helpers.isEmpty()) {
            NoOffersYetCard()
        } else {
            val displayedHelpers = if (showAllOffers || presentation.helpers.size <= COLLAPSED_OFFER_COUNT) {
                presentation.helpers
            } else {
                presentation.helpers.take(COLLAPSED_OFFER_COUNT)
            }
            displayedHelpers.forEach { helper ->
                HelperOfferCard(helper, canWithdraw = IncidentAction.WITHDRAW in presentation.secondary, callbacks)
            }
            val mySelectedOfferLocked = presentation.helpers.any {
                it.isMe && !it.canEditOffer && it.offer.offerId == incident.selectionOfferId
            }
            if (mySelectedOfferLocked && !presentation.terminal) {
                Text("This offer is selected. Withdraw before changing your assistance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (presentation.helpers.size > COLLAPSED_OFFER_COUNT) {
                TextButton(
                    onClick = onToggleShowAll,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (showAllOffers) "Show fewer offers" else "Show all ${presentation.helpers.size} offers"
                    )
                }
            }
        }
    }
}

@Composable
private fun HelperOfferCard(helper: HelperPresentation, canWithdraw: Boolean, callbacks: HelperOfferCallbacks) {
    val chatTarget = helperChatTarget(helper.offer)
    TacticalHelperOfferCard(
        helper = helper,
        onChoose = if (helper.canChoose) {
            { callbacks.onChoose(helper.offer) }
        } else null,
        onEdit = if (helper.canEditOffer) {
            { callbacks.onEdit(helper.offer) }
        } else null,
        onWithdraw = if (helper.isMe && canWithdraw) {
            { callbacks.onWithdraw() }
        } else null,
        onDirectChat = if (!helper.isMe && chatTarget != null) {
            { callbacks.onDirectChat(chatTarget) }
        } else null
    )
}

@Composable
private fun HelperOffersHeading(helperCount: Int) {
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
                text = "People offering help ($helperCount)",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.semantics { heading() }
            )
        }
    }
}

@Composable
private fun NoOffersYetCard() {
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
}

/** The de-emphasized full-width outlined button used for in-page secondary actions. */
@Composable
private fun CompactSecondaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@Composable
private fun CancelIncidentButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
            modifier = Modifier
                .defaultMinSize(minHeight = 32.dp)
                .height(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Cancel Incident Report",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

// Offers carry a mesh ID; reporter user IDs and historical names do not.
