package edu.cit.resqmesh.core.network.bluetooth

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build

/** PHY preference handed to `setPreferredPhy`. */
data class PhyPreference(val txMask: Int, val rxMask: Int, val option: Int)

/**
 * LE Coded PHY (long range) choices. Coded S=8 trades throughput (125 kbit/s) for roughly 12 dB of
 * link budget, so links request it only when weak or struggling, and only when this phone's
 * controller supports it. The controller keeps the current PHY when the peer cannot do Coded.
 */
object PhyPolicy {
    /** Link RSSI at or below this counts as weak enough to trade throughput for range. */
    const val WEAK_LINK_RSSI_DBM = -75

    /**
     * A peer advertises on 1M and Coded from different random addresses. While its 1M advertisement
     * was heard this recently, the Coded sighting is ignored so one identity keeps one endpoint; the
     * Coded address takes over only once the 1M signal is out of reach.
     */
    const val CODED_SIGHTING_SUPPRESS_MS = 10_000L

    @Volatile private var cached: BleCapabilities? = null

    /** Capabilities never change for a process, so read them once. */
    fun capabilities(context: Context): BleCapabilities =
        cached ?: BleCapabilities.read(context).also { cached = it }

    fun supportsCodedLinks(sdk: Int, caps: BleCapabilities): Boolean =
        sdk >= Build.VERSION_CODES.O && caps.codedPhy == true

    fun supportsCodedAdvertising(sdk: Int, caps: BleCapabilities): Boolean =
        supportsCodedLinks(sdk, caps) && caps.extendedAdvertising == true

    /** Coded-only preference for a weak or struggling link, or null when it cannot help. */
    fun codedPreference(sdk: Int, caps: BleCapabilities): PhyPreference? =
        if (!supportsCodedLinks(sdk, caps)) null
        else PhyPreference(
            BluetoothDevice.PHY_LE_CODED_MASK,
            BluetoothDevice.PHY_LE_CODED_MASK,
            BluetoothDevice.PHY_OPTION_S8
        )

    fun acceptCodedSighting(lastLegacySeenAt: Long?, now: Long): Boolean =
        lastLegacySeenAt == null || now - lastLegacySeenAt >= CODED_SIGHTING_SUPPRESS_MS

    fun isWeak(rssi: Int): Boolean = rssi <= WEAK_LINK_RSSI_DBM

    fun phyName(phy: Int): String = when (phy) {
        BluetoothDevice.PHY_LE_1M -> "1M"
        BluetoothDevice.PHY_LE_2M -> "2M"
        BluetoothDevice.PHY_LE_CODED -> "CODED"
        else -> "UNKNOWN($phy)"
    }
}
