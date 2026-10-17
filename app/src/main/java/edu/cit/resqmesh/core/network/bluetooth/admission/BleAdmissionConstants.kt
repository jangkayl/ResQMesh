package edu.cit.resqmesh.core.network.bluetooth.admission

import edu.cit.resqmesh.core.network.bluetooth.admission.BleAdmissionConstants.CANDIDATE_FRESH_MS
import edu.cit.resqmesh.core.network.bluetooth.admission.BleAdmissionConstants.MAX_CANDIDATES
import edu.cit.resqmesh.core.network.bluetooth.admission.BleAdmissionConstants.BRIDGE_COOLDOWN_MS
import edu.cit.resqmesh.core.network.bluetooth.admission.BleAdmissionConstants.ORPHAN_RESCUE_DELAY_MS
import edu.cit.resqmesh.core.network.bluetooth.admission.BleAdmissionConstants.MIN_CONNECTION_JITTER_MS
import edu.cit.resqmesh.core.network.bluetooth.admission.BleAdmissionConstants.MAX_CONNECTION_JITTER_MS
import edu.cit.resqmesh.core.network.bluetooth.admission.BleAdmissionConstants.FALLBACK_INITIATOR_DELAY_MS
import edu.cit.resqmesh.core.network.bluetooth.admission.BleAdmissionConstants.macRegex

internal object BleAdmissionConstants {
    const val CANDIDATE_FRESH_MS = 8_000L
    const val MAX_CANDIDATES = 32
    const val BRIDGE_COOLDOWN_MS = 60_000L
    const val ORPHAN_RESCUE_DELAY_MS = 5_000L
    const val MIN_CONNECTION_JITTER_MS = 100L
    const val MAX_CONNECTION_JITTER_MS = 1_000L
    const val FALLBACK_INITIATOR_DELAY_MS = 2_500L
    val macRegex = Regex("(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")
}
