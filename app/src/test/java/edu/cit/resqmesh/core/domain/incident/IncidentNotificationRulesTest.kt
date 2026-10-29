package edu.cit.resqmesh.core.domain.incident

import edu.cit.resqmesh.core.model.EventType
import edu.cit.resqmesh.core.model.Incident
import org.junit.Assert.assertEquals
import org.junit.Test

class IncidentNotificationRulesTest {
    private val now = 10_000_000L
    private val context = IncidentNotificationContext(
        userId = "USR-ME", deviceId = "DEV-ME", signingKey = "key-me", alertsEnabled = true, now = now
    )

    private fun incident(
        severity: String = "Critical",
        creatorId: String = "USR-OTHER",
        reporterKey: String? = "key-other",
        selectedHelperKey: String? = null,
        createdAt: Long = now - 60_000L
    ) = Incident(
        incidentId = "inc-1", creatorId = creatorId, creatorName = "Reporter", incidentType = "Fire",
        severity = severity, description = "", areaDescription = "Pier", latitude = null, longitude = null,
        locationCapturedAt = null, locationAccuracyMeters = null, status = "OPEN", primaryResponderId = null,
        primaryResponderName = null, version = 1, createdAt = createdAt, updatedAt = createdAt,
        workflowVersion = 2, reporterSigningKey = reporterKey, selectionId = null, selectionOfferId = null,
        selectionOfferRevision = null, selectedHelperKey = selectedHelperKey, selectionConfirmedAt = null,
        title = "Warehouse fire"
    )

    private fun decide(
        type: EventType,
        incident: Incident,
        actor: String = "USR-OTHER",
        revision: Long = 1L,
        ctx: IncidentNotificationContext = context
    ) = IncidentNotificationRules.decide(type.name, actor, revision, incident, ctx)

    private val none = IncidentNotificationDecision.None
    private fun involved(kind: InvolvedKind) = IncidentNotificationDecision.Involved(kind)

    @Test fun criticalAndSeriousNotifyEveryone() {
        assertEquals(IncidentNotificationDecision.NewIncident, decide(EventType.INCIDENT_CREATED, incident("Critical")))
        val serious = incident(" serious ")
        assertEquals(IncidentNotificationDecision.NewIncident, decide(EventType.INCIDENT_CREATED, serious))
    }

    @Test fun moderateAndUnknownSeverityDoNotNotify() {
        assertEquals(none, decide(EventType.INCIDENT_CREATED, incident("Moderate")))
        assertEquals(none, decide(EventType.INCIDENT_CREATED, incident("")))
        assertEquals(none, decide(EventType.INCIDENT_CREATED, incident("High")))
    }

    @Test fun ownEventNeverNotifies() {
        assertEquals(none, decide(EventType.INCIDENT_CREATED, incident(), actor = "USR-ME"))
        assertEquals(none, decide(EventType.INCIDENT_CREATED, incident(), actor = "DEV-ME"))
        val mine = incident(creatorId = "USR-ME", reporterKey = "key-me")
        assertEquals(none, decide(EventType.INCIDENT_CREATED, mine))
    }

    @Test fun staleIncidentDoesNotNotify() {
        val stale = incident(createdAt = now - IncidentNotificationRules.NEW_INCIDENT_MAX_AGE_MS - 1)
        assertEquals(none, decide(EventType.INCIDENT_CREATED, stale))
        val edge = incident(createdAt = now - IncidentNotificationRules.NEW_INCIDENT_MAX_AGE_MS)
        assertEquals(IncidentNotificationDecision.NewIncident, decide(EventType.INCIDENT_CREATED, edge))
    }

    @Test fun toggleOffSuppressesBroadcastOnly() {
        val off = context.copy(alertsEnabled = false)
        assertEquals(none, decide(EventType.INCIDENT_CREATED, incident(), ctx = off))
        val mine = incident(creatorId = "USR-ME", reporterKey = "key-me")
        assertEquals(
            involved(InvolvedKind.OFFER_RECEIVED),
            decide(EventType.INCIDENT_OFFER_UPDATED, mine, ctx = off)
        )
    }

    @Test fun reporterIsToldAboutOffersAndDecisions() {
        val mine = incident(creatorId = "USR-ME", reporterKey = "key-me")
        assertEquals(involved(InvolvedKind.OFFER_RECEIVED), decide(EventType.INCIDENT_OFFER_UPDATED, mine))
        assertEquals(involved(InvolvedKind.LEAD_CONFIRMED), decide(EventType.INCIDENT_LEAD_CONFIRMED, mine))
        assertEquals(involved(InvolvedKind.LEAD_DECLINED), decide(EventType.INCIDENT_LEAD_DECLINED, mine))
    }

    @Test fun revisedOfferDoesNotRenotify() {
        val mine = incident(creatorId = "USR-ME", reporterKey = "key-me")
        assertEquals(none, decide(EventType.INCIDENT_OFFER_UPDATED, mine, revision = 2L))
    }

    @Test fun nonReporterGetsNoOfferOrDecisionAlerts() {
        val other = incident()
        assertEquals(none, decide(EventType.INCIDENT_OFFER_UPDATED, other))
        assertEquals(none, decide(EventType.INCIDENT_LEAD_CONFIRMED, other))
    }

    @Test fun selectedHelperIsToldOnlyWhenSelectedByKey() {
        assertEquals(
            involved(InvolvedKind.SELECTED_AS_LEAD),
            decide(EventType.INCIDENT_LEAD_SELECTED, incident(selectedHelperKey = "key-me"))
        )
        assertEquals(none, decide(EventType.INCIDENT_LEAD_SELECTED, incident(selectedHelperKey = "key-x")))
        assertEquals(none, decide(EventType.INCIDENT_LEAD_SELECTED, incident(selectedHelperKey = null)))
    }

    @Test fun resolvedAndCancelledReachReporterAndLeadOnly() {
        val mine = incident(creatorId = "USR-ME", reporterKey = "key-me")
        val led = incident(selectedHelperKey = "key-me")
        assertEquals(involved(InvolvedKind.CLOSED), decide(EventType.INCIDENT_RESOLVED, mine, actor = "USR-X"))
        assertEquals(involved(InvolvedKind.CLOSED), decide(EventType.INCIDENT_CANCELLED, led))
        assertEquals(none, decide(EventType.INCIDENT_RESOLVED, incident()))
    }

    @Test fun staleAgeDoesNotBlockInvolvedUpdates() {
        val old = incident(creatorId = "USR-ME", reporterKey = "key-me", createdAt = 0L)
        assertEquals(involved(InvolvedKind.LEAD_CONFIRMED), decide(EventType.INCIDENT_LEAD_CONFIRMED, old))
    }

    @Test fun unrelatedEventTypesAreIgnored() {
        assertEquals(none, decide(EventType.INCIDENT_ACKNOWLEDGED, incident()))
    }
}
