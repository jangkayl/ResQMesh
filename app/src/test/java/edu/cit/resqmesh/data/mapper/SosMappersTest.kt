package edu.cit.resqmesh.data.mapper

import edu.cit.resqmesh.data.local.entity.SosAlertEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SosMappersTest {
    @Test fun sosAlertRoundTripsEveryField() {
        val entity = SosAlertEntity(
            sosId = "sos-1", originNodeId = "node-1", originName = "Origin", signingKey = "skey",
            emergencyType = "FIRE", revision = 7L, ended = true, createdAt = 1_700_000_001L,
            updatedAt = 1_700_000_002L, latitude = 14.5, longitude = 121.25, accuracyMeters = 12.5f,
            locationCapturedAt = 1_700_000_003L, locallySilenced = true, eventJson = "{\"e\":1}",
            transmission = "SENT"
        )
        assertEquals(entity, entity.toModel().toEntity())
    }
}
