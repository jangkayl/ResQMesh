package edu.cit.resqmesh.data.repository

import edu.cit.resqmesh.data.local.entity.DomainEventEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class IncidentSyncScopeTest {
    private val now = 1_800_000_000_000L
    private fun request(since: Long, cap: Int = IncidentSyncScope.MAX_INCIDENTS) =
        IncidentSyncScope.from(JSONObject().put("since", since).put("cap", cap), now)

    @Test fun standardScopeWithinClockSkewIsAccepted() {
        val ours = IncidentSyncScope.current(now)
        assertEquals(ours, request(ours.since))
        assertNotNull(request(ours.since + 11 * HOUR))
    }

    @Test fun nonStandardScopesThatWouldDefeatTheCacheAreRefused() {
        val ours = IncidentSyncScope.current(now)
        assertNull(request(ours.since + 1))              // not minute-aligned
        assertNull(request(ours.since - 13 * HOUR))       // far outside our window
        assertNull(request(ours.since, cap = 10))         // non-standard cap
        assertNull(IncidentSyncScope.from(JSONObject(), now))
    }

    @Test fun orphanEventsNeverBlockCompletion() {
        val orphan = DomainEventEntity("e1", "unknown-incident", "INCIDENT", "INCIDENT_OFFER_UPDATED",
            "B", "B", 1, now, "{}", applied = false, validationStatus = "ACCEPTED")
        assertEquals(0, IncidentSyncSnapshot(listOf(orphan), emptyList(), emptyList()).pending)
    }

    private companion object { const val HOUR = 60L * 60 * 1000 }
}
