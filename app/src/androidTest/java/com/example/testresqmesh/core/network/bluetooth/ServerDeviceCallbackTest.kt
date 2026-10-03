package com.example.testresqmesh.core.network.bluetooth

import android.bluetooth.BluetoothAdapter
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.testresqmesh.core.network.bluetooth.state.*
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Uses device wrappers only; it never connects, subscribes, or sends Bluetooth traffic. */
@RunWith(AndroidJUnit4::class)
class ServerDeviceCallbackTest {
    @Test fun separatelyConstructedWrappersForTheSameAddressMatchButOtherPeersDoNot() {
        val availableAdapter = BluetoothAdapter.getDefaultAdapter()
        assumeNotNull(availableAdapter)
        val adapter = requireNotNull(availableAdapter)
        val stored = adapter.getRemoteDevice("02:00:00:00:00:01")
        val callback = adapter.getRemoteDevice("02:00:00:00:00:01")
        val other = adapter.getRemoteDevice("02:00:00:00:00:02")
        assertNotSame(stored, callback)
        val store = BleStateStore()
        val queue = ConcurrentLinkedDeque<GattTransfer>()
        val writing = AtomicBoolean()
        val link = store.links.begin(stored.address, BleLinkRole.SERVER, "peer", queue, writing)
        link.serverDevice = stored
        store.links.transition(link, BleLinkState.CONFIGURING)
        store.links.transition(link, BleLinkState.READY)
        val coordinator = GattTransferCoordinator(store)
        val flight = GattTransferFlight(GattTransfer(byteArrayOf(1)), link, queue, writing, null, stored)
        assertTrue(coordinator.callbackMatches(flight, BleLinkRole.SERVER, null, callback))
        assertFalse(coordinator.callbackMatches(flight, BleLinkRole.SERVER, null, other))
        store.links.retire(link) {}
        assertFalse(coordinator.callbackMatches(flight, BleLinkRole.SERVER, null, callback))
    }
}
