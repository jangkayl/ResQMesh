package com.example.testresqmesh.ui.state

import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.ScannedDevice
import com.example.testresqmesh.core.model.KnownNode
import com.example.testresqmesh.core.model.AttachmentUiState

data class ConnectionUiState(
    val isOnline: Boolean = false,
    val connectionStatus: String = "Ready to deploy Mesh Node.",
    val myNodeName: String = "",
    val isRescanning: Boolean = false
)

data class RadarUiState(
    val scannedDevices: List<ScannedDevice> = emptyList(),
    val connectedDevices: List<ConnectedDevice> = emptyList(),
    val recentOfflineDevices: List<ConnectedDevice> = emptyList(),
    val knownNodes: List<KnownNode> = emptyList(),
    val blockedDeviceNames: Set<String> = emptySet(),
    val topology: Map<String, Set<String>> = emptyMap()
)

data class ChatUiState(
    val publicMessages: List<ChatMessage> = emptyList(),
    val privateMessages: Map<String, List<ChatMessage>> = emptyMap(),
    val connectedDevices: List<ConnectedDevice> = emptyList(),
    val knownNodes: List<KnownNode> = emptyList(),
    val scannedDevices: List<ScannedDevice> = emptyList(),
    val blockedDeviceNames: Set<String> = emptySet(),
    val attachments: Map<String, AttachmentUiState> = emptyMap()
)
