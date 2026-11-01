package edu.cit.resqmesh.data.mapper

import edu.cit.resqmesh.data.local.entity.DomainEventEntity
import edu.cit.resqmesh.data.local.entity.IncidentEntity
import edu.cit.resqmesh.data.local.entity.IncidentOfferEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class IncidentDisplayModelTest {
    @Test
    fun displayModelCleansTextButEntityStaysRaw() {
        val entity = IncidentEntity(
            incidentId = "i", creatorId = "c", creatorName = "Ann‮#AB12", incidentType = "FIRE", severity = "HIGH",
            description = "d​e", areaDescription = "a‮b", status = "OPEN", primaryResponderId = null,
            primaryResponderName = null, version = 1, createdAt = 1, updatedAt = 1, title = "t​x"
        )
        val copy = entity.copy()
        val model = entity.toDisplayModel()
        assertEquals("Ann#AB12", model.creatorName)
        assertEquals("de", model.description)
        assertEquals("ab", model.areaDescription)
        assertEquals("tx", model.title)
        assertEquals(copy, entity)
        assertEquals("t​x", entity.title)
    }

    @Test
    fun eventDisplayModelCleansActorButKeepsSignedFieldsRaw() {
        val payload = "{\"helperName\":\"a‮b\"}"
        val entity = DomainEventEntity("e", "i", "INCIDENT", "INCIDENT_CREATED",
            "a", "Ann‮#AB12", 1, 1, payload, "sig", true)
        val model = entity.toDisplayModel()
        assertEquals("Ann#AB12", model.actorName)
        assertEquals(payload, model.payloadJson)
        assertEquals("Ann‮#AB12", entity.actorName)
    }

    @Test
    fun offerDisplayModelCleansTextButEntityStaysRaw() {
        val entity = IncidentOfferEntity("o", "i", "k", "N1", "Bob​#CD34", "n‮ote", 1, false, 1)
        val copy = entity.copy()
        val model = entity.toDisplayModel()
        assertEquals("Bob#CD34", model.helperName)
        assertEquals("note", model.note)
        assertEquals(copy, entity)
    }
}
