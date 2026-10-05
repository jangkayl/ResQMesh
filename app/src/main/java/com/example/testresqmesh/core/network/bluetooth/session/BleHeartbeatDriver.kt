package com.example.testresqmesh.core.network.bluetooth.session

import android.annotation.SuppressLint
import android.bluetooth.*
import android.os.Handler
import com.example.testresqmesh.core.network.bluetooth.state.HeartbeatCoordinator
import com.example.testresqmesh.core.utils.AppLogger
import java.util.UUID
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import com.example.testresqmesh.core.network.bluetooth.session.BleTransportResources
import com.example.testresqmesh.core.network.bluetooth.session.BleHeartbeatDriver
import com.example.testresqmesh.core.network.MeshPayload
import com.example.testresqmesh.core.network.bluetooth.state.BleStateStore
import com.example.testresqmesh.core.network.bluetooth.state.BleLink

@SuppressLint("MissingPermission")
internal class BleHeartbeatDriver(
    private val store: BleStateStore,
    private val handler: Handler,
    private val heartbeatCoordinator: HeartbeatCoordinator,
    private val HEARTBEAT_ACK_TIMEOUT_MS: Long,
    private val readyGattLink: (String) -> BleLink?,
    private val enqueueGattPayload: (String, ByteArray, Boolean, String?) -> Boolean,
    private val forceGattDisconnect: (String, BluetoothGatt?) -> Unit,
    private val resources: BleTransportResources,
    private val deviceName: () -> String
) {
    private val myDeviceName get() = deviceName()
    private val gattServer get() = resources.gattServer
    fun startHeartbeatChallenge(endpoint: String) {
        if (!store.isNodeActive.get() || heartbeatCoordinator.contains(endpoint)) return
        val link = readyGattLink(endpoint) ?: return
        val id = "HB:${UUID.randomUUID()}"
        val challenge = heartbeatCoordinator.begin(endpoint, link.role, link.generation, id) ?: return
        val payload = MeshPayload(id = id, type = "PING", senderName = myDeviceName)
        if (!enqueueGattPayload(endpoint, ProtoBuf.encodeToByteArray(payload), true, id)) {
            heartbeatCoordinator.remove(endpoint, challenge)
        } else {
            AppLogger.d("BLE_MESH", "Link ${link.generation} $endpoint: queued GATT heartbeat challenge")
        }
    }

    fun markHeartbeatSent(endpoint: String, link: com.example.testresqmesh.core.network.bluetooth.state.BleLink, id: String) {
        if (!store.links.isCurrent(link)) return
        val sent = heartbeatCoordinator.markSent(endpoint, link.generation, id) ?: return
        fun checkAck() {
            if (heartbeatCoordinator.pending(endpoint) != sent) return
            val current = store.links.current(endpoint, sent.role)
            if (current == null || current.generation != sent.generation) {
                heartbeatCoordinator.remove(endpoint, sent)
                return
            }
            if (store.connectionInteractionTimes[endpoint]?.let { it > sent.sentAt } == true) {
                heartbeatCoordinator.remove(endpoint, sent)
                return
            }
            if (store.activeL2capSockets.containsKey(endpoint)) {
                heartbeatCoordinator.remove(endpoint, sent)
                return
            }
            if (com.example.testresqmesh.core.network.bluetooth.state.BleLivenessPolicy.hasRecentOutboundProgress(
                    current.lastAcknowledgedWriteAt, System.currentTimeMillis())) {
                handler.postDelayed({ checkAck() }, HEARTBEAT_ACK_TIMEOUT_MS)
                return
            }
            if (sent.expired(System.currentTimeMillis(), HEARTBEAT_ACK_TIMEOUT_MS)) {
                heartbeatCoordinator.remove(endpoint, sent)
                AppLogger.d("BLE_MESH", "Link ${sent.generation} $endpoint: GATT heartbeat ACK timed out; retiring silent roles")
                store.activeConnections[endpoint]?.let { forceGattDisconnect(endpoint, it) }
                store.activeServerConnections[endpoint]?.let { gattServer?.cancelConnection(it) }
            }
        }
        handler.postDelayed({ checkAck() }, HEARTBEAT_ACK_TIMEOUT_MS)
    }

    fun onHeartbeatAck(endpoint: String, id: String) {
        handler.post {
            val pending = heartbeatCoordinator.pending(endpoint) ?: return@post
            val current = store.links.current(endpoint, pending.role) ?: return@post
            if (store.links.isCurrent(current) && heartbeatCoordinator.acknowledge(endpoint, id, current.generation)) {
                AppLogger.d("BLE_MESH", "Link ${current.generation} $endpoint: GATT heartbeat acknowledged")
            }
        }
    }
}
