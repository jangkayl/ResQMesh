package edu.cit.resqmesh.core.location

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationFixTest {
    private val now = 10_000_000L

    @Test
    fun summaryShowsAccuracyAndAge() {
        assertEquals("±12 m · updated just now", LocationFix(12.7f, now - 30_000L).summary(now))
        assertEquals("±8 m · updated 3 min ago", LocationFix(8f, now - 180_000L).summary(now))
        assertEquals("±1 m · updated 2 h ago", LocationFix(0.2f, now - 7_200_000L).summary(now))
    }

    @Test
    fun futureFixReadsJustNow() {
        assertEquals("±5 m · updated just now", LocationFix(5f, now + 60_000L).summary(now))
    }
}
