package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.network.BlockControlEnvelope
import com.example.testresqmesh.core.network.MeshNetworkGateway
import com.example.testresqmesh.core.network.MeshPayload
import com.example.testresqmesh.core.network.OutboundFrameEvent
import com.example.testresqmesh.core.network.bluetooth.MeshTransportState
import com.example.testresqmesh.core.model.ScanEvent

/** One synchronous binding pass from MeshRepository.init; no session or policy ownership. */
internal fun bindMeshNetworkEvents(gateway: MeshNetworkGateway, callbacks: MeshNetworkCallbacks) {
    gateway.onStatusChanged = callbacks.onStatusChanged
    gateway.onOutboundFrame = callbacks.onOutboundFrame
    gateway.onTransportStateChanged = callbacks.onTransportStateChanged
    gateway.onDeviceConnected = callbacks.onDeviceConnected
    gateway.onDeviceDisconnected = callbacks.onDeviceDisconnected
    gateway.onDeviceLivenessChanged = callbacks.onDeviceLivenessChanged
    gateway.onPublicKeyReceived = callbacks.onPublicKeyReceived
    gateway.onRoutingTableReceived = callbacks.onRoutingTableReceived
    gateway.onDeviceBlocked = callbacks.onDeviceBlocked
    gateway.onDeviceUnblocked = callbacks.onDeviceUnblocked
    gateway.onBlockRequest = callbacks.onBlockRequest
    gateway.onBlockAck = callbacks.onBlockAck
    gateway.checkRouteExists = callbacks.checkRouteExists
    gateway.canRetireForBridge = callbacks.canRetireForBridge
    gateway.onDeviceScanned = callbacks.onDeviceScanned
    gateway.onDeviceScanRemoved = callbacks.onDeviceScanRemoved
    gateway.onSosCancelled = callbacks.onSosCancelled
    gateway.onConversationMessage = callbacks.onConversationMessage
    gateway.stpNeighborsProvider = callbacks.stpNeighborsProvider
    gateway.onLiveAudioChunk = callbacks.onLiveAudioChunk
    gateway.onMessageReceived = callbacks.onMessageReceived
    gateway.onMessageDelivered = callbacks.onMessageDelivered
    gateway.onMessageSeen = callbacks.onMessageSeen
}

internal class MeshNetworkCallbacks(
    val onStatusChanged: (String) -> Unit,
    val onOutboundFrame: (OutboundFrameEvent) -> Unit,
    val onTransportStateChanged: (MeshTransportState) -> Unit,
    val onDeviceConnected: (ConnectedDevice) -> Unit,
    val onDeviceDisconnected: (String) -> Unit,
    val onDeviceLivenessChanged: (String, Boolean) -> Unit,
    val onPublicKeyReceived: (String, String, String) -> Unit,
    val onRoutingTableReceived: (String, String, List<String>, List<String>, Long) -> Unit,
    val onDeviceBlocked: (String) -> Unit,
    val onDeviceUnblocked: (String) -> Unit,
    val onBlockRequest: (String, MeshPayload, BlockControlEnvelope) -> Unit,
    val onBlockAck: (String, MeshPayload, BlockControlEnvelope) -> Unit,
    val checkRouteExists: (String) -> Boolean,
    val canRetireForBridge: (String) -> Boolean,
    val onDeviceScanned: (ScanEvent) -> Unit,
    val onDeviceScanRemoved: (String) -> Unit,
    val onSosCancelled: () -> Unit,
    val onConversationMessage: (String, MeshPayload) -> Unit,
    val stpNeighborsProvider: () -> Set<String>,
    val onLiveAudioChunk: (String, String, ByteArray) -> Unit,
    val onMessageReceived: (String, String, String, String, Boolean, Boolean, String?, String?, Double?, Double?, String, List<String>, String) -> Unit,
    val onMessageDelivered: (String, String, List<String>) -> Unit,
    val onMessageSeen: (String, String) -> Unit
)
