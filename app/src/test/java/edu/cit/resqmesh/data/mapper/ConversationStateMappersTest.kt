package edu.cit.resqmesh.data.mapper

import edu.cit.resqmesh.core.model.toChatMessage
import edu.cit.resqmesh.data.local.entity.ConversationStateEntity
import edu.cit.resqmesh.data.local.entity.SosAlertEntity
import edu.cit.resqmesh.data.repository.asMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationStateMappersTest {
    @Test fun conversationStateRoundTripsEveryField() {
        val entity = ConversationStateEntity("COMMUNITY", draft = "hi", lastReadAt = 99L)
        assertEquals(entity, entity.toModel().toEntity())
    }

    @Test fun sosChatMessageMatchesDataLayerAsMessage() {
        val entity = SosAlertEntity(
            sosId = "sos-1", originNodeId = "node-1", originName = "Origin", signingKey = "skey",
            emergencyType = "FIRE", revision = 2L, ended = false, createdAt = 10L, updatedAt = 20L,
            latitude = 14.5, longitude = 121.25, accuracyMeters = 5f, locationCapturedAt = 15L,
            locallySilenced = false, eventJson = "{}", transmission = "SENT"
        )
        assertEquals(entity.asMessage(), entity.toModel().toChatMessage())
    }
}
