package edu.cit.resqmesh.feature.sos.ui.broadcast

import edu.cit.resqmesh.core.location.LocationFix
import edu.cit.resqmesh.core.ui.components.location.LocationAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SosLocationRowModelTest {
    private val now = 100_000_000L
    private val fresh = LocationFix(12f, now - 60_000L)
    private val stale = LocationFix(12f, now - 22 * 60_000L)

    private fun model(
        include: Boolean = true,
        availability: LocationAvailability = LocationAvailability.AVAILABLE,
        autoDetect: Boolean = true,
        fix: LocationFix? = fresh
    ) = sosLocationRowModel(include, availability, autoDetect, fix, now)

    @Test
    fun freshFixShowsAccuracyAndAge() {
        val m = model()
        assertEquals(SosLocationTone.GOOD, m.tone)
        assertEquals("Location included", m.title)
        assertEquals("±12 m · updated 1 min ago", m.subtitle)
        assertNull(m.problem)
    }

    @Test
    fun excludedWinsOverEveryOtherState() {
        val m = model(include = false, availability = LocationAvailability.PERMISSION_DENIED)
        assertEquals("Location not included", m.title)
        assertNull(m.problem)
    }

    @Test
    fun deniedAndOffOfferAFix() {
        for (problem in listOf(LocationAvailability.PERMISSION_DENIED, LocationAvailability.LOCATION_OFF)) {
            assertEquals(problem, model(availability = problem).problem)
        }
    }

    @Test
    fun staleFixWarnsThatANewFixIsTaken() {
        val m = model(fix = stale)
        assertEquals(SosLocationTone.ATTENTION, m.tone)
        assertEquals("Last fix 22 min ago. A new fix is taken on send.", m.subtitle)
    }

    @Test
    fun noFixDependsOnAutoDetect() {
        assertEquals(SosLocationTone.PENDING, model(fix = null).tone)
        assertEquals("A fix will be taken when you send.", model(fix = null, autoDetect = false).subtitle)
    }
}
