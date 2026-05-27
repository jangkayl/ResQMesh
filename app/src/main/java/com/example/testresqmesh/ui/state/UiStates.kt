package com.example.testresqmesh.ui.state

import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.ScannedDevice
import com.example.testresqmesh.core.model.KnownNode

data class ConnectionUiState(
    val isOnline: Boolean = false,
    val connectionStatus: String = "Ready to deploy Mesh Node.",
    val myNodeName: String = "",
    val isRescanning: Boolean = false
)

data class RadarUiState(
    val scannedDevices: List<ScannedDevice> = emptyList(),
    val connectedDevices: List<ConnectedDevice> = emptyList(),
    val blockedDeviceNames: Set<String> = emptySet()
)

data class ChatUiState(
    val publicMessages: List<ChatMessage> = emptyList(),
    val privateMessages: Map<String, List<ChatMessage>> = emptyMap(),
    val connectedDevices: List<ConnectedDevice> = emptyList(),
    val knownNodes: List<KnownNode> = emptyList()
)
