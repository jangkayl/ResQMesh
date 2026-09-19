package com.example.testresqmesh.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveVoicePolicyTest {
    @Test fun `fresh later sequence is accepted after a lost frame`() {
        assertEquals(
            LiveVoicePolicy.FrameDecision.ACCEPT,
            LiveVoicePolicy.validate("session", 5, 10_000L, 1, 3, 10_500L)
        )
    }

    @Test fun `expired and replayed voice is not accepted`() {
        assertEquals(
            LiveVoicePolicy.FrameDecision.EXPIRED,
            LiveVoicePolicy.validate("session", 5, 10_000L, 1, null, 13_000L)
        )
        assertEquals(
            LiveVoicePolicy.FrameDecision.OLD_SEQUENCE,
            LiveVoicePolicy.validate("session", 5, 10_000L, 1, 5, 10_100L)
        )
    }

    @Test fun `voice relay is bounded`() {
        assertEquals(
            LiveVoicePolicy.FrameDecision.HOP_LIMIT,
            LiveVoicePolicy.validate("session", 1, 10_000L, LiveVoicePolicy.MAX_RELAY_HOPS + 1, null, 10_100L)
        )
    }
}
