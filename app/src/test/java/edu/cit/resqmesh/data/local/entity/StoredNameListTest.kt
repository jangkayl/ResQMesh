package edu.cit.resqmesh.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Test

class StoredNameListTest {
    @Test
    fun commaNameRoundTrips() {
        val items = listOf("Cruz, Juan#AB12", "b#2")
        assertEquals(items, StoredNameList.decode(StoredNameList.encode(items)))
    }

    @Test
    fun percentRoundTripsSinglePass() {
        val items = listOf("100%", "%2C", "%252C", "a%,b")
        assertEquals(items, StoredNameList.decode(StoredNameList.encode(items)))
        assertEquals(listOf("%2C"), StoredNameList.decode("%252C"))
    }

    @Test
    fun legacyRowsDecodeUnchanged() {
        assertEquals(listOf("a#1", "b#2"), StoredNameList.decode("a#1,b#2"))
        assertEquals(listOf("50%off", "%zz"), StoredNameList.decode("50%off,%zz"))
    }

    @Test
    fun sentinelsAreByteIdentical() {
        for (s in listOf("PENDING", "FAILED", "Me")) {
            assertEquals(s, StoredNameList.encode(listOf(s)))
        }
    }

    @Test
    fun emptyListMapsToEmptyString() {
        assertEquals("", StoredNameList.encode(emptyList()))
        assertEquals(emptyList<String>(), StoredNameList.decode(""))
    }
}
