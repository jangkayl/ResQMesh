package edu.cit.resqmesh.feature.setup.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperModePolicyTest {
    private val salt = "test-salt"
    private val hash = DeveloperModePolicy.hash(salt, "4821")

    @Test
    fun knownVectorMatchesGradleDerivation() {
        // Same vector is asserted in app/build.gradle.kts so the two derivations cannot drift.
        assertEquals(
            "a9a37ddec1da4994002d80be9095b41ffaa08104",
            DeveloperModePolicy.hash("resqmesh-dev-pin-v1", "known-PIN-4821")
        )
    }

    @Test
    fun nonAsciiPinIsRejected() {
        val h = DeveloperModePolicy.hash(salt, "4821")
        assertFalse(DeveloperModePolicy.acceptsPin(h, salt, "4821é"))
    }

    @Test
    fun configuredAcceptsCorrectPin() {
        assertTrue(DeveloperModePolicy.acceptsPin(hash, salt, "4821"))
        assertTrue(DeveloperModePolicy.isEnabled(hash, storedFlag = true, storedHash = hash))
        assertFalse(DeveloperModePolicy.isEnabled(hash, storedFlag = false, storedHash = hash))
    }

    @Test
    fun rejectsWrongAndNullPin() {
        assertFalse(DeveloperModePolicy.acceptsPin(hash, salt, "0000"))
        assertFalse(DeveloperModePolicy.acceptsPin(hash, salt, ""))
        assertFalse(DeveloperModePolicy.acceptsPin(hash, salt, null))
        assertFalse(DeveloperModePolicy.acceptsPin(hash, "other-salt", "4821"))
    }

    @Test
    fun unconfiguredRejectsEverythingAndIgnoresStoredFlag() {
        assertFalse(DeveloperModePolicy.isConfigured(""))
        assertFalse(DeveloperModePolicy.acceptsPin("", salt, "0000"))
        assertFalse(DeveloperModePolicy.acceptsPin("", salt, ""))
        assertFalse(DeveloperModePolicy.isEnabled("", storedFlag = true, storedHash = ""))
    }

    @Test
    fun rotatedPinTurnsStoredFlagOff() {
        assertFalse(DeveloperModePolicy.isEnabled(hash, storedFlag = true, storedHash = "old"))
        assertFalse(DeveloperModePolicy.isEnabled(hash, storedFlag = true, storedHash = null))
    }

    @Test
    fun limiterLocksAfterFiveFailuresThenReleases() {
        var now = 0L
        val limiter = PinAttemptLimiter(clock = { now })
        repeat(4) { limiter.recordFailure() }
        assertTrue(limiter.remainingLockSeconds() == 0)
        limiter.recordFailure()
        assertTrue(limiter.remainingLockSeconds() == 30)
        now = 31_000L
        assertTrue(limiter.remainingLockSeconds() == 0)
    }
}
