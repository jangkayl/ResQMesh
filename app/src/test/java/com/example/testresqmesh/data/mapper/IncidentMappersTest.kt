package com.example.testresqmesh.data.mapper

import com.example.testresqmesh.data.local.entity.DomainEventEntity
import com.example.testresqmesh.data.local.entity.IncidentEntity
import com.example.testresqmesh.data.local.entity.IncidentOfferEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class IncidentMappersTest {
    @Test fun incidentRoundTripsEveryField() {
        val entity = IncidentEntity(
            incidentId = "inc-1", creatorId = "creator-1", creatorName = "Creator",
            incidentType = "MEDICAL", severity = "HIGH", description = "desc", areaDescription = "area",
            latitude = 14.5, longitude = 121.25, locationCapturedAt = 1_700_000_001L,
            locationAccuracyMeters = 7.5f, status = "RESPONDING", primaryResponderId = "resp-1",
            primaryResponderName = "Responder", version = 9L, createdAt = 1_700_000_002L,
            updatedAt = 1_700_000_003L, workflowVersion = 3, reporterSigningKey = "rkey",
            selectionId = "sel-1", selectionOfferId = "offer-1", selectionOfferRevision = 4L,
            selectedHelperKey = "hkey", selectionConfirmedAt = 1_700_000_004L, title = "Title"
        )
        val model = entity.toModel()
        assertEquals("RESPONDING", model.status)
        assertEquals(3, model.workflowVersion)
        assertEquals("Title", model.title)
        assertEquals(entity, model.toEntity())
    }

    @Test fun incidentOfferRoundTripsEveryField() {
        val entity = IncidentOfferEntity(
            offerId = "offer-1", incidentId = "inc-1", helperKey = "hkey", helperNodeId = "node-1",
            helperName = "Helper", note = "note", revision = 5L, withdrawn = true, updatedAt = 1_700_000_005L
        )
        assertEquals(entity, entity.toModel().toEntity())
    }

    @Test fun domainEventRoundTripsEveryField() {
        val entity = DomainEventEntity(
            eventId = "ev-1", entityId = "inc-1", entityType = "INCIDENT", eventType = "INCIDENT_CREATED",
            actorId = "actor-1", actorName = "Actor", logicalVersion = 6L, timestamp = 1_700_000_006L,
            payloadJson = "{\"k\":1}", signature = "sig", applied = false, validationStatus = "REJECTED"
        )
        val model = entity.toModel()
        assertEquals("REJECTED", model.validationStatus)
        assertEquals(false, model.applied)
        assertEquals(entity, model.toEntity())
    }
}
