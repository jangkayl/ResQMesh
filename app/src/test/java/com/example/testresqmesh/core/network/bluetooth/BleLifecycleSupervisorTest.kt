package com.example.testresqmesh.core.network.bluetooth

import android.bluetooth.BluetoothDevice
import android.os.Handler
import com.example.testresqmesh.core.network.bluetooth.state.*
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test

class BleLifecycleSupervisorTest {
    private class Fixture {
        val store = BleStateStore()
        val retired = mutableListOf<String>()
        val probes = mutableListOf<String>()
        val supervisor = BleLifecycleSupervisor(store, Handler(), HeartbeatCoordinator(), 8000, 25000,
            {}, { probes.add(it) }, { retired.add(it) }, { retired.add(it) }, { _, _ -> }, {}, {})
        init {
            store.isNodeActive.set(true)
            val field = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            val device = (field.get(null) as sun.misc.Unsafe).allocateInstance(BluetoothDevice::class.java) as BluetoothDevice
            store.activeServerConnections["endpoint"] = device
            store.connectionInteractionTimes["endpoint"] = System.currentTimeMillis() - 60_000
        }
        fun link() = store.links.begin("endpoint", BleLinkRole.SERVER, "peer", ConcurrentLinkedDeque(), AtomicBoolean(false))
    }

    @Test fun missingSubscriptionIsOwnedBySetupDeadlineNotLiveness() {
        val f = Fixture(); val link = f.link()
        f.store.links.transition(link, BleLinkState.CONFIGURING)
        f.supervisor.checkNow()
        assertTrue(f.retired.isEmpty()); assertTrue(f.probes.isEmpty())
    }
    @Test fun missingDisconnectCallbackDoesNotCauseRepeatedLivenessCancellation() {
        val f = Fixture(); val link = f.link()
        f.store.links.transition(link, BleLinkState.CONFIGURING)
        f.store.links.transition(link, BleLinkState.DISCONNECTING)
        repeat(3) { f.supervisor.checkNow() }
        assertTrue(f.retired.isEmpty()); assertTrue(f.probes.isEmpty())
    }
    @Test fun silentReadyEndpointStillGetsRealRecovery() {
        val f = Fixture(); val link = f.link()
        f.store.links.transition(link, BleLinkState.CONFIGURING)
        f.store.links.transition(link, BleLinkState.READY)
        link.identityAdmitted = true
        f.supervisor.checkNow()
        assertEquals(listOf("endpoint"), f.probes)
        assertEquals(2, f.retired.size)
    }
    @Test fun configuredButUnadmittedEndpointUsesIdentityDeadline() {
        val f = Fixture(); val link = f.link()
        f.store.links.transition(link, BleLinkState.CONFIGURING)
        f.store.links.transition(link, BleLinkState.READY)
        repeat(3) { f.supervisor.checkNow() }
        assertTrue(f.retired.isEmpty()); assertTrue(f.probes.isEmpty())
    }
}
