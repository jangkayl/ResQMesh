package edu.cit.resqmesh.feature.comms.model

import edu.cit.resqmesh.core.model.ChatMessage
import edu.cit.resqmesh.core.model.ConnectedDevice
import edu.cit.resqmesh.core.model.ScannedDevice
import edu.cit.resqmesh.core.model.KnownNode

data class ChatUiState(
    val publicMessages: List<ChatMessage> = emptyList(),
    val privateMessages: Map<String, List<ChatMessage>> = emptyMap(),
    val connectedDevices: List<ConnectedDevice> = emptyList(),
    val knownNodes: List<KnownNode> = emptyList(),
    val scannedDevices: List<ScannedDevice> = emptyList(),
    val blockedDeviceNames: Set<String> = emptySet(),
    val peerNames: Map<String, String> = emptyMap()
)
