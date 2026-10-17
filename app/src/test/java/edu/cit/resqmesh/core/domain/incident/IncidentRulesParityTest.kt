package edu.cit.resqmesh.core.domain.incident

import edu.cit.resqmesh.core.model.EventType
import edu.cit.resqmesh.core.model.IncidentState
import edu.cit.resqmesh.data.local.entity.IncidentEntity
import edu.cit.resqmesh.data.repository.IncidentOwnership
import edu.cit.resqmesh.data.repository.IncidentPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentRulesParityTest {
    private val reporter = "reporter"
    private val helper = "helper"
    private val other = "other"
    private val stranger = "stranger"

    private fun incident(status: String, responder: String? = helper, version: Int = 1, key: String? = null) =
        IncidentEntity(
            incidentId = "i1", creatorId = reporter, creatorName = "R", incidentType = "T",
            severity = "HIGH", description = "d", areaDescription = "a", status = status,
            primaryResponderId = responder, primaryResponderName = null, version = 1,
            createdAt = 0, updatedAt = 0, workflowVersion = version, reporterSigningKey = key
        )

    private fun assertParity(e: IncidentEntity?, event: String, actor: String) {
        assertEquals(
            "${e?.status}/${e?.primaryResponderId}/$event/$actor",
            IncidentPolicy.mayPerform(e, event, actor),
            IncidentRules.mayPerform(e?.status, e?.creatorId, e?.primaryResponderId, event, actor)
        )
    }

    @Test
    fun mayPerformMatchesPolicyForAllStatesEventsAndActors() {
        val statuses = IncidentState.values().map { it.name } + "UNKNOWN"
        val events = EventType.values().map { it.name } + "NOT_AN_EVENT"
        val actors = listOf(reporter, helper, other, stranger, "")
        val entities = listOf<String?>(helper, null).flatMap { r -> statuses.map { incident(it, r) } }
        val cases = (entities + listOf(null)).flatMap { e -> events.flatMap { ev -> actors.map { Triple(e, ev, it) } } }
        cases.forEach { (e, ev, a) -> assertParity(e, ev, a) }
        assertTrue(cases.size > 500)
    }

    @Test
    fun nextStateMatchesPolicy() {
        (EventType.values().map { it.name } + "NOT_AN_EVENT").forEach {
            assertEquals(IncidentPolicy.nextState(it), IncidentRules.nextState(it))
        }
    }

    @Test
    fun isReporterMatchesOwnershipAcrossVersionsAndKeys() {
        val stored = listOf<String?>(null, "", "k1")
        val users = listOf<String?>(null, reporter, stranger)
        val keys = listOf<String?>(null, "", "  ", "k1", "k2")
        val entities = listOf(1, 2, 3).flatMap { v -> stored.map { incident("OPEN", version = v, key = it) } }
        entities.flatMap { e -> users.flatMap { u -> keys.map { Triple(e, u, it) } } }.forEach { (e, user, key) ->
            assertEquals(
                "${e.workflowVersion}/${e.reporterSigningKey}/$user/$key",
                IncidentOwnership.isReporter(e, user, key),
                IncidentRules.isReporter(e.creatorId, e.workflowVersion, e.reporterSigningKey, user, key)
            )
        }
    }

    @Test
    fun hardCodedRows() {
        assertTrue(IncidentRules.mayPerform(null, null, null, "INCIDENT_CREATED", reporter))
        assertFalse(IncidentRules.mayPerform("OPEN", reporter, null, "INCIDENT_CREATED", reporter))
        assertTrue(IncidentRules.mayPerform("OPEN", reporter, null, "INCIDENT_ACKNOWLEDGED", helper))
        assertFalse(IncidentRules.mayPerform("OPEN", reporter, null, "INCIDENT_ACKNOWLEDGED", reporter))
        assertTrue(IncidentRules.mayPerform("ACKNOWLEDGED", reporter, null, "INCIDENT_ASSIGNED", helper))
        assertTrue(IncidentRules.mayPerform("ASSIGNED", reporter, helper, "INCIDENT_RESPONSE_STARTED", helper))
        assertFalse(IncidentRules.mayPerform("ASSIGNED", reporter, helper, "INCIDENT_RESPONSE_STARTED", other))
        assertTrue(IncidentRules.mayPerform("RESPONDING", reporter, helper, "INCIDENT_RESOLVED", helper))
        assertTrue(IncidentRules.mayPerform("RESPONDING", reporter, helper, "INCIDENT_CANCELLED", reporter))
        assertFalse(IncidentRules.mayPerform("RESOLVED", reporter, helper, "INCIDENT_CANCELLED", reporter))
        assertTrue(IncidentRules.mayPerform("ASSIGNED", reporter, helper, "INCIDENT_ASSIGNMENT_RELEASED", helper))
        assertFalse(IncidentRules.mayPerform("OPEN", reporter, null, "INCIDENT_OFFER_UPDATED", reporter))
        assertEquals("ACKNOWLEDGED", IncidentRules.nextState("INCIDENT_ASSIGNMENT_RELEASED"))
        assertNull(IncidentRules.nextState("INCIDENT_OFFER_UPDATED"))
        assertTrue(IncidentRules.isReporter(reporter, 2, "k", reporter, "k"))
        assertFalse(IncidentRules.isReporter(reporter, 2, "k", reporter, " "))
        assertTrue(IncidentRules.isReporter(reporter, 1, null, reporter, null))
    }
}
