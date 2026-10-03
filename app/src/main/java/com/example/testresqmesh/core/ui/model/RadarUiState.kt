package com.example.testresqmesh.core.ui.model

import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.ScannedDevice
import com.example.testresqmesh.core.model.KnownNode

data class RadarUiState(
    val scannedDevices: List<ScannedDevice> = emptyList(),
    val connectedDevices: List<ConnectedDevice> = emptyList(),
    val recentOfflineDevices: List<ConnectedDevice> = emptyList(),
    val knownNodes: List<KnownNode> = emptyList(),
    val blockedDeviceNames: Set<String> = emptySet(),
    val topology: Map<String, Set<String>> = emptyMap()
)
