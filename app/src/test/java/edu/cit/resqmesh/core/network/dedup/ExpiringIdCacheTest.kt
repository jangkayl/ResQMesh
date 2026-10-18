package edu.cit.resqmesh.core.network.dedup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class ExpiringIdCacheTest {
    private var now = 1_000L

    @Test fun marksNewOnceAndDetectsDuplicates() {
        val cache = ExpiringIdCache(10, 1000L) { now }
        assertTrue(cache.markSeen("a"))
        assertFalse(cache.markSeen("a"))
        assertTrue(cache.contains("a"))
        assertFalse(cache.contains("b"))
    }

    @Test fun entriesExpireAfterTtlAndRepeatsDoNotExtendIt() {
        val cache = ExpiringIdCache(10, 1000L) { now }
        cache.markSeen("a")
        now += 600
        assertFalse(cache.markSeen("a"))
        now += 400
        assertFalse(cache.contains("a"))
        assertTrue(cache.markSeen("a"))
    }

    @Test fun evictsOldestWhenOverCap() {
        val cache = ExpiringIdCache(3, 100_000L) { now }
        listOf("a", "b", "c", "d").forEach { cache.markSeen(it); now += 1 }
        assertEquals(3, cache.size)
        assertFalse(cache.contains("a"))
        assertTrue(cache.contains("b") && cache.contains("c") && cache.contains("d"))
    }
}
