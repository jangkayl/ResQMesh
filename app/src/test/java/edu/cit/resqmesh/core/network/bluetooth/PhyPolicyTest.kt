package edu.cit.resqmesh.core.network.bluetooth

import android.bluetooth.BluetoothDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhyPolicyTest {
    private val coded = BleCapabilities(codedPhy = true, phy2M = true, extendedAdvertising = true)

    @Test fun oldApiNeverUsesCoded() {
        assertFalse(PhyPolicy.supportsCodedLinks(25, coded))
        assertFalse(PhyPolicy.supportsCodedAdvertising(25, coded))
        assertNull(PhyPolicy.codedPreference(25, coded))
    }

    @Test fun unknownOrMissingCodedSupportFallsBack() {
        assertNull(PhyPolicy.codedPreference(33, BleCapabilities()))
        assertNull(PhyPolicy.codedPreference(33, coded.copy(codedPhy = false)))
    }

    @Test fun codedAdvertisingAlsoNeedsExtendedAdvertising() {
        assertTrue(PhyPolicy.supportsCodedAdvertising(33, coded))
        assertFalse(PhyPolicy.supportsCodedAdvertising(33, coded.copy(extendedAdvertising = false)))
        assertFalse(PhyPolicy.supportsCodedAdvertising(33, coded.copy(extendedAdvertising = null)))
    }

    @Test fun supportedPhoneGetsCodedS8() {
        val pref = PhyPolicy.codedPreference(33, coded)!!
        assertEquals(BluetoothDevice.PHY_LE_CODED_MASK, pref.txMask)
        assertEquals(BluetoothDevice.PHY_LE_CODED_MASK, pref.rxMask)
        assertEquals(BluetoothDevice.PHY_OPTION_S8, pref.option)
    }

    @Test fun weakThresholdIsInclusive() {
        assertTrue(PhyPolicy.isWeak(PhyPolicy.WEAK_LINK_RSSI_DBM))
        assertTrue(PhyPolicy.isWeak(-90))
        assertFalse(PhyPolicy.isWeak(-60))
    }

    @Test fun codedSightingWaitsForLegacySilence() {
        assertTrue(PhyPolicy.acceptCodedSighting(null, 50_000L))
        assertFalse(PhyPolicy.acceptCodedSighting(45_000L, 50_000L))
        assertTrue(PhyPolicy.acceptCodedSighting(50_000L - PhyPolicy.CODED_SIGHTING_SUPPRESS_MS, 50_000L))
    }
}
