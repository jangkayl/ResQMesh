package edu.cit.resqmesh.core.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationDemandTest {
    private val map = LiveLocationSpec(5_000L, 5f)
    private val sos = LiveLocationSpec(15_000L, 5f)

    @Test
    fun fastestOpenSessionWinsAndLastCloseEndsDemand() {
        val changes = mutableListOf<LiveLocationSpec?>()
        val demand = LiveLocationDemand { changes += it }
        val sosSession = demand.acquire(sos)
        val mapSession = demand.acquire(map)
        mapSession.close()
        sosSession.close()
        assertEquals(listOf(sos, map, sos, null), changes)
    }

    @Test
    fun sameSpecSessionsAreCountedAndDoubleCloseIsIgnored() {
        val demand = LiveLocationDemand { }
        val first = demand.acquire(map)
        val second = demand.acquire(map)
        first.close()
        first.close()
        assertEquals(map, demand.current)
        second.close()
        assertNull(demand.current)
    }

    @Test
    fun concurrentPinpointCallersShareOneRequestAndAllGetTheResult() {
        val request = SharedResultRequest<String?>()
        val results = mutableListOf<String?>()
        assertTrue(request.join { results += "sos:$it" })
        assertFalse(request.join { results += "map:$it" })
        request.complete("fix")
        assertEquals(listOf("sos:fix", "map:fix"), results)
        assertTrue(request.join { })
    }

    @Test
    fun fixUsabilityChecksAgeAccuracyAndMissingTime() {
        assertTrue(isFixUsable(1_000L, 20f, 2_000L, 1_000L, 50f))
        assertFalse(isFixUsable(999L, 20f, 2_000L, 1_000L, 50f))
        assertFalse(isFixUsable(1_000L, 51f, 2_000L, 1_000L, 50f))
        assertFalse(isFixUsable(0L, 20f, 500L, 1_000L))
    }
}
