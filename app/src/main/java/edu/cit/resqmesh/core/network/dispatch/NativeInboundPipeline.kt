package edu.cit.resqmesh.core.network.dispatch

import android.os.Handler
import edu.cit.resqmesh.core.model.ConnectedDevice
import edu.cit.resqmesh.core.model.NodeIdentity
import edu.cit.resqmesh.core.logging.trace.TraceDir
import edu.cit.resqmesh.core.logging.trace.TraceDropReason
import edu.cit.resqmesh.core.logging.trace.TraceStage
import edu.cit.resqmesh.core.network.DetachedSender
import edu.cit.resqmesh.core.network.PacketTraceEmitter
import edu.cit.resqmesh.core.network.ReliableMeshTransfers
import edu.cit.resqmesh.core.network.bluetooth.state.BleLinkRole
import edu.cit.resqmesh.core.network.bluetooth.state.BleStateStore
import edu.cit.resqmesh.core.logging.AppLogger
import edu.cit.resqmesh.core.utils.collections.BoundedLruMap
import kotlinx.serialization.ExperimentalSerializationApi

/** Inbound gates run at the same call site; only the existing rename/retirement posts are used. */
@OptIn(ExperimentalSerializationApi::class)
@Suppress("LongParameterList")
internal class NativeInboundPipeline(
    private val store: BleStateStore,
    private val handler: Handler,
    private val peerTransferVersions: BoundedLruMap<String, Int>,
    private val generation: () -> Long,
    private val nodeIdentity: () -> String,
    private val isTransportGenerationCurrent: (Long) -> Boolean,
    private val hasLiveSocket: (String) -> Boolean,
    private val nodeIdForEndpoint: (String) -> String?,
    private val endpointForPeer: (String) -> String?,
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

    /**
     * A digest-verified transfer finished, so it no longer needs its sender's link. Link gates ran on
     * every piece; the block, frame and origin gates still run here. Only MESSAGE and CONVERSATION
     * complete, so identity pulses cannot reach the detached path.
     */
    fun processCompletedTransfer(peer: String, payloadBytes: ByteArray) {
        if (!store.isNodeActive.get() || store.blockedDevices[peer.uppercase()] == true) return
        val live = endpointForPeer(peer)
        if (live != null) { processBinaryPayload(live, payloadBytes); return }
        val endpoint = DetachedSender.endpointOf(peer)
        if (store.guards.admitFrame("$endpoint/frames")) dispatch(endpoint, payloadBytes)
    }

    fun processBinaryPayload(endpointId: String, payloadBytes: ByteArray, transport: String? = null) {
        val capturedGeneration = transportGeneration
        val receivingLinks = BleLinkRole.entries.mapNotNull { store.links.current(endpointId, it) }
        if (!isTransportGenerationCurrent(capturedGeneration) || !hasLiveSocket(endpointId)) {
            AppLogger.d("BLE_MESH", "Dropping payload from unowned endpoint $endpointId")
            PacketTraceEmitter.drop(null, TraceDropReason.UNOWNED_LINK, endpointId, payloadBytes.size, transport)
            return
        }
        try {
            val payload = kotlinx.serialization.protobuf.ProtoBuf.decodeFromByteArray(edu.cit.resqmesh.core.network.MeshPayload.serializer(), payloadBytes)
            if (payload.type in setOf(ReliableMeshTransfers.PIECE, ReliableMeshTransfers.ACK)) {
                val peer = nodeIdForEndpoint(endpointId)?.uppercase() ?: run {
                    PacketTraceEmitter.drop(
                        payload, TraceDropReason.TRANSFER_GUARD, endpointId, payloadBytes.size, transport
                    )
                    return
                }
                if (!hasReadyEndpoint(endpointId) || !store.links.isIdentityAdmitted(endpointId) ||
                    isDeviceBlocked(store.connectedEndpointNames[endpointId].orEmpty()) ||
                    peerTransferVersions[peer] != ReliableMeshTransfers.VERSION ||
                    payload.targetNodeId.isNotBlank() && !payload.targetNodeId.equals(myNodeId, true)) {
                    PacketTraceEmitter.drop(
                        payload, TraceDropReason.TRANSFER_GUARD, endpointId, payloadBytes.size, transport
                    )
                    return
                }
                PacketTraceEmitter.emit(payload, TraceStage.RECEIVED, TraceDir.IN, payloadBytes.size, transport,
                    selfId = myNodeId)
                reliableTransfers.receive(peer, payload)
                return
            }
            // PIECE/ACK frames returned above: a 2 MiB transfer is ~2048 one-KiB pieces, far above 64 frames/s,
            // and their volume is already bounded by the bytes/s budget and the transfer window.
            if (!store.guards.admitFrame("$endpointId/frames")) {
                PacketTraceEmitter.drop(payload, TraceDropReason.FRAME_RATE, endpointId, payloadBytes.size, transport)
                return
            }
            PacketTraceEmitter.emit(payload, TraceStage.RECEIVED, TraceDir.IN, payloadBytes.size, transport,
                selfId = myNodeId)
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
                val claimedId = edu.cit.resqmesh.core.network.bluetooth.DirectIdentityPolicy.validate(payload) {
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
                            edu.cit.resqmesh.core.model.ConnectedDevice(
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
            AppLogger.d("BLE_MESH", "Failed to decode payload for auto-rename: ${e.javaClass.simpleName}")
        }

        dispatch(endpointId, payloadBytes)
    }
}
