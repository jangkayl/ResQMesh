package com.example.testresqmesh.core.network.bluetooth.peers

import com.example.testresqmesh.core.network.transport.NativeOutboundDispatcher
import android.annotation.SuppressLint
import android.bluetooth.*
import com.example.testresqmesh.core.network.bluetooth.BleSessionLifecycle
import android.os.Handler
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.network.bluetooth.BlePeerAdmissionController
import com.example.testresqmesh.core.network.bluetooth.BleRadioController
import com.example.testresqmesh.core.network.bluetooth.L2capTransport
import com.example.testresqmesh.core.network.bluetooth.state.BleLinkRole
import com.example.testresqmesh.core.network.bluetooth.state.GattTransferCoordinator
import com.example.testresqmesh.core.network.bluetooth.state.HeartbeatCoordinator
import com.example.testresqmesh.core.network.bluetooth.state.payloadBytes
import com.example.testresqmesh.core.utils.AppLogger
import com.example.testresqmesh.core.network.bluetooth.peers.BlePeerDirectory
import com.example.testresqmesh.core.network.bluetooth.peers.BleLinkRetirementController
import com.example.testresqmesh.core.network.bluetooth.session.BleTransportResources
import com.example.testresqmesh.core.network.OutboundFrameEvent
import com.example.testresqmesh.core.network.ReliableMeshTransfers
import com.example.testresqmesh.core.network.bluetooth.state.BleStateStore
import com.example.testresqmesh.core.network.bluetooth.state.BleLink

@SuppressLint("MissingPermission")
internal class BleLinkRetirementController(
    private val store: BleStateStore,
    private val handler: Handler,
    private val resources: BleTransportResources,
    private val directory: BlePeerDirectory,
    private val outboundDispatcher: NativeOutboundDispatcher,
    private val transfers: () -> ReliableMeshTransfers,
    private val l2capTransport: L2capTransport,
    private val transferCoordinator: GattTransferCoordinator,
    private val heartbeatCoordinator: HeartbeatCoordinator,
    private val radioController: BleRadioController,
    private val peerAdmissionController: BlePeerAdmissionController,
    private val session: () -> BleSessionLifecycle,
    private val finishRadioHandshake: (String, String) -> Unit,
    private val releaseConnectLock: (String?, String, Boolean) -> Unit,
    private val scheduleAdvertisingUpdate: () -> Unit,
    private val renewIdleGattServerIfNeeded: () -> Unit,
    private val sendSystemPulse: () -> Unit,
    private val frame: (String, ByteArray, OutboundFrameEvent.Stage, String) -> Unit,
    private val disconnected: () -> ((String) -> Unit)?
) {
    private val reliableTransfers get() = transfers()
    private val sessionLifecycle get() = session()
    private val gattServer get() = resources.gattServer
    private val onDeviceDisconnected get() = disconnected()
    private fun hasLiveSocket(endpoint: String) = directory.hasLiveSocket(endpoint)
    private fun hasReadyEndpoint(endpoint: String) = directory.hasReadyEndpoint(endpoint)
    private fun nodeIdForEndpoint(endpoint: String) = directory.nodeIdForEndpoint(endpoint)
    private fun distinctReadyLinkCount() = directory.distinctReadyLinkCount()
    private fun reportFrame(endpoint: String, bytes: ByteArray, stage: OutboundFrameEvent.Stage, transport: String) = frame(endpoint, bytes, stage, transport)
    fun cleanupEndpointIfUnowned(endpointId: String) {
        if (hasLiveSocket(endpointId)) return
        outboundDispatcher.retireLegacyFrames(endpointId)
        nodeIdForEndpoint(endpointId)?.uppercase()?.let { reliableTransfers.wake(it) }
        l2capTransport.disconnect(endpointId)
        outboundDispatcher.retireFallback(endpointId)
        store.gattFlights[endpointId]?.transfer?.let { reportFrame(endpointId, it.payloadBytes(), OutboundFrameEvent.Stage.FAILED, "GATT") }
        store.pendingQueues[endpointId]?.forEach { reportFrame(endpointId, it.payloadBytes(), OutboundFrameEvent.Stage.FAILED, "GATT") }
        transferCoordinator.forget(endpointId)
        store.pendingQueues.remove(endpointId)
        store.gattFlights.remove(endpointId)?.writing?.set(false)
        heartbeatCoordinator.remove(endpointId)
        store.isWriting.remove(endpointId)
        store.chunkBuffers.remove(endpointId)
        store.connectionMtu.remove(endpointId)
        store.writeFailureCount.remove(endpointId)
        store.connectionInteractionTimes.remove(endpointId)
        AppLogger.d("BLE_MESH", "Retired unowned endpoint transport $endpointId")
        scheduleAdvertisingUpdate()
        handler.post {
            if (!store.isNodeActive.get()) return@post
            sessionLifecycle.peers(distinctReadyLinkCount())
            radioController.reconcileHandshakeOwners(store.links::isSetupOwner)
            radioController.startScanning()
            peerAdmissionController.recover()
            renewIdleGattServerIfNeeded()
        }
    }

    fun retireServerLink(link: com.example.testresqmesh.core.network.bluetooth.state.BleLink) {
        if (!store.retireServerOwnership(link) { finishRadioHandshake(it, "server transfer retirement") }) return
        AppLogger.removeLink(link.endpoint, "SERVER", link.generation)
        if (!store.links.hasLiveRole(link.endpoint, BleLinkRole.CLIENT)) {
            link.serverDevice?.let { try { gattServer?.cancelConnection(it) } catch (_: SecurityException) {} }
        }
        cleanupEndpointIfUnowned(link.endpoint)
        if (!hasLiveSocket(link.endpoint)) {
            store.connectedEndpointIds.remove(link.endpoint)
            store.connectedEndpointNames.remove(link.endpoint)
            store.endpointNodeIds.remove(link.endpoint)
            store.connectionEstablishTime.remove(link.endpoint)
        }
        if (!hasReadyEndpoint(link.endpoint)) onDeviceDisconnected?.invoke(link.endpoint)
        sendSystemPulse()
        renewIdleGattServerIfNeeded()
    }

    fun forceGattDisconnect(macAddress: String, gatt: BluetoothGatt?) {
        val link = store.links.current(macAddress, BleLinkRole.CLIENT)
        if (gatt != null && link?.gatt != null && link.gatt !== gatt) {
            AppLogger.d("BLE_MESH", "Ignoring forced cleanup from stale CLIENT GATT on $macAddress")
            try { gatt.close() } catch (e: Exception) {}
            return
        }
        if (link != null) {
            store.links.retire(link) { finishRadioHandshake(it, "forced client retirement") }
        }
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (e: Exception) { }

        if (gatt != null) store.activeConnections.remove(macAddress, gatt)
        else store.activeConnections.remove(macAddress)
        cleanupEndpointIfUnowned(macAddress)
        if (!store.activeServerConnections.containsKey(macAddress)) {
            store.connectedEndpointIds.remove(macAddress)
            store.connectedEndpointNames.remove(macAddress)
            store.endpointNodeIds.remove(macAddress)
        }
        if (!store.activeServerConnections.containsKey(macAddress)) {
            store.connectionEstablishTime.remove(macAddress)
        }
        releaseConnectLock(macAddress, "force disconnect", false)

        handler.post {
            if (!hasReadyEndpoint(macAddress)) onDeviceDisconnected?.invoke(macAddress)
            sendSystemPulse()
        }
    }

    fun disconnectFromEndpoint(endpointId: String) {
        listOf(
            BleLinkRole.CLIENT,
            BleLinkRole.SERVER
        ).forEach { role ->
            store.links.current(endpointId, role)?.let { link ->
                store.links.retire(link) { finishRadioHandshake(it, "endpoint retirement") }
            }
        }
        val gatt = store.activeConnections.remove(endpointId)
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (e: Exception) {}

        val serverDevice = store.activeServerConnections.remove(endpointId)
        serverDevice?.let { device ->
            try {
                gattServer?.cancelConnection(device)
            } catch (e: Exception) {}
        }

        l2capTransport.disconnect(endpointId)

        store.connectionEstablishTime.remove(endpointId)
        store.pendingQueues.remove(endpointId)
        store.gattFlights.remove(endpointId)?.writing?.set(false)
        heartbeatCoordinator.remove(endpointId)
        store.isWriting.remove(endpointId)
        store.chunkBuffers.remove(endpointId)
        store.connectionMtu.remove(endpointId)
        store.connectionInteractionTimes.remove(endpointId)
        store.writeFailureCount.remove(endpointId)
        store.connectedEndpointIds.remove(endpointId)
        store.connectedEndpointNames.remove(endpointId)
        store.endpointNodeIds.remove(endpointId)
        releaseConnectLock(endpointId, "endpoint disconnected", false)
        if (gatt != null || serverDevice != null) {
            handler.post {
                onDeviceDisconnected?.invoke(endpointId)
                sendSystemPulse()
            }
        }
        scheduleAdvertisingUpdate()
        handler.post {
            if (store.isNodeActive.get()) {
                sessionLifecycle.peers(distinctReadyLinkCount())
                radioController.startScanning()
                peerAdmissionController.recover()
            }
        }
    }

    fun denyDirectIdentity(deviceName: String) {
        store.blockedDevices[NodeIdentity.key(deviceName)] = true
    }

    fun releaseDirectIdentity(deviceName: String) {
        store.blockedDevices.remove(NodeIdentity.key(deviceName))
        store.blockedDevices.keys
            .filter { NodeIdentity.matches(it, deviceName) }
            .forEach { store.blockedDevices.remove(it) }
    }

    fun disconnectDirectIdentity(deviceName: String, reason: String = "direct identity denied") {
        val nodeId = NodeIdentity.idOf(deviceName)
        val endpoints = (store.activeConnections.keys + store.activeServerConnections.keys)
            .filter { endpoint ->
                NodeIdentity.matches(store.connectedEndpointNames[endpoint], deviceName) ||
                    (nodeId != null && nodeIdForEndpoint(endpoint) == nodeId)
            }
            .toSet()
        endpoints.forEach { endpoint ->
            AppLogger.d("BLE_MESH", "Disconnecting $endpoint for $deviceName ($reason)")
            disconnectFromEndpoint(endpoint)
        }
    }

    fun isDeviceBlocked(deviceName: String): Boolean {
        if (store.blockedDevices[NodeIdentity.key(deviceName)] == true) return true
        return store.blockedDevices.keys.any { key ->
            store.blockedDevices[key] == true && NodeIdentity.matches(key, deviceName)
        }
    }
}
