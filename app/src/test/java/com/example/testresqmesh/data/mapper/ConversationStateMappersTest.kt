package com.example.testresqmesh.data.mapper

import com.example.testresqmesh.core.model.toChatMessage
import com.example.testresqmesh.data.local.entity.ConversationStateEntity
import com.example.testresqmesh.data.local.entity.SosAlertEntity
import com.example.testresqmesh.data.repository.asMessage
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
