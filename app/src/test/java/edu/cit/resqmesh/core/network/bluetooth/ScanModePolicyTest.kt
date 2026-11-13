package edu.cit.resqmesh.core.network.bluetooth

import android.bluetooth.le.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanModePolicyTest {
    private val balanced = ScanSettings.SCAN_MODE_BALANCED
    private val lowLatency = ScanSettings.SCAN_MODE_LOW_LATENCY

    @Test fun boostOffAlwaysBalanced() {
        val c = ScanModePolicy.choose(false, false, false, true)
        assertEquals(balanced, c.mode)
        assertEquals("boost_off", c.reason)
    }

    @Test fun sosForcesLowLatencyEvenWithPeers() {
        val c = ScanModePolicy.choose(true, true, true, true, hasFreeLinkSlot = false)
        assertEquals(lowLatency, c.mode)
        assertEquals("sos", c.reason)
    }

    @Test fun aloneUsesLowLatency() {
        val c = ScanModePolicy.choose(true, false, false, false)
        assertEquals(lowLatency, c.mode)
        assertEquals("no_peers", c.reason)
    }

    @Test fun peersWithFreeSlotStayLowLatency() {
        val c = ScanModePolicy.choose(true, true, true, false, hasFreeLinkSlot = true)
        assertEquals(lowLatency, c.mode)
        assertEquals("free_slots", c.reason)
    }

    @Test fun fullLinkSlotsUseBalanced() {
        assertEquals("links_full", ScanModePolicy.choose(true, true, false, false, hasFreeLinkSlot = false).reason)
        val c = ScanModePolicy.choose(true, false, true, false, hasFreeLinkSlot = false)
        assertEquals(balanced, c.mode)
        assertEquals("links_full", c.reason)
    }
}
