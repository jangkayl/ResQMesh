package com.example.testresqmesh.core.network.bluetooth.gatt.client

import android.bluetooth.*
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.network.bluetooth.state.BleLinkState
import com.example.testresqmesh.core.utils.AppLogger
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import com.example.testresqmesh.core.network.bluetooth.gatt.GattClientHost

internal class GattClientConnectionEvents(
    override val attempt: GattClientAttempt
) : GattClientHost by attempt, GattClientAttemptContext {
    fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        handler.post {
            if (!isTransportGenerationCurrent(epoch)) return@post
            fun handleCallback() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                ) {
                    AppLogger.d("BLE_MESH", "Ignoring CLIENT connection callback for $peerName: BLUETOOTH_CONNECT was revoked")
                    finishConnectPhase("Bluetooth permission revoked")
                    store.links.transition(link, BleLinkState.DISCONNECTING)
                    store.links.forget(link)
                    return
                }
                if (!owns(gatt, "connection state $newState/$status")) {
                    if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        try {
                            gatt.close()
                        } catch (e: SecurityException) {
                            AppLogger.d("BLE_MESH", "Could not close stale CLIENT link: BLUETOOTH_CONNECT was revoked")
                        }
                    }
                    return
                }
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (!store.links.transition(link, BleLinkState.DISCOVERING)) return
                    AppLogger.d("BLE_MESH", "Link ${link.generation} CLIENT $macAddress: DISCOVERING radioStatus=$status")
                    AppLogger.updateLink(macAddress, peerName, "CLIENT", link.generation, "DISCOVERING")
                    AppLogger.d("BLE_MESH", "GATT Socket locked with ${peerName}. Starting service discovery with default MTU.")
                    store.activeConnections[macAddress] = gatt
                    manager.scheduleAdvertisingUpdate()
                    store.connectedEndpointNames[macAddress] = peerName
                    NodeIdentity.idOf(peerName)?.let { store.endpointNodeIds[macAddress] = it }
                    store.pendingQueues.putIfAbsent(macAddress, ConcurrentLinkedDeque())
                    store.isWriting.putIfAbsent(macAddress, AtomicBoolean(false))
                    store.chunkBuffers.putIfAbsent(macAddress, ByteArray(0))
                    store.connectionInteractionTimes.putIfAbsent(macAddress, System.currentTimeMillis())
                    store.connectionEstablishTime[macAddress] = System.currentTimeMillis()

                    // The connect timeout is swapped for a handshake watchdog that always fires,
                    // so a dropped OEM discovery/descriptor callback cannot strand the lock.
                    timeoutHandler.removeCallbacks(connectTimeoutRunnable)
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
                    }, HANDSHAKE_WATCHDOG_MS)

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
                        AppLogger.d("BLE_MESH", "CLIENT connection priority skipped: BLUETOOTH_CONNECT was revoked")
                        finishConnectPhase("Bluetooth permission revoked")
                        store.links.transition(link, BleLinkState.DISCONNECTING)
                        store.links.forget(link)
                        return
                    }
                    // Default ATT payload size is reliable on every supported Android version.
                    // Negotiate no larger MTU until the link is READY and the setup queue is idle.
                    store.connectionMtu[macAddress] = 20
                    link.mtu = 20
                    requestServicesOnce(gatt)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
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
                    AppLogger.removeLink(macAddress, "CLIENT", link.generation)
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
                    try {
                        gatt.close()
                    } catch (e: SecurityException) {
                        AppLogger.d("BLE_MESH", "Could not close CLIENT link: BLUETOOTH_CONNECT was revoked")
                    }
                    store.links.forget(link)
                }

            }
            handleCallback()
        }
    }
}
