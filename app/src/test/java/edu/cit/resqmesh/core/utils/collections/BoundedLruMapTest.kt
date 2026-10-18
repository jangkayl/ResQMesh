package edu.cit.resqmesh.core.utils.collections

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BoundedLruMapTest {
    @Test fun evictsLeastRecentlyUsedBeyondCap() {
        val map = BoundedLruMap<String, Int>(3)
        map.put("a", 1); map.put("b", 2); map.put("c", 3)
        map.put("d", 4)
        assertEquals(listOf("b", "c", "d"), map.keys())
        assertNull(map["a"])
        assertEquals(3, map.size)
    }

    @Test fun getRefreshesRecency() {
        val map = BoundedLruMap<String, Int>(3)
        map.put("a", 1); map.put("b", 2); map.put("c", 3)
        assertEquals(1, map["a"])
        map.put("d", 4)
        assertTrue(map.containsKey("a"))
        assertFalse(map.containsKey("b"))
        assertEquals(listOf("c", "a", "d"), map.keys())
    }

    @Test fun removeClearAndGetOrPut() {
        val map = BoundedLruMap<String, Int>(2)
        assertEquals(5, map.getOrPut("x") { 5 })
        assertEquals(5, map.getOrPut("x") { 9 })
        assertEquals(5, map.remove("x"))
        map.put("y", 1)
        map.clear()
        assertEquals(0, map.size)
    }
}
