package edu.cit.resqmesh.feature.incident.alert

import edu.cit.resqmesh.core.domain.incident.IncidentNotificationContext
import edu.cit.resqmesh.core.domain.incident.IncidentNotificationDecision
import edu.cit.resqmesh.core.domain.incident.IncidentNotificationRules
import edu.cit.resqmesh.core.domain.incident.InvolvedKind
import edu.cit.resqmesh.core.model.Incident
import edu.cit.resqmesh.core.notification.IncidentEventNotice
import edu.cit.resqmesh.core.notification.IncidentNotifier
import edu.cit.resqmesh.core.notification.NotificationHelper
import edu.cit.resqmesh.core.notification.NotificationPreferences

/** Turns rule decisions into one replaceable notification per incident. */
class IncidentAlertController(
    private val notifications: NotificationHelper,
    private val preferences: NotificationPreferences,
    private val now: () -> Long = System::currentTimeMillis
) : IncidentNotifier {
    override suspend fun applied(event: IncidentEventNotice, incident: Incident) {
        val context = IncidentNotificationContext(
            userId = event.local.userId,
            deviceId = event.local.deviceId,
            signingKey = event.local.signingKey,
            alertsEnabled = preferences.incidentAlertsEnabled.value,
            now = now()
        )
        val decision = IncidentNotificationRules.decide(
            event.eventType, event.actorId, event.revision, incident, context
        )
        val label = incident.title.ifBlank { incident.incidentType }
        val helper = event.actorName.ifBlank { "A helper" }
        when (decision) {
            IncidentNotificationDecision.None -> Unit
            IncidentNotificationDecision.NewIncident -> notifications.showIncidentNotification(
                incident.incidentId,
                "${incident.severity.trim()} incident: $label",
                listOf(incident.incidentType, incident.areaDescription).filter { it.isNotBlank() }.joinToString(" - ")
            )
            is IncidentNotificationDecision.Involved -> {
                val text = when (decision.kind) {
                    InvolvedKind.OFFER_RECEIVED -> "$helper offered help"
                    InvolvedKind.SELECTED_AS_LEAD -> "You were selected as lead"
                    InvolvedKind.LEAD_CONFIRMED -> "$helper confirmed as lead"
                    InvolvedKind.LEAD_DECLINED -> "$helper declined to lead"
                    InvolvedKind.CLOSED -> "Incident ${incident.status.lowercase()}"
                }
                notifications.showIncidentNotification(
                    incident.incidentId, label, text, quietUpdate = decision.kind == InvolvedKind.CLOSED
                )
            }
        }
    }
}
