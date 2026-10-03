package com.example.testresqmesh.feature.comms.model

import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.ScannedDevice
import com.example.testresqmesh.core.model.KnownNode

data class ChatUiState(
    val publicMessages: List<ChatMessage> = emptyList(),
    val privateMessages: Map<String, List<ChatMessage>> = emptyMap(),
    val connectedDevices: List<ConnectedDevice> = emptyList(),
    val knownNodes: List<KnownNode> = emptyList(),
    val scannedDevices: List<ScannedDevice> = emptyList(),
    val blockedDeviceNames: Set<String> = emptySet(),
    val peerNames: Map<String, String> = emptyMap()
)
