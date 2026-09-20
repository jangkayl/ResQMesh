package com.example.testresqmesh.core.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationStatusTest {

    @Test
    fun testAllLocationStatusVariantsExist() {
        val statuses = LocationStatus.values()
        assertEquals(5, statuses.size)
        assertTrue(statuses.contains(LocationStatus.IDLE))
        assertTrue(statuses.contains(LocationStatus.ACQUIRING))
        assertTrue(statuses.contains(LocationStatus.READY))
        assertTrue(statuses.contains(LocationStatus.ERROR_DENIED))
        assertTrue(statuses.contains(LocationStatus.ERROR_DISABLED))
    }
}
