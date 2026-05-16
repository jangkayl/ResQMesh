package com.example.testresqmesh.ui.states

import com.example.testresqmesh.data.models.ChatMessage
import com.example.testresqmesh.data.models.ConnectedDevice
import com.example.testresqmesh.data.models.ScannedDevice

data class ConnectionUiState(
    val isOnline: Boolean = false,
    val connectionStatus: String = "Ready to deploy Mesh Node.",
    val myNodeName: String = "",
    val isRescanning: Boolean = false
)

data class RadarUiState(
    val scannedDevices: List<ScannedDevice> = emptyList(),
    val connectedDevices: List<ConnectedDevice> = emptyList()
)

data class ChatUiState(
    val publicMessages: List<ChatMessage> = emptyList(),
    val privateMessages: Map<String, List<ChatMessage>> = emptyMap()
)
