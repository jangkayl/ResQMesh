package edu.cit.resqmesh.core.network.bluetooth.state

import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test

class BleStateStoreBoundsTest {
    @Test
    fun scanFedTablesEvictLeastRecentlyUsedEndpoints() {
        val store = BleStateStore()
        repeat(1000) {
            store.endpointFirstSeen["mac$it"] = it.toLong()
            store.endpointLastScore["mac$it"] = "score"
        }
        assertEquals(256, store.endpointFirstSeen.size)
        assertEquals(256, store.endpointLastScore.size)
        assertNull(store.endpointFirstSeen["mac0"])
        assertEquals(999L, store.endpointFirstSeen["mac999"])
    }
}
