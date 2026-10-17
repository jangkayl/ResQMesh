package edu.cit.resqmesh.data.mapper

import edu.cit.resqmesh.data.local.entity.UserEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class UserMappersTest {
    @Test fun userRoundTripsEveryField() {
        val entity = UserEntity(
            userId = "user-1", deviceId = "device-1", displayName = "Name", publicKey = "pub",
            createdAt = 1_700_000_001L
        )
        assertEquals(entity, entity.toModel().toEntity())
    }
}
