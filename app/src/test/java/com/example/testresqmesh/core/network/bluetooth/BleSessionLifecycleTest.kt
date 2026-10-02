package com.example.testresqmesh.core.network.bluetooth

import org.junit.Assert.*
import org.junit.Test

class BleSessionLifecycleTest {
    private class Fixture {
        val starts = mutableListOf<Long>()
        var stops = 0
        val states = mutableListOf<MeshTransportState>()
        val lifecycle = BleSessionLifecycle({ starts.add(it) }, { stops++ }, { states.add(it) })
    }

    @Test fun toggleRebuildsTransportAndRetainsRequestedSession() {
        val f = Fixture()
        f.lifecycle.start(BleAvailability.AVAILABLE)
        val old = f.starts.single()
        f.lifecycle.ready(old)
        f.lifecycle.peers(1)
        f.lifecycle.reconcile(BleAvailability.BLUETOOTH_OFF)
        assertTrue(f.lifecycle.requested)
        assertFalse(f.lifecycle.owns(old))
        assertEquals(MeshTransportState.BLUETOOTH_OFF, f.lifecycle.state)
        f.lifecycle.reconcile(BleAvailability.AVAILABLE)
        assertEquals(2, f.starts.size)
        assertEquals(1, f.stops)
        assertTrue(f.lifecycle.owns(f.starts.last()))
    }

    @Test fun repeatedEventsNeverDuplicateTransport() {
        val f = Fixture()
        repeat(3) { f.lifecycle.start(BleAvailability.AVAILABLE) }
        assertEquals(1, f.starts.size)
        repeat(3) { f.lifecycle.reconcile(BleAvailability.BLUETOOTH_OFF) }
        assertEquals(1, f.stops)
        repeat(3) { f.lifecycle.reconcile(BleAvailability.AVAILABLE) }
        assertEquals(2, f.starts.size)
    }

    @Test fun offlineCannotBeUndoneByAdapterOrLateCallback() {
        val f = Fixture()
        f.lifecycle.start(BleAvailability.AVAILABLE)
        val old = f.starts.single()
        f.lifecycle.stop()
        f.lifecycle.reconcile(BleAvailability.AVAILABLE)
        f.lifecycle.ready(old)
        f.lifecycle.failed(old)
        f.lifecycle.peers(1)
        assertEquals(MeshTransportState.OFFLINE, f.lifecycle.state)
        assertEquals(1, f.starts.size)
    }

    @Test fun oldFailureCannotStopReplacementAtSameAddress() {
        val f = Fixture()
        f.lifecycle.start(BleAvailability.AVAILABLE)
        val old = f.starts.single()
        f.lifecycle.reconcile(BleAvailability.BLUETOOTH_OFF)
        f.lifecycle.reconcile(BleAvailability.AVAILABLE)
        val replacement = f.starts.last()
        f.lifecycle.ready(replacement)
        f.lifecycle.ready(old)
        f.lifecycle.failed(old)
        assertTrue(f.lifecycle.owns(replacement))
        assertEquals(1, f.stops)
    }

    @Test fun offAtStartAndPermissionRestorationWaitForAvailability() {
        val f = Fixture()
        f.lifecycle.start(BleAvailability.BLUETOOTH_OFF)
        assertTrue(f.starts.isEmpty())
        f.lifecycle.reconcile(BleAvailability.PERMISSION_REQUIRED)
        assertEquals(MeshTransportState.PERMISSION_REQUIRED, f.lifecycle.state)
        assertTrue(f.starts.isEmpty())
        f.lifecycle.reconcile(BleAvailability.AVAILABLE)
        assertEquals(1, f.starts.size)
        f.lifecycle.reconcile(BleAvailability.PERMISSION_REQUIRED)
        assertEquals(1, f.stops)
        f.lifecycle.reconcile(BleAvailability.AVAILABLE)
        assertEquals(2, f.starts.size)
    }

    @Test fun partialStartupFailureCanRetryAndOnlyReadyPeersCount() {
        val f = Fixture()
        f.lifecycle.start(BleAvailability.AVAILABLE)
        f.lifecycle.peers(1)
        assertEquals(MeshTransportState.STARTING, f.lifecycle.state)
        f.lifecycle.failed(f.starts.single())
        assertEquals(MeshTransportState.ERROR, f.lifecycle.state)
        assertEquals(1, f.stops)
        f.lifecycle.reconcile(BleAvailability.AVAILABLE)
        f.lifecycle.ready(f.starts.last())
        f.lifecycle.peers(1)
        assertEquals(MeshTransportState.CONNECTED, f.lifecycle.state)
        f.lifecycle.peers(0)
        assertEquals(MeshTransportState.SEARCHING, f.lifecycle.state)
    }

    @Test fun generationIsInvalidatedBeforePlatformCleanup() {
        lateinit var lifecycle: BleSessionLifecycle
        var old = 0L
        lifecycle = BleSessionLifecycle({ old = it }, { assertFalse(lifecycle.owns(old)) }, {})
        lifecycle.start(BleAvailability.AVAILABLE)
        lifecycle.reconcile(BleAvailability.BLUETOOTH_OFF)
    }

    @Test fun scanBudgetSurvivesFailuresAndOnlyAllowsNewStartsAfterWindow() {
        val budget = BleScanStartBudget()
        repeat(4) { budget.recordStart(1_000L + it) }
        assertEquals(29_000L, budget.delayUntilAllowed(2_000L))
        assertEquals(0L, budget.delayUntilAllowed(31_000L))
        budget.recordStart(31_000L)
        assertEquals(1L, budget.delayUntilAllowed(31_000L))
    }
}
