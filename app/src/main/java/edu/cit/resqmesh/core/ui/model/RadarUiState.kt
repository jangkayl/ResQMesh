package edu.cit.resqmesh.core.ui.model

import edu.cit.resqmesh.core.model.ConnectedDevice
import edu.cit.resqmesh.core.model.ScannedDevice
import edu.cit.resqmesh.core.model.KnownNode

data class RadarUiState(
    val scannedDevices: List<ScannedDevice> = emptyList(),
    val connectedDevices: List<ConnectedDevice> = emptyList(),
    val recentOfflineDevices: List<ConnectedDevice> = emptyList(),
    val knownNodes: List<KnownNode> = emptyList(),
    val blockedDeviceNames: Set<String> = emptySet(),
    val topology: Map<String, Set<String>> = emptyMap()
)
