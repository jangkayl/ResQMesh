package edu.cit.resqmesh.core.network.bluetooth.session

import android.annotation.SuppressLint
import android.bluetooth.*
import android.os.Handler
import edu.cit.resqmesh.core.network.bluetooth.state.HeartbeatCoordinator
import edu.cit.resqmesh.core.logging.AppLogger
import java.util.UUID
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import edu.cit.resqmesh.core.network.bluetooth.session.BleTransportResources
import edu.cit.resqmesh.core.network.bluetooth.session.BleHeartbeatDriver
import edu.cit.resqmesh.core.network.MeshPayload
import edu.cit.resqmesh.core.network.bluetooth.BleConfig
import edu.cit.resqmesh.core.network.bluetooth.state.BleStateStore
import edu.cit.resqmesh.core.network.bluetooth.state.BleLink
import edu.cit.resqmesh.core.network.bluetooth.state.HeartbeatChallenge

@SuppressLint("MissingPermission")
internal class BleHeartbeatDriver(
    private val store: BleStateStore,
    private val handler: Handler,
    private val heartbeatCoordinator: HeartbeatCoordinator,
    private val heartbeatAckTimeoutMs: Long,
    private val readyGattLink: (String) -> BleLink?,
    private val enqueueGattPayload: (String, ByteArray, Boolean, String?) -> Boolean,
    private val forceGattDisconnect: (String, BluetoothGatt?) -> Unit,
    private val resources: BleTransportResources,
    private val deviceName: () -> String
) {
    /** Called on the first missed ACK of a link, so the owner can ask for a more robust PHY. */
    var onLinkStruggling: (String) -> Unit = {}

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

    fun markHeartbeatSent(endpoint: String, link: edu.cit.resqmesh.core.network.bluetooth.state.BleLink, id: String) {
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
                heartbeatCoordinator.resetMisses(endpoint)
                return
            }
            if (store.activeL2capSockets.containsKey(endpoint)) {
                heartbeatCoordinator.remove(endpoint, sent)
                return
            }
            if (edu.cit.resqmesh.core.network.bluetooth.state.BleLivenessPolicy.hasRecentOutboundProgress(
                    current.lastAcknowledgedWriteAt, System.currentTimeMillis())) {
                handler.postDelayed({ checkAck() }, heartbeatAckTimeoutMs)
                return
            }
            if (sent.expired(System.currentTimeMillis(), heartbeatAckTimeoutMs)) onAckMissed(endpoint, sent)
        }
        handler.postDelayed({ checkAck() }, heartbeatAckTimeoutMs)
    }

    /**
     * A weak edge link can black out briefly (a body or door in the path) and recover, so a missed
     * ACK re-challenges up to [BleConfig.HEARTBEAT_MAX_MISSES] times before giving up the slot.
     */
    private fun onAckMissed(endpoint: String, sent: HeartbeatChallenge) {
        heartbeatCoordinator.remove(endpoint, sent)
        val misses = heartbeatCoordinator.recordMiss(endpoint, sent.generation)
        val max = BleConfig.HEARTBEAT_MAX_MISSES
        if (misses < max) {
            AppLogger.d("BLE_MESH", "Link ${sent.generation} $endpoint: heartbeat ACK missed ($misses/$max); retrying")
            if (misses == 1) onLinkStruggling(endpoint)
            startHeartbeatChallenge(endpoint)
            return
        }
        heartbeatCoordinator.resetMisses(endpoint)
        AppLogger.d("BLE_MESH", "Link ${sent.generation} $endpoint: heartbeat ACK missed ($misses/$max); retiring")
        store.activeConnections[endpoint]?.let { forceGattDisconnect(endpoint, it) }
        store.activeServerConnections[endpoint]?.let { gattServer?.cancelConnection(it) }
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
