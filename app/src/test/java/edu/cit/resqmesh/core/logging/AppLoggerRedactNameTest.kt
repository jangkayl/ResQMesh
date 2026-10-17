package edu.cit.resqmesh.core.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AppLoggerRedactNameTest {
    @Test fun nullAndBlankAreMasked() {
        assertEquals("<name:none>", AppLogger.redactName(null))
        assertEquals("<name:none>", AppLogger.redactName("  "))
    }

    @Test fun stableAndNonRevealing() {
        val a = AppLogger.redactName("Alice Smith")
        assertEquals(a, AppLogger.redactName("Alice Smith "))
        assertFalse(a.contains("lice"))
        assertNotEquals(a, AppLogger.redactName("Bob"))
    }
}
