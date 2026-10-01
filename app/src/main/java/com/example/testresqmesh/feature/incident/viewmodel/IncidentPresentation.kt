package com.example.testresqmesh.feature.incident.viewmodel

import com.example.testresqmesh.core.model.EventType
import com.example.testresqmesh.data.local.entity.DomainEventEntity
import com.example.testresqmesh.data.local.entity.IncidentEntity
import com.example.testresqmesh.data.local.entity.IncidentOfferEntity
import com.example.testresqmesh.data.repository.IncidentPolicy
import com.example.testresqmesh.data.repository.IncidentOwnership
import org.json.JSONObject

enum class IncidentAction(val label: String) {
    OFFER("Offer help"), EDIT_OFFER("Edit offer"), REVIEW("Review helpers"),
    REVIEW_SELECTED("Review selected helper"), CONFIRM("Confirm I can help"),
    DECLINE("Decline"), WITHDRAW("Withdraw offer"), REVOKE("Remove selection"),
    RESOLVE("Mark resolved"), CANCEL("Cancel request"),
    ACKNOWLEDGE("Acknowledge request"), ASSIGN("Volunteer to respond"),
    START("Start responding"), RELEASE("Release assignment")
}

data class HelperPresentation(val offer: IncidentOfferEntity, val label: String, val isMe: Boolean,
    val canChoose: Boolean, val canEditOffer: Boolean = false)

data class IncidentPresentation(
    val title: String,
    val status: String,
    val isReporter: Boolean,
    val terminal: Boolean,
    val primary: IncidentAction?,
    val secondary: List<IncidentAction>,
    val helpers: List<HelperPresentation>,
    val selectedOffer: IncidentOfferEntity?,
    val warning: String?,
    val myOffer: IncidentOfferEntity?,
    val nextStep: String = ""
)

fun IncidentEntity.displayTitle(): String = when {
    title.isNotBlank() -> title.trim()
    areaDescription.isNotBlank() -> "$incidentType · $areaDescription"
    else -> "$incidentType emergency"
}

fun helperSummary(
    offers: List<IncidentOfferEntity>,
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

fun isInvolved(incident: IncidentEntity, offers: List<IncidentOfferEntity>, userId: String?, key: String?): Boolean {
    val withdrawnSelection = incident.status !in setOf("RESOLVED", "CANCELLED") &&
        offers.any { it.offerId == incident.selectionOfferId && it.withdrawn }
    return IncidentOwnership.isReporter(incident, userId, key) || (userId != null && (
        !withdrawnSelection && incident.primaryResponderId == userId)) ||
        (key != null && (!withdrawnSelection && incident.selectedHelperKey == key ||
            offers.any { it.helperKey == key && !it.withdrawn }))
}

/** Affordances mirror existing authority; repositories still validate every mutation. */
fun incidentPresentation(incident: IncidentEntity, offers: List<IncidentOfferEntity>, userId: String?, key: String?,
    events: List<DomainEventEntity> = emptyList(), identityLoading: Boolean = userId == null,
    identityError: String? = null): IncidentPresentation {
    // Room's incident and offer flows can emit separately. Never render a withdrawn helper
    // as selected while the repaired incident projection is reaching the screen.
    if (incident.workflowVersion == 2 && incident.status !in setOf("RESOLVED", "CANCELLED") &&
        offers.any { it.offerId == incident.selectionOfferId && (it.withdrawn ||
            it.revision > (incident.selectionOfferRevision ?: Long.MAX_VALUE)) }) {
        return incidentPresentation(incident.copy(status = "OPEN", selectionId = null, selectionOfferId = null,
            selectionOfferRevision = null, selectedHelperKey = null, selectionConfirmedAt = null,
            primaryResponderId = null, primaryResponderName = null), offers, userId, key, events, identityLoading, identityError)
    }
    val reporter = IncidentOwnership.isReporter(incident, userId, key)
    val reporterAuthority = reporter
    val identityProblem = when {
        identityLoading -> "Checking your identity…"
        identityError != null -> identityError
        userId == null -> "Your identity is unavailable. Retry or reopen the app."
        incident.workflowVersion == 2 && key == null -> "Your signing identity is unavailable. Retry or reopen the app."
        incident.workflowVersion == 2 && userId == incident.creatorId && !reporter ->
            "This incident belongs to another signing identity. Renaming cannot restore access."
        else -> null
    }
    val terminal = incident.status in setOf("RESOLVED", "CANCELLED")
    val selected = offers.firstOrNull { it.offerId == incident.selectionOfferId }
    val mine = offers.firstOrNull { key != null && it.helperKey == key && !it.withdrawn }
    val selectedMe = key != null && incident.selectedHelperKey == key && incident.selectionId != null
    val validSelection = selected != null && !selected.withdrawn && selected.revision == incident.selectionOfferRevision
    val isMeConfirmed = selectedMe && (incident.status == "RESPONDING" || incident.selectionConfirmedAt != null)
    val revoked = events.filter { it.applied && it.eventType == "INCIDENT_LEAD_REVOKED" }
        .mapNotNull { runCatching { JSONObject(it.payloadJson).optString("selectionId") }.getOrNull() }.toSet()
    val previouslyRevoked = !selectedMe && key != null && events.any {
        it.applied && it.eventType == "INCIDENT_LEAD_SELECTED" && runCatching {
            val payload = JSONObject(it.payloadJson)
            payload.optString("helperKey") == key && payload.optString("selectionId") in revoked
        }.getOrDefault(false)
    }
    val warning = when {
        identityProblem != null -> identityProblem
        terminal -> null
        previouslyRevoked && !terminal -> "You are no longer the selected helper. The reporter removed that selection."
        incident.selectionId == null -> null
        selected == null -> "Selected offer unavailable on this phone yet."
        !validSelection -> "Helper updated offer · Please review updated details."
        else -> null
    }
    val secondary = mutableListOf<IncidentAction>()
    val primary = if (terminal || identityProblem != null) null else if (incident.workflowVersion == 2) {
        when {
            reporterAuthority -> {
                if (incident.selectionId != null) secondary += IncidentAction.REVOKE
                secondary += IncidentAction.CANCEL
                when {
                    incident.status == "RESPONDING" && validSelection -> IncidentAction.RESOLVE
                    incident.selectionId != null -> IncidentAction.REVIEW_SELECTED
                    offers.any { !it.withdrawn } -> IncidentAction.REVIEW
                    else -> null
                }
            }
            reporter || userId == null || key == null -> null
            selectedMe -> {
                if (mine != null) secondary += IncidentAction.WITHDRAW
                if (incident.status == "AWAITING_HELPER" && validSelection) {
                    secondary += IncidentAction.DECLINE
                    IncidentAction.CONFIRM
                } else null
            }
            mine != null -> { secondary += IncidentAction.WITHDRAW; IncidentAction.EDIT_OFFER }
            else -> IncidentAction.OFFER
        }
    } else {
        fun allowed(type: EventType) = userId != null && IncidentPolicy.mayPerform(incident, type.name, userId)
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
        compareByDescending<IncidentOfferEntity> { it.offerId == incident.selectionOfferId }
            .thenByDescending { key != null && it.helperKey == key }
            .thenByDescending { it.updatedAt }.thenBy { it.offerId }
    ).map { offer ->
        val isSelected = offer.offerId == incident.selectionOfferId
        val label = when {
            !isSelected -> "Wants to help"
            terminal -> if (incident.selectionConfirmedAt != null) "Previously confirmed helper" else "Previously selected helper"
            !validSelection -> "Selection needs review"
            incident.status == "RESPONDING" || incident.selectionConfirmedAt != null -> "Confirmed helper"
            else -> "Selected · Waiting for confirmation"
        }
        val isMe = key != null && offer.helperKey == key && userId != null
        HelperPresentation(offer, label, isMe, !terminal && reporterAuthority && identityProblem == null && incident.status == "OPEN",
            canEditOffer = isMe && !terminal && !isSelected && identityProblem == null)
    }

    val nextStep = when {
        identityProblem != null -> identityProblem
        incident.status == "RESOLVED" -> "This incident has been marked resolved on this phone."
        incident.status == "CANCELLED" -> "This incident was cancelled on this phone."
        reporter -> when {
            incident.status == "RESPONDING" -> {
                val name = selected?.helperName ?: incident.primaryResponderName ?: "Helper"
                "$name is confirmed to help. Coordinate directly or mark resolved when assistance is complete."
            }
            incident.status == "AWAITING_HELPER" -> {
                val name = selected?.helperName ?: "the selected helper"
                "Waiting for $name to confirm readiness."
            }
            offers.any { !it.withdrawn } -> "Review helper offers below and choose who to coordinate with."
            else -> "Broadcasting request · Awaiting helper offers."
        }
        isMeConfirmed -> "You are the confirmed helper. Coordinate with the reporter."
        selectedMe && incident.status == "AWAITING_HELPER" -> "You were selected to help · Please confirm availability."
        mine != null -> "Offer sent · Awaiting reporter review."
        else -> "You can offer help if you are nearby and able to assist."
    }

    return IncidentPresentation(
        title = incident.displayTitle(),
        status = when (incident.status) {
            "OPEN" -> "Looking for help"
            "AWAITING_HELPER" -> "Awaiting confirmation"
            "RESPONDING" -> if (incident.workflowVersion == 2) "Helper confirmed" else "Responding"
            "ACKNOWLEDGED" -> "Request acknowledged"
            "ASSIGNED" -> "Responder assigned"
            "RESOLVED" -> "Resolved"
            "CANCELLED" -> "Cancelled"
            else -> "Status unavailable"
        },
        isReporter = reporter,
        terminal = terminal,
        primary = primary,
        secondary = secondary,
        helpers = helpers,
        selectedOffer = selected?.takeUnless { it.withdrawn },
        warning = warning,
        myOffer = mine,
        nextStep = nextStep
    )
}
