package edu.cit.resqmesh.core.network.bluetooth

import android.bluetooth.le.ScanSettings

/** Chosen scan mode plus a short log reason. */
data class ScanModeChoice(val mode: Int, val reason: String) {
    val label: String get() = if (mode == ScanSettings.SCAN_MODE_LOW_LATENCY) "LOW_LATENCY" else "BALANCED"
}

/** Picks the BLE scan duty cycle: aggressive only while alone or sending SOS, otherwise balanced. */
object ScanModePolicy {
    /** A valid advertisement within this window counts as a recently seen peer. */
    const val RECENT_PEER_WINDOW_MS = 30_000L

    fun choose(
        rangeBoostEnabled: Boolean,
        hasReadyConnection: Boolean,
        recentlySawPeer: Boolean,
        localSosActive: Boolean
    ): ScanModeChoice = when {
        !rangeBoostEnabled -> ScanModeChoice(ScanSettings.SCAN_MODE_BALANCED, "boost_off")
        localSosActive -> ScanModeChoice(ScanSettings.SCAN_MODE_LOW_LATENCY, "sos")
        !hasReadyConnection && !recentlySawPeer -> ScanModeChoice(ScanSettings.SCAN_MODE_LOW_LATENCY, "no_peers")
        else -> ScanModeChoice(ScanSettings.SCAN_MODE_BALANCED, "peers_present")
    }
}
