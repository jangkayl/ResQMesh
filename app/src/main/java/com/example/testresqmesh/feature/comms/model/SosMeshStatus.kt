package com.example.testresqmesh.feature.comms.model

import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.NodeIdentity

enum class SosMeshState { OFF, SEARCHING, CHECKING, CONNECTED }

data class SosMeshStatus(val state: SosMeshState, val readyPeers: Int = 0) {
    val label: String get() = when (state) {
        SosMeshState.OFF -> "Mesh off"
        SosMeshState.SEARCHING -> "Searching for peers"
        SosMeshState.CHECKING -> "Checking connections"
        SosMeshState.CONNECTED -> "Connected to $readyPeers mesh ${if (readyPeers == 1) "peer" else "peers"}"
    }
}

fun sosMeshStatus(active: Boolean, devices: List<ConnectedDevice>, blocked: Set<String>): SosMeshStatus {
    if (!active) return SosMeshStatus(SosMeshState.OFF)
    val usable = devices.filter { it.isPayloadReady && !it.isProvisional && !NodeIdentity.matchesAny(it.name, blocked) }
    val responding = usable.filter { it.isPeerResponsive }.distinctBy { it.nodeId.ifBlank { it.endpointId } }.size
    return when {
        responding > 0 -> SosMeshStatus(SosMeshState.CONNECTED, responding)
        usable.isNotEmpty() -> SosMeshStatus(SosMeshState.CHECKING)
        else -> SosMeshStatus(SosMeshState.SEARCHING)
    }
}

fun sosTransmissionLabel(state: String): String = when {
    state.startsWith("CONFIRMED:") -> "State confirmed by ${NodeIdentity.displayNameOf(state.substringAfter(':'))}"
    state == "SENT" -> "Sent to a link · Remote confirmation pending"
    state == "QUEUED" -> "Saved · Waiting to send"
    else -> "Received SOS state"
}
