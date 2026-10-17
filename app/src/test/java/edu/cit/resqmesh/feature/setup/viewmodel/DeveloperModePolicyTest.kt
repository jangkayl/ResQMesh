package edu.cit.resqmesh.feature.setup.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperModePolicyTest {
    @Test
    fun releaseIgnoresStoredFlagAndPin() {
        assertFalse(DeveloperModePolicy.isEnabled(isDebugBuild = false, storedFlag = true))
        assertFalse(DeveloperModePolicy.acceptsPin(isDebugBuild = false, pin = "0000"))
    }

    @Test
    fun debugHonoursStoredFlagAndPin() {
        assertTrue(DeveloperModePolicy.isEnabled(isDebugBuild = true, storedFlag = true))
        assertFalse(DeveloperModePolicy.isEnabled(isDebugBuild = true, storedFlag = false))
        assertTrue(DeveloperModePolicy.acceptsPin(isDebugBuild = true, pin = "0000"))
        assertFalse(DeveloperModePolicy.acceptsPin(isDebugBuild = true, pin = "1234"))
    }
}
