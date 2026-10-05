package com.example.testresqmesh.core.network.bluetooth.peers

import android.bluetooth.BluetoothDevice
import com.example.testresqmesh.core.network.bluetooth.state.BleLinkRole
import com.example.testresqmesh.core.network.bluetooth.state.BleLinkState
import com.example.testresqmesh.core.network.bluetooth.state.BleStateStore
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test

class BlePeerDirectoryTest {
    private val store = BleStateStore()
    private val blocked = mutableSetOf<String>()
    private val directory = BlePeerDirectory(store) { it in blocked }

    private fun peer(endpoint: String, name: String, id: String, ready: Boolean = true) {
        val field = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val device = (field.get(null) as sun.misc.Unsafe).allocateInstance(BluetoothDevice::class.java) as BluetoothDevice
        store.activeServerConnections[endpoint] = device
        store.connectedEndpointNames[endpoint] = name
        store.endpointNodeIds[endpoint] = id
        val link = store.links.begin(endpoint, BleLinkRole.SERVER, id, ConcurrentLinkedDeque(), AtomicBoolean(false))
        if (ready) link.state = BleLinkState.READY
    }

    @Test fun duplicateEndpointsCountOnePeerAndBlockingIsReadLive() {
        peer("one", "Alpha#A001", "A001")
        peer("two", "Alpha#A001", "A001")
        assertEquals(1, directory.distinctLinkCount())
        assertEquals(1, directory.distinctReadyLinkCount())
        blocked += "Alpha#A001"
        assertEquals(0, directory.distinctReadyLinkCount())
        assertEquals(1, directory.distinctLinkCount())
    }

    @Test fun replacementAttemptDoesNotInheritOldReadiness() {
        peer("one", "Alpha#A001", "A001")
        assertTrue(directory.hasReadyEndpoint("one"))
        store.links.begin("one", BleLinkRole.SERVER, "A001", ConcurrentLinkedDeque(), AtomicBoolean(false))
        assertTrue(directory.hasLiveSocket("one"))
        assertFalse(directory.hasReadyEndpoint("one"))
        assertEquals(0, directory.distinctReadyLinkCount())
    }

    @Test fun provisionalEndpointUsesAdvertisedIdentityAndLatestObservation() {
        peer("old", "Unknown Node", "A001", ready = false)
        assertEquals("old", directory.findLinkEndpointByIdentity("Alpha#A001"))
        peer("new", "Alpha#A001", "A001")
        store.endpointLastSeen["old"] = 10L
        store.endpointLastSeen["new"] = 20L
        assertEquals("new", directory.latestEndpointForIdentity("Alpha#A001", "fallback"))
    }
}
