package edu.cit.resqmesh.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SosFutureSkewTest {
    private fun event(createdAt: Long) = SosEvent(
        eventId = "e", sosId = "s", originNodeId = "n", originName = "name", signingKey = "k",
        emergencyType = "Fire", revision = 1, ended = false, createdAt = createdAt, updatedAt = createdAt
    )

    @Test
    fun createdAtBeyondTheSkewWindowIsFutureDated() {
        val now = 1_000_000_000_000L
        assertFalse(SosProtocol.futureDated(event(now), now))
        assertFalse(SosProtocol.futureDated(event(now + SosProtocol.MAX_FUTURE_SKEW_MS), now))
        assertTrue(SosProtocol.futureDated(event(now + SosProtocol.MAX_FUTURE_SKEW_MS + 1), now))
        assertFalse(SosProtocol.futureDated(event(now - 86_400_000L), now))
    }
}
