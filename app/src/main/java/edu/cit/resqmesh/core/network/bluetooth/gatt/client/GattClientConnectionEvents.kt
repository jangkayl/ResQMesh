package edu.cit.resqmesh.core.network.bluetooth.gatt.client

import edu.cit.resqmesh.core.network.bluetooth.BleConfig
import android.bluetooth.*
import edu.cit.resqmesh.core.model.ConnectedDevice
import edu.cit.resqmesh.core.model.NodeIdentity
import edu.cit.resqmesh.core.network.bluetooth.state.BleLinkState
import edu.cit.resqmesh.core.logging.AppLogger
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import edu.cit.resqmesh.core.network.bluetooth.gatt.GattClientHost

internal class GattClientConnectionEvents(
    override val attempt: GattClientAttempt
) : GattClientHost by attempt, GattClientAttemptContext {
    fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        handler.post {
            if (!isTransportGenerationCurrent(epoch)) return@post
            handleConnectionStateChange(gatt, status, newState)
        }
    }

    private fun handleConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        if (isConnectPermissionRevoked()) {
            abortForRevokedPermission(
                "Ignoring CLIENT connection callback for $peerName: BLUETOOTH_CONNECT was revoked"
            )
            return
        }
        if (!owns(gatt, "connection state $newState/$status")) {
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                closeQuietly(gatt, "Could not close stale CLIENT link: BLUETOOTH_CONNECT was revoked")
            }
            return
        }
        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> onLinkConnected(gatt, status)
            BluetoothProfile.STATE_DISCONNECTED -> onLinkDisconnected(gatt)
        }
    }

    private fun onLinkConnected(gatt: BluetoothGatt, status: Int) {
        if (!store.links.transition(link, BleLinkState.DISCOVERING)) return
        AppLogger.d("BLE_MESH", "Link ${link.generation} CLIENT $macAddress: DISCOVERING radioStatus=$status")
        AppLogger.d("BLE_MESH", "GATT Socket locked with ${peerName}. Starting service discovery with default MTU.")
        registerConnectedEndpoint(gatt)
        armHandshakeWatchdog(gatt)

        handler.post {
            if (!isTransportGenerationCurrent(epoch)) return@post
            onDeviceConnected?.invoke(
                ConnectedDevice(
                    endpointId = macAddress,
                    name = peerName,
                    isClassicConnected = true,
                    isProvisional = false,
                    nodeId = NodeIdentity.idOf(peerName) ?: store.endpointNodeIds[macAddress].orEmpty(),
                    isPayloadReady = false
                )
            )
        }
        try {
            gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
        } catch (e: SecurityException) {
            abortForRevokedPermission("CLIENT connection priority skipped: BLUETOOTH_CONNECT was revoked")
            return
        }
        // Default ATT payload size is reliable on every supported Android version.
        // Negotiate no larger MTU until the link is READY and the setup queue is idle.
        store.connectionMtu[macAddress] = BleConfig.DEFAULT_ATT_PAYLOAD_BYTES
        link.mtu = BleConfig.DEFAULT_ATT_PAYLOAD_BYTES
        requestServicesOnce(gatt)
    }

    private fun registerConnectedEndpoint(gatt: BluetoothGatt) {
        store.activeConnections[macAddress] = gatt
        manager.scheduleAdvertisingUpdate()
        store.connectedEndpointNames[macAddress] = peerName
        NodeIdentity.idOf(peerName)?.let { store.endpointNodeIds[macAddress] = it }
        store.pendingQueues.putIfAbsent(macAddress, ConcurrentLinkedDeque())
        store.isWriting.putIfAbsent(macAddress, AtomicBoolean(false))
        store.chunkBuffers.putIfAbsent(macAddress, ByteArray(0))
        store.connectionInteractionTimes.putIfAbsent(macAddress, System.currentTimeMillis())
        store.connectionEstablishTime[macAddress] = System.currentTimeMillis()
    }

    /**
     * The connect timeout is swapped for a handshake watchdog that always fires,
     * so a dropped OEM discovery/descriptor callback cannot strand the lock.
     */
    private fun armHandshakeWatchdog(gatt: BluetoothGatt) {
        timeoutHandler.removeCallbacks(connectTimeoutRunnable)
        store.connectTimeouts.onConnected(connectTimeoutKey)
        timeoutHandler.postDelayed({
            if (store.links.isCurrent(link) && link.state != BleLinkState.READY) {
                if (link.state == BleLinkState.DISCOVERING && stablePeerId != null &&
                    store.explicitLeTransportPeers.add(stablePeerId)) {
                    AppLogger.d(
                        "BLE_MESH",
                        "AUTO transport received no ATT discovery response from $peerName; next attempt will force LE"
                    )
                }
                AppLogger.d("BLE_MESH", "Handshake watchdog fired for CLIENT link ${link.generation} $peerName in ${link.state}. Disconnecting.")
                finishConnectPhase("handshake watchdog")
                forceGattDisconnect(macAddress, gatt)
            }
        }, BleConfig.HANDSHAKE_WATCHDOG_MS)
    }

    private fun onLinkDisconnected(gatt: BluetoothGatt) {
        val stateBeforeDisconnect = link.state
        if (useExplicitLeTransport && stateBeforeDisconnect == BleLinkState.CONNECTING &&
            store.explicitLeTransportPeers.remove(stablePeerId)) {
            AppLogger.d(
                "BLE_MESH",
                "Explicit LE disconnected before setup for $peerName; restoring AUTO transport"
            )
        }
        store.links.transition(link, BleLinkState.DISCONNECTING)
        AppLogger.d("BLE_MESH", "GATT Socket disconnected from ${peerName}.")
        finishConnectPhase("disconnected")
        store.activeConnections.remove(macAddress, gatt)
        cleanupEndpointIfUnowned(macAddress)
        if (!store.activeServerConnections.containsKey(macAddress)) {
            store.connectionEstablishTime.remove(macAddress)
        }
        if (!store.activeServerConnections.containsKey(macAddress)) {
            store.connectedEndpointIds.remove(macAddress)
            store.connectedEndpointNames.remove(macAddress)
        }

        handler.post {
            if (!isTransportGenerationCurrent(epoch)) return@post
            onDeviceDisconnected?.invoke(macAddress)
            sendSystemPulse()
        }
        closeQuietly(gatt, "Could not close CLIENT link: BLUETOOTH_CONNECT was revoked")
        store.links.forget(link)
    }

    private fun closeQuietly(gatt: BluetoothGatt, revokedMessage: String) {
        try {
            gatt.close()
        } catch (e: SecurityException) {
            AppLogger.d("BLE_MESH", revokedMessage)
        }
    }
}
