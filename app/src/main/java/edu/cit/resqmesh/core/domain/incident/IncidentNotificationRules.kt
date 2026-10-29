package edu.cit.resqmesh.core.domain.incident

import edu.cit.resqmesh.core.model.EventType
import edu.cit.resqmesh.core.model.Incident

/** Local identity and settings the rules need; [now] is injected so age gates are testable. */
data class IncidentNotificationContext(
    val userId: String?,
    val deviceId: String?,
    val signingKey: String?,
    val alertsEnabled: Boolean,
    val now: Long
)

enum class InvolvedKind { OFFER_RECEIVED, SELECTED_AS_LEAD, LEAD_CONFIRMED, LEAD_DECLINED, CLOSED }

sealed interface IncidentNotificationDecision {
    data object None : IncidentNotificationDecision
    data object NewIncident : IncidentNotificationDecision
    data class Involved(val kind: InvolvedKind) : IncidentNotificationDecision
}

/**
 * Pure decision of whether an applied incident event should raise a notification. New-incident
 * broadcasts are gated by severity, age and the user toggle; events on incidents the user reported
 * or leads always notify. The user's own actions never notify.
 */
object IncidentNotificationRules {
    const val NEW_INCIDENT_MAX_AGE_MS = 30 * 60 * 1000L
    private val BROADCAST_SEVERITIES = setOf("critical", "serious")

    fun decide(
        eventType: String,
        actorId: String,
        revision: Long,
        incident: Incident,
        context: IncidentNotificationContext
    ): IncidentNotificationDecision {
        if (isOwnAction(actorId, context)) return IncidentNotificationDecision.None
        val reporter = IncidentRules.isReporter(
            incident.creatorId, incident.workflowVersion, incident.reporterSigningKey,
            context.userId, context.signingKey
        )
        val lead = !context.signingKey.isNullOrBlank() && incident.selectedHelperKey == context.signingKey
        return if (eventType == EventType.INCIDENT_CREATED.name) {
            newIncident(incident, reporter, context)
        } else {
            involvedDecision(eventType, revision, reporter, lead)
        }
    }

    private fun involvedDecision(
        eventType: String,
        revision: Long,
        reporter: Boolean,
        lead: Boolean
    ): IncidentNotificationDecision {
        val kind = when (eventType) {
            EventType.INCIDENT_OFFER_UPDATED.name -> InvolvedKind.OFFER_RECEIVED.takeIf { reporter && revision <= 1L }
            EventType.INCIDENT_LEAD_SELECTED.name -> InvolvedKind.SELECTED_AS_LEAD.takeIf { lead }
            EventType.INCIDENT_LEAD_CONFIRMED.name -> InvolvedKind.LEAD_CONFIRMED.takeIf { reporter }
            EventType.INCIDENT_LEAD_DECLINED.name -> InvolvedKind.LEAD_DECLINED.takeIf { reporter }
            EventType.INCIDENT_RESOLVED.name, EventType.INCIDENT_CANCELLED.name ->
                InvolvedKind.CLOSED.takeIf { reporter || lead }
            else -> null
        }
        return kind?.let { IncidentNotificationDecision.Involved(it) } ?: IncidentNotificationDecision.None
    }

    private fun isOwnAction(actorId: String, context: IncidentNotificationContext): Boolean =
        actorId.isNotBlank() && (actorId == context.userId || actorId == context.deviceId)

    private fun newIncident(
        incident: Incident,
        reporter: Boolean,
        context: IncidentNotificationContext
    ): IncidentNotificationDecision {
        val worthy = context.alertsEnabled && !reporter &&
            incident.severity.trim().lowercase() in BROADCAST_SEVERITIES &&
            context.now - incident.createdAt <= NEW_INCIDENT_MAX_AGE_MS
        return if (worthy) IncidentNotificationDecision.NewIncident else IncidentNotificationDecision.None
    }
}
