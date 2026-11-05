package edu.cit.resqmesh.feature.incident.model

import edu.cit.resqmesh.core.model.EventType
import edu.cit.resqmesh.core.model.DomainEvent
import edu.cit.resqmesh.core.model.Incident
import edu.cit.resqmesh.core.model.IncidentOffer
import edu.cit.resqmesh.core.model.IncidentState
import edu.cit.resqmesh.core.domain.incident.IncidentRules
import org.json.JSONObject

fun Incident.isReporter(userId: String?, signingKey: String?): Boolean =
    IncidentRules.isReporter(creatorId, workflowVersion, reporterSigningKey, userId, signingKey)

enum class IncidentAction(val label: String) {
    OFFER("Offer help"), EDIT_OFFER("Edit offer"), REVIEW("Review helpers"),
    REVIEW_SELECTED("Review selected helper"), CONFIRM("Confirm I can help"),
    DECLINE("Decline"), WITHDRAW("Withdraw offer"), REVOKE("Remove selection"),
    RESOLVE("Mark resolved"), CANCEL("Cancel request"),
    ACKNOWLEDGE("I saw this request"), ASSIGN("Volunteer to respond"),
    START("Start responding"), RELEASE("Release assignment")
}

data class HelperPresentation(val offer: IncidentOffer, val label: String, val isMe: Boolean,
    val canChoose: Boolean, val canEditOffer: Boolean = false)

data class IncidentPresentation(
    val title: String,
    val status: String,
    val isReporter: Boolean,
    val terminal: Boolean,
    val primary: IncidentAction?,
    val secondary: List<IncidentAction>,
    val helpers: List<HelperPresentation>,
    val selectedOffer: IncidentOffer?,
    val warning: String?,
    val myOffer: IncidentOffer?,
    val nextStep: String = ""
)

fun Incident.displayTitle(): String = when {
    title.isNotBlank() -> title.trim()
    areaDescription.isNotBlank() -> "$incidentType · $areaDescription"
    else -> "$incidentType emergency"
}

fun helperSummary(
    offers: List<IncidentOffer>,
    confirmedHelperName: String?,
    hasMyOffer: Boolean
): String {
    val active = offers.filter { !it.withdrawn }
    val base = when {
        confirmedHelperName != null -> "$confirmedHelperName confirmed"
        active.isEmpty() -> "No offers yet"
        active.size == 1 -> "1 person offered help"
        else -> "${active.size} people offered help"
    }
    return if (hasMyOffer && confirmedHelperName == null) "$base · You offered help" else base
}

fun isInvolved(incident: Incident, offers: List<IncidentOffer>, userId: String?, key: String?): Boolean {
    val withdrawnSelection = !incident.isTerminal() &&
        offers.any { it.offerId == incident.selectionOfferId && it.withdrawn }
    return incident.isReporter(userId, key) || (userId != null && (
        !withdrawnSelection && incident.primaryResponderId == userId)) ||
        (key != null && (!withdrawnSelection && incident.selectedHelperKey == key ||
            offers.any { it.helperKey == key && !it.withdrawn }))
}

/**
 * Room's incident and offer flows can emit separately. True when the selected offer was
 * withdrawn or revised past the selection while the repaired projection is still arriving.
 */
private fun hasStaleSelection(incident: Incident, offers: List<IncidentOffer>): Boolean =
    incident.usesHelpWorkflow() && !incident.isTerminal() &&
        offers.any { it.offerId == incident.selectionOfferId && (it.withdrawn ||
            it.revision > (incident.selectionOfferRevision ?: Long.MAX_VALUE)) }

private fun Incident.withoutSelection(): Incident = copy(status = IncidentState.OPEN.name, selectionId = null,
    selectionOfferId = null, selectionOfferRevision = null, selectedHelperKey = null, selectionConfirmedAt = null,
    primaryResponderId = null, primaryResponderName = null)

private fun Incident.statusLabel(): String = when (status) {
    IncidentState.OPEN.name -> "Looking for help"
    IncidentState.AWAITING_HELPER.name -> "Awaiting confirmation"
    IncidentState.RESPONDING.name -> if (usesHelpWorkflow()) "Helper confirmed" else "Responding"
    IncidentState.ACKNOWLEDGED.name -> "Request acknowledged"
    IncidentState.ASSIGNED.name -> "Responder assigned"
    IncidentState.RESOLVED.name -> "Resolved"
    IncidentState.CANCELLED.name -> "Cancelled"
    else -> "Status unavailable"
}

/** Affordances mirror existing authority; repositories still validate every mutation. */
fun incidentPresentation(incident: Incident, offers: List<IncidentOffer>, userId: String?, key: String?,
    events: List<DomainEvent> = emptyList(), identityLoading: Boolean = userId == null,
    identityError: String? = null): IncidentPresentation {
    // Never render a withdrawn helper as selected while the repaired projection is reaching the screen.
    if (hasStaleSelection(incident, offers)) {
        return incidentPresentation(
            incident.withoutSelection(), offers, userId, key, events, identityLoading, identityError
        )
    }
    val isLocalReporter = incident.isReporter(userId, key)
    val usesHelpWorkflow = incident.usesHelpWorkflow()
    val identityProblem = when {
        identityLoading -> "Checking your identity…"
        identityError != null -> identityError
        userId == null -> "Your identity is unavailable. Retry or reopen the app."
        usesHelpWorkflow && key == null -> "Your signing identity is unavailable. Retry or reopen the app."
        usesHelpWorkflow && userId == incident.creatorId && !isLocalReporter ->
            "This incident belongs to another signing identity. Renaming cannot restore access."
        else -> null
    }
    val terminal = incident.isTerminal()
    val isResponding = incident.hasStatus(IncidentState.RESPONDING)
    val isAwaitingHelper = incident.hasStatus(IncidentState.AWAITING_HELPER)
    val selectedOffer = offers.firstOrNull { it.offerId == incident.selectionOfferId }
    val myOffer = offers.firstOrNull { key != null && it.helperKey == key && !it.withdrawn }
    val isSelectedHelper = key != null && incident.selectedHelperKey == key && incident.selectionId != null
    val validSelection = selectedOffer != null && !selectedOffer.withdrawn &&
        selectedOffer.revision == incident.selectionOfferRevision
    val isMeConfirmed = isSelectedHelper && (isResponding || incident.selectionConfirmedAt != null)
    val revokedSelectionIds = events.filter { it.applied && it.eventType == EventType.INCIDENT_LEAD_REVOKED.name }
        .mapNotNull { runCatching { JSONObject(it.payloadJson).optString("selectionId") }.getOrNull() }.toSet()
    val previouslyRevoked = !isSelectedHelper && key != null && events.any {
        it.applied && it.eventType == EventType.INCIDENT_LEAD_SELECTED.name && runCatching {
            val payload = JSONObject(it.payloadJson)
            payload.optString("helperKey") == key && payload.optString("selectionId") in revokedSelectionIds
        }.getOrDefault(false)
    }
    val warning = when {
        identityProblem != null -> identityProblem
        terminal -> null
        previouslyRevoked -> "You are no longer the selected helper. The reporter removed that selection."
        incident.selectionId == null -> null
        selectedOffer == null -> "Selected offer unavailable on this phone yet."
        !validSelection -> "Helper updated offer · Please review updated details."
        else -> null
    }
    val secondary = mutableListOf<IncidentAction>()
    val primary = if (terminal || identityProblem != null) null else if (usesHelpWorkflow) {
        when {
            isLocalReporter -> {
                if (incident.selectionId != null) secondary += IncidentAction.REVOKE
                secondary += IncidentAction.CANCEL
                when {
                    isResponding && validSelection -> IncidentAction.RESOLVE
                    incident.selectionId != null -> IncidentAction.REVIEW_SELECTED
                    offers.any { !it.withdrawn } -> IncidentAction.REVIEW
                    else -> null
                }
            }
            userId == null || key == null -> null
            isSelectedHelper -> {
                if (myOffer != null) secondary += IncidentAction.WITHDRAW
                if (isAwaitingHelper && validSelection) {
                    secondary += IncidentAction.DECLINE
                    IncidentAction.CONFIRM
                } else null
            }
            myOffer != null -> { secondary += IncidentAction.WITHDRAW; IncidentAction.EDIT_OFFER }
            else -> IncidentAction.OFFER
        }
    } else {
        fun allowed(type: EventType) = userId != null &&
            IncidentRules.mayPerform(
                incident.status, incident.creatorId, incident.primaryResponderId, type.name, userId
            )
        if (allowed(EventType.INCIDENT_CANCELLED)) secondary += IncidentAction.CANCEL
        if (allowed(EventType.INCIDENT_ASSIGNMENT_RELEASED)) secondary += IncidentAction.RELEASE
        if (allowed(EventType.INCIDENT_ACKNOWLEDGED)) secondary += IncidentAction.ACKNOWLEDGE
        when {
            allowed(EventType.INCIDENT_RESPONSE_STARTED) -> { secondary += IncidentAction.RESOLVE; IncidentAction.START }
            allowed(EventType.INCIDENT_RESOLVED) -> IncidentAction.RESOLVE
            allowed(EventType.INCIDENT_ASSIGNED) -> IncidentAction.ASSIGN
            else -> null
        }
    }
    val helpers = offers.filter { !it.withdrawn }.sortedWith(
        compareByDescending<IncidentOffer> { it.offerId == incident.selectionOfferId }
            .thenByDescending { key != null && it.helperKey == key }
            .thenByDescending { it.updatedAt }.thenBy { it.offerId }
    ).map { offer ->
        val isSelected = offer.offerId == incident.selectionOfferId
        val label = when {
            !isSelected -> "Wants to help"
            terminal -> if (incident.selectionConfirmedAt != null) "Previously confirmed helper" else "Previously selected helper"
            !validSelection -> "Selection needs review"
            isResponding || incident.selectionConfirmedAt != null -> "Confirmed helper"
            else -> "Selected · Waiting for confirmation"
        }
        val isMe = key != null && offer.helperKey == key && userId != null
        val canChoose = !terminal && isLocalReporter && identityProblem == null &&
            incident.hasStatus(IncidentState.OPEN)
        HelperPresentation(offer, label, isMe, canChoose,
            canEditOffer = isMe && !terminal && !isSelected && identityProblem == null)
    }

    val nextStep = when {
        identityProblem != null -> identityProblem
        incident.hasStatus(IncidentState.RESOLVED) -> "This incident has been marked resolved on this phone."
        incident.hasStatus(IncidentState.CANCELLED) -> "This incident was cancelled on this phone."
        isLocalReporter -> when {
            isResponding -> {
                val name = selectedOffer?.helperName ?: incident.primaryResponderName ?: "Helper"
                "$name is confirmed to help. Coordinate directly or mark resolved when assistance is complete."
            }
            isAwaitingHelper -> {
                val name = selectedOffer?.helperName ?: "the selected helper"
                "Waiting for $name to confirm readiness."
            }
            offers.any { !it.withdrawn } -> "Review helper offers below and choose who to coordinate with."
            else -> "Broadcasting request · Awaiting helper offers."
        }
        isMeConfirmed -> "You are the confirmed helper. Coordinate with the reporter."
        isSelectedHelper && isAwaitingHelper -> "You were selected to help · Please confirm availability."
        myOffer != null -> "Offer sent · Awaiting reporter review."
        else -> "You can offer help if you are nearby and able to assist."
    }

    return IncidentPresentation(
        title = incident.displayTitle(),
        status = incident.statusLabel(),
        isReporter = isLocalReporter,
        terminal = terminal,
        primary = primary,
        secondary = secondary,
        helpers = helpers,
        selectedOffer = selectedOffer?.takeUnless { it.withdrawn },
        warning = warning,
        myOffer = myOffer,
        nextStep = nextStep
    )
}
