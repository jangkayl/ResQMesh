package com.example.testresqmesh.core.network.dispatch

import android.os.Handler
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.network.ReliableMeshTransfers
import com.example.testresqmesh.core.network.bluetooth.state.BleLinkRole
import com.example.testresqmesh.core.network.bluetooth.state.BleStateStore
import com.example.testresqmesh.core.utils.AppLogger
import kotlinx.serialization.ExperimentalSerializationApi

/** Inbound gates run at the same call site; only the existing rename/retirement posts are used. */
@OptIn(ExperimentalSerializationApi::class)
internal class NativeInboundPipeline(
    private val store: BleStateStore,
    private val handler: Handler,
    private val peerTransferVersions: MutableMap<String, Int>,
    private val generation: () -> Long,
    private val nodeIdentity: () -> String,
    private val isTransportGenerationCurrent: (Long) -> Boolean,
    private val hasLiveSocket: (String) -> Boolean,
    private val nodeIdForEndpoint: (String) -> String?,
    private val hasReadyEndpoint: (String) -> Boolean,
    private val isDeviceBlocked: (String) -> Boolean,
    private val transfers: () -> ReliableMeshTransfers,
    private val removeHeartbeat: (String) -> Unit,
    private val disconnectFromEndpoint: (String) -> Unit,
    private val disconnectDirectIdentity: (String, String) -> Unit,
    private val sendSystemPulse: () -> Unit,
    private val connectedListener: () -> ((ConnectedDevice) -> Unit)?,
    private val livenessListener: () -> ((String, Boolean) -> Unit)?,
    private val dispatch: (String, ByteArray) -> Unit
) {
    private val transportGeneration get() = generation()
    private val myNodeId get() = nodeIdentity()
    private val reliableTransfers get() = transfers()
    private val onDeviceConnected get() = connectedListener()
    private val onDeviceLivenessChanged get() = livenessListener()

    fun processBinaryPayload(endpointId: String, payloadBytes: ByteArray) {
        val capturedGeneration = transportGeneration
        val receivingLinks = BleLinkRole.entries.mapNotNull { store.links.current(endpointId, it) }
        if (!isTransportGenerationCurrent(capturedGeneration) || !hasLiveSocket(endpointId)) {
            AppLogger.d("BLE_MESH", "Dropping payload from unowned endpoint $endpointId")
            return
        }
        try {
            val payload = kotlinx.serialization.protobuf.ProtoBuf.decodeFromByteArray(com.example.testresqmesh.core.network.MeshPayload.serializer(), payloadBytes)
            if (payload.type in setOf(ReliableMeshTransfers.PIECE, ReliableMeshTransfers.ACK)) {
                val peer = nodeIdForEndpoint(endpointId)?.uppercase() ?: return
                if (!hasReadyEndpoint(endpointId) || !store.links.isIdentityAdmitted(endpointId) ||
                    isDeviceBlocked(store.connectedEndpointNames[endpointId].orEmpty()) ||
                    peerTransferVersions[peer] != ReliableMeshTransfers.VERSION ||
                    payload.targetNodeId.isNotBlank() && !payload.targetNodeId.equals(myNodeId, true)) return
                reliableTransfers.receive(peer, payload)
                return
            }
            onDeviceLivenessChanged?.invoke(endpointId, true)
            if (payload.type != "PONG") removeHeartbeat(endpointId)

            // Inbound central addresses are often unknown at ACL creation. A direct SYSTEM pulse is
            // the first reliable endpoint-to-stable-identity binding; reject it before the peer is
            // published or normal traffic is dispatched. Relayed packets never take this path.
            val isDirectIdentityPulse = payload.type == "SYSTEM" && payload.routePath.isEmpty() && payload.senderName.isNotEmpty()
            if (isDirectIdentityPulse && isDeviceBlocked(payload.senderName)) {
                AppLogger.d("BLE_MESH", "Identity gate rejected direct blocked peer ${payload.senderName} on $endpointId")
                val rejectedLinks = BleLinkRole.entries.mapNotNull { store.links.current(endpointId, it) }
                handler.post {
                    if (!isTransportGenerationCurrent(capturedGeneration)) return@post
                    // The receiving socket can still be Unknown Node. Retire it by captured ownership,
                    // never by the unbound name, and never retire a replacement on the same endpoint.
                    if (store.links.ownsEndpoint(endpointId, rejectedLinks)) disconnectFromEndpoint(endpointId)
                    disconnectDirectIdentity(payload.senderName, "identity gate")
                }
                return
            }

            // Only auto-rename the physical socket if this is a direct message (not relayed).
            // Relayed payloads carry a non-empty routePath; renaming from those would map a remote
            // node onto a local socket and corrupt the routing table.
            if (isDirectIdentityPulse) {
                val claimedId = com.example.testresqmesh.core.network.bluetooth.DirectIdentityPolicy.validate(payload) {
                    android.util.Base64.decode(it, android.util.Base64.DEFAULT)
                } ?: return
                receivingLinks.forEach { it.identityAdmitted = true }
                peerTransferVersions[claimedId] = payload.transferProtocol
                reliableTransfers.wake(claimedId, reconcile = false)
                val oldName = store.connectedEndpointNames[endpointId]
                if (NodeIdentity.isPlaceholder(oldName) || !NodeIdentity.matches(oldName, payload.senderName)) {
                    AppLogger.d("BLE_MESH", "Auto-rename: $endpointId is now ${payload.senderName}")
                    store.connectedEndpointNames[endpointId] = payload.senderName
                    NodeIdentity.idOf(payload.senderName)?.let { nodeId ->
                        store.endpointNodeIds[endpointId] = nodeId
                        listOf(
                            BleLinkRole.CLIENT,
                            BleLinkRole.SERVER
                        ).forEach { role ->
                            store.links.current(endpointId, role)?.peerNodeId = nodeId
                        }
                    }
                    handler.post {
                        if (!isTransportGenerationCurrent(capturedGeneration) ||
                            !store.links.ownsEndpoint(endpointId, receivingLinks)) return@post
                        val isDirectlyConnected = store.activeConnections.containsKey(endpointId) || store.activeServerConnections.containsKey(endpointId)
                        onDeviceConnected?.invoke(
                            com.example.testresqmesh.core.model.ConnectedDevice(
                                endpointId = endpointId,
                                name = payload.senderName,
                                isClassicConnected = isDirectlyConnected,
                                isProvisional = false,
                                nodeId = NodeIdentity.idOf(payload.senderName) ?: store.endpointNodeIds[endpointId].orEmpty(),
                                isPayloadReady = store.links.isReady(endpointId)
                            )
                        )
                        sendSystemPulse()
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.d("BLE_MESH", "Failed to decode payload for auto-rename: ${e.message}")
        }

        dispatch(endpointId, payloadBytes)
    }
}
