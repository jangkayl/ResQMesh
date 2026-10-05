package com.example.testresqmesh.core.network.bluetooth

import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.network.MeshPayload
import kotlinx.serialization.protobuf.ProtoBuf
import com.example.testresqmesh.core.utils.TerminalLogCategory
import com.example.testresqmesh.core.network.ReliableMeshTransfers
import com.example.testresqmesh.core.network.bluetooth.state.BleStateStore
import com.example.testresqmesh.core.utils.AppLogger
import kotlinx.serialization.encodeToByteArray

/** Owns presence counters only. The facade retains link, session and dispatch ownership. */
internal class BlePresencePublisher(
    private val store: BleStateStore,
    private val updatePeerCount: () -> Unit,
    private val nodeIdForEndpoint: (String) -> String?,
    private val hasReadyEndpoint: (String) -> Boolean,
    private val isDeviceBlocked: (String) -> Boolean,
    private val myDeviceName: () -> String,
    private val myNodeId: () -> String,
    private val publicKey: () -> String,
    private val getMeshProfileTtl: () -> Int,
    private val broadcastPriorityPayload: (ByteArray) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newPulseId: () -> String = { java.util.UUID.randomUUID().toString() }
) {
    fun resetTransport() {
        lastSystemPulseHash = 0
        lastSystemPulseTime = 0L
    }

    private companion object {
        const val FULL_REFRESH_MS = 30_000L
    }

    private var lastSystemPulseHash: Int = 0
    private var lastSystemPulseTime: Long = 0
    // Wall-clock seed keeps versions increasing across ordinary process restarts.
    private var topologySequence: Long = clock()
    private var pingCounter: Int = 0

    fun publish(forceFull: Boolean = false) {
        if (!store.isNodeActive.get()) return
        updatePeerCount()
        try {
            // Build and sort identity pairs together. Independently sorting names and IDs can bind
            // one peer's display name to another peer's stable identity.
            val neighbors = (store.activeConnections.keys + store.activeServerConnections.keys)
                .distinct()
                .mapNotNull { endpoint ->
                    val name = store.connectedEndpointNames[endpoint]
                    val nodeId = nodeIdForEndpoint(endpoint)?.uppercase()
                    if (!hasReadyEndpoint(endpoint) || name.isNullOrBlank() || NodeIdentity.isPlaceholder(name) ||
                        nodeId.isNullOrBlank() || isDeviceBlocked(name)) null
                    else nodeId to name
                }
                .distinctBy { it.first }
                .sortedBy { it.first }
            val connectedNodeIds = neighbors.map { it.first }
            val connectedNodesList = neighbors.map { it.second }
            val currentHash = neighbors.hashCode()
            val now = clock()

            // PING is link liveness only. Refresh the leased topology well before its expiry.
            if (!forceFull && currentHash == lastSystemPulseHash && (now - lastSystemPulseTime < FULL_REFRESH_MS)) {
                pingCounter++
                val payload = MeshPayload(
                    id = "P$pingCounter",
                    type = "PING",
                    senderName = myDeviceName()
                )
                val payloadBytes = ProtoBuf.encodeToByteArray(MeshPayload.serializer(), payload)
                broadcastPriorityPayload(payloadBytes)
                return
            }

            // Topology changed or 30 seconds passed; send a full SYSTEM snapshot.
            lastSystemPulseHash = currentHash
            lastSystemPulseTime = now
            topologySequence++

            val pulseId = newPulseId()
            val payload = MeshPayload(
                id = pulseId,
                type = "SYSTEM",
                senderName = myDeviceName(),
                connectedNodes = connectedNodesList,
                senderNodeId = myNodeId(),
                connectedNodeIds = connectedNodeIds,
                publicKey = publicKey(),
                ttl = getMeshProfileTtl(),
                topologySequence = topologySequence,
                transferProtocol = ReliableMeshTransfers.VERSION
            )
            val payloadBytes = ProtoBuf.encodeToByteArray(MeshPayload.serializer(), payload)
            AppLogger.event(
                category = TerminalLogCategory.SYNC,
                event = "SYSTEM_SENT",
                message = "Sending full SYSTEM pulse to ${store.activeConnections.size + store.activeServerConnections.size} GATT endpoints",
                tag = "BLE_MESH"
            )
            broadcastPriorityPayload(payloadBytes)
        } catch (e: Exception) {
            AppLogger.d("BLE_MESH", "Failed to send system pulse: ${e.message}")
        }
    }
}
