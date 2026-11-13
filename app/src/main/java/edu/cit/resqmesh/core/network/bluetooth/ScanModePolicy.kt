package edu.cit.resqmesh.core.network.bluetooth

import android.bluetooth.le.ScanSettings

/** Chosen scan mode plus a short log reason. */
data class ScanModeChoice(val mode: Int, val reason: String) {
    val label: String get() = if (mode == ScanSettings.SCAN_MODE_LOW_LATENCY) "LOW_LATENCY" else "BALANCED"
}

/**
 * Picks the BLE scan duty cycle. With Range Boost on, scanning stays aggressive while the phone is
 * alone, sending SOS or still has a free direct-link slot: weak edge-of-range advertisements are
 * only caught when the radio listens more often. Balanced is used once every slot is READY.
 */
object ScanModePolicy {
    /** A valid advertisement within this window counts as a recently seen peer. */
    const val RECENT_PEER_WINDOW_MS = 30_000L

    fun choose(
        rangeBoostEnabled: Boolean,
        hasReadyConnection: Boolean,
        recentlySawPeer: Boolean,
        localSosActive: Boolean,
        hasFreeLinkSlot: Boolean = true
    ): ScanModeChoice = when {
        !rangeBoostEnabled -> ScanModeChoice(ScanSettings.SCAN_MODE_BALANCED, "boost_off")
        localSosActive -> ScanModeChoice(ScanSettings.SCAN_MODE_LOW_LATENCY, "sos")
        !hasReadyConnection && !recentlySawPeer -> ScanModeChoice(ScanSettings.SCAN_MODE_LOW_LATENCY, "no_peers")
        hasFreeLinkSlot -> ScanModeChoice(ScanSettings.SCAN_MODE_LOW_LATENCY, "free_slots")
        else -> ScanModeChoice(ScanSettings.SCAN_MODE_BALANCED, "links_full")
    }
}
