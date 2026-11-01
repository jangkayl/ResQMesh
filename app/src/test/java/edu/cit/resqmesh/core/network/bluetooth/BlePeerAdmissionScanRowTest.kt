package edu.cit.resqmesh.core.network.bluetooth

import edu.cit.resqmesh.core.network.bluetooth.BlePeerAdmissionControllerTest.TestFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlePeerAdmissionScanRowTest {
    @Test
    fun blockedAdvertisementPublishesScanRowButNeverQueuesOrConnects() {
        val f = TestFixture(directLinks = 0, localScoreStr = "900AA")
        f.blockedPeers.add("Bob#B2")
        val ad = BleAdvertisement("11:22:33:44:55:66", "Bob#B2", "B2", "800BB", 0)

        f.controller.handle(ad)
        f.clockTime += 500L
        f.scheduler.runAll()

        assertEquals(BleAdmissionReason.BLOCKED, f.decisions.single().reason)
        assertEquals(BleConnectStartResult.REJECTED, f.decisions.single().result)
        assertEquals(listOf("Bob#B2"), f.scans.map { it.name })
        assertEquals(1_000_000L, f.store.endpointLastSeen["11:22:33:44:55:66"])
        assertFalse(f.store.connectedEndpointIds.contains("11:22:33:44:55:66"))
        assertTrue(f.connectCalls.isEmpty())
    }

    @Test
    fun selfMatchAdvertisementPublishesNoScanRow() {
        val f = TestFixture()
        f.controller.handle(BleAdvertisement("11:22:33:44:55:66", "Alice#A1", "A1", "800BB", 0))
        f.scheduler.runAll()

        assertEquals(BleAdmissionReason.SELF_MATCH, f.decisions.single().reason)
        assertTrue(f.scans.isEmpty())
        assertTrue(f.store.endpointLastSeen.isEmpty())
    }
}
