package edu.cit.resqmesh.feature.setup.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs on the device because Android's PBKDF2 provider, unlike the JVM's, rejects an empty password. */
class DeveloperModePolicyDeviceTest {
    private val salt = "test-salt"
    private val hash = DeveloperModePolicy.hash(salt, "4821")

    @Test fun emptyPinIsRejectedWithoutThrowing() {
        assertFalse(DeveloperModePolicy.acceptsPin(hash, salt, ""))
    }

    @Test fun correctPinIsStillAccepted() {
        assertTrue(DeveloperModePolicy.acceptsPin(hash, salt, "4821"))
    }
}
