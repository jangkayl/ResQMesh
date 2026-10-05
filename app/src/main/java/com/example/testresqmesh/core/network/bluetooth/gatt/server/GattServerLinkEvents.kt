package com.example.testresqmesh.core.network.bluetooth.gatt.server

import android.bluetooth.*
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.network.bluetooth.state.BleLinkRole
import com.example.testresqmesh.core.network.bluetooth.state.BleLinkState
import com.example.testresqmesh.core.utils.AppLogger
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import com.example.testresqmesh.core.network.bluetooth.gatt.GattServerHost

internal class GattServerLinkEvents(
    override val registration: GattServerRegistration
) : GattServerHost by registration, GattServerRegistrationContext {
    fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val disconnectOwner = if (newState == BluetoothProfile.STATE_DISCONNECTED)
                store.links.current(device.address, BleLinkRole.SERVER) else null
            handler.post {
                if (!ownsServer()) return@post
                fun handleCallback() {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                    ) {
                        AppLogger.d("BLE_MESH", "Ignoring SERVER connection callback: BLUETOOTH_CONNECT was revoked")
                        return
                    }
                    val macAddress = device.address
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        // Android may report the local GATT server side of an ACL that this process
                        // opened as a client. It is not a second inbound mesh link. Creating a SERVER
                        // record here schedules a CCCD timeout which can later cancel the healthy
                        // outbound ACL, especially on Samsung devices.
                        val ownedClient = store.links.current(macAddress, BleLinkRole.CLIENT)
                        if (ownedClient != null && store.links.hasLiveRole(macAddress, BleLinkRole.CLIENT)) {
                            AppLogger.d(
                                "BLE_MESH",
                                "Server view shares outbound CLIENT link ${ownedClient.generation} on $macAddress; no duplicate SERVER setup"
                            )
                            return
                        }
                        if (store.links.current(macAddress, BleLinkRole.SERVER) == null &&
                            store.serverIndications.unresolved(macAddress)) {
                            AppLogger.d("BLE_MESH", "SERVER_CALLBACK_WAIT rejecting same-address replacement until old indication resolves")
                            try { gattServer?.cancelConnection(device) } catch (_: SecurityException) {}
                            return
                        }
                        val peerName = store.connectedEndpointNames[macAddress]
                        if (peerName != null && isDeviceBlocked(peerName)) {
                            AppLogger.d("BLE_MESH", "Server: Rejected blocked device ${peerName}.")
                            try {
                                gattServer?.cancelConnection(device)
                            } catch (e: SecurityException) {
                                AppLogger.d("BLE_MESH", "Could not reject blocked device: BLUETOOTH_CONNECT was revoked")
                            }
                            return
                        }

                        // COLLISION & ZOMBIE SOCKET RESOLUTION
                        // Resolved by identity rather than MAC. The inbound device arrives on its
                        // Central MAC, so `activeConnections[macAddress]` was almost always null here
                        // and the collision went undetected, leaving two sockets to one peer. The old
                        // score comparison was also dead code: `endpointLastScore` is empty for a MAC
                        // we never scanned, so `myScore > ""` was always true.
                        val existingEndpoint = peerName?.let { findLinkEndpointByIdentity(it) }
                        if (existingEndpoint != null && existingEndpoint != macAddress) {
                            val existingAge = System.currentTimeMillis() - linkEstablishedAt(existingEndpoint)
                            if (existingAge < DUPLICATE_LINK_GRACE_MS) {
                                // Simultaneous connect. Break the tie deterministically so exactly one
                                // side yields: the node with the lower ID keeps its outbound link.
                                val myId = NodeIdentity.idOf(myDeviceName) ?: myNodeId
                                val theirId = NodeIdentity.idOf(peerName) ?: ""
                                if (myId < theirId) {
                                    AppLogger.d("BLE_MESH", "Dual-Link Collision with $peerName: we keep our link ($myId < $theirId). Rejecting inbound.")
                                    try {
                                        gattServer?.cancelConnection(device)
                                    } catch (e: SecurityException) {
                                        AppLogger.d("BLE_MESH", "Could not reject duplicate inbound link: BLUETOOTH_CONNECT was revoked")
                                    }
                                    return
                                }
                                AppLogger.d("BLE_MESH", "Dual-Link Collision with $peerName: yielding our link ($myId >= $theirId). Accepting inbound.")
                                disconnectFromEndpoint(existingEndpoint)
                            } else {
                                // The existing link is established and healthy. A redundant inbound
                                // connection must not be allowed to tear it down: that self-inflicted
                                // teardown was the connect/disconnect loop.
                                AppLogger.d("BLE_MESH", "Rejecting redundant inbound link from $peerName. Healthy link already on $existingEndpoint.")
                                try {
                                    gattServer?.cancelConnection(device)
                                } catch (e: SecurityException) {
                                    AppLogger.d("BLE_MESH", "Could not reject redundant inbound link: BLUETOOTH_CONNECT was revoked")
                                }
                                return
                            }
                        }

                        val totalConnections = distinctLinkCount()
                        if (totalConnections >= MAX_TOTAL_CONNECTIONS) {
                            AppLogger.d("BLE_MESH", "Server: Rejected connection from ${device.address}. Mesh node is full.")
                            try {
                                gattServer?.cancelConnection(device)
                            } catch (e: SecurityException) {
                                AppLogger.d("BLE_MESH", "Could not reject full-mesh connection: BLUETOOTH_CONNECT was revoked")
                            }
                            return
                        }
                        AppLogger.d("BLE_MESH", "Server: Device ${macAddress} connected.")
                        val queue = store.pendingQueues.computeIfAbsent(macAddress) { ConcurrentLinkedDeque() }
                        val writing = if (!store.activeConnections.containsKey(macAddress) &&
                            !store.activeServerConnections.containsKey(macAddress)) {
                            AtomicBoolean(false).also { store.isWriting[macAddress] = it }
                        } else store.isWriting.computeIfAbsent(macAddress) { AtomicBoolean(false) }
                        val link = store.links.begin(macAddress, BleLinkRole.SERVER, peerName?.let(NodeIdentity::idOf), queue, writing)
                        val radioOwner = "server:${link.endpoint}:${link.generation}"
                        beginRadioHandshake(radioOwner)
                        link.serverDevice = device
                        store.links.transition(link, BleLinkState.CONFIGURING)
                        AppLogger.d("BLE_MESH", "Link ${link.generation} SERVER $macAddress ${link.peerNodeId ?: "unknown"}: CONFIGURING radioStatus=$status")
                        AppLogger.updateLink(macAddress, peerName, "SERVER", link.generation, "CONFIGURING")
                        val setupDeadline = object : Runnable {
                            override fun run() {
                                if (!store.links.isCurrent(link) || link.state != BleLinkState.CONFIGURING) return
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    AppLogger.d("BLE_MESH", "SERVER setup deadline ended: BLUETOOTH_CONNECT was revoked")
                                    finishRadioHandshake(radioOwner, "Bluetooth permission revoked")
                                    store.links.transition(link, BleLinkState.DISCONNECTING)
                                    store.links.forget(link)
                                    return
                                }
                                if (store.links.hasReadyPeerExcept(link)) {
                                    AppLogger.d("BLE_MESH", "Link ${link.generation} SERVER $macAddress: keeping unfinished role while peer has a READY link")
                                    handler.postDelayed(this, serverSetupTimeoutMs)
                                    return
                                }
                                if (store.links.expireConfiguring(link)) {
                                    AppLogger.d("BLE_MESH", "Link ${link.generation} SERVER $macAddress: CCCD setup timed out; closing unfinished link")
                                    finishRadioHandshake(radioOwner, "server setup timeout")
                                    try { gattServer?.cancelConnection(device) } catch (e: SecurityException) {
                                        AppLogger.d("BLE_MESH", "Server setup timeout disconnect failed on $macAddress: ${e.message}")
                                    }
                                }
                            }
                        }
                        handler.postDelayed(setupDeadline, serverSetupTimeoutMs)
                        store.activeServerConnections[macAddress] = device
                        manager.scheduleAdvertisingUpdate()
                        store.pendingQueues.putIfAbsent(macAddress, ConcurrentLinkedDeque())
                        store.isWriting.putIfAbsent(macAddress, AtomicBoolean(false))
                        store.chunkBuffers.putIfAbsent(macAddress, ByteArray(0))
                        store.connectionInteractionTimes.putIfAbsent(macAddress, System.currentTimeMillis())
                        // Recorded for server links too, so duplicate-link resolution can compare ages.
                        store.connectionEstablishTime[macAddress] = System.currentTimeMillis()

                        val safePeerName = peerName ?: NodeIdentity.UNKNOWN_NAME
                        store.connectedEndpointNames[macAddress] = safePeerName
                        // Seed the identity when this MAC was scanned before, so a provisional socket can
                        // still suppress the peer's discovery row instead of showing it twice.
                        val seededNodeId = NodeIdentity.idOf(safePeerName) ?: store.endpointNodeIds[macAddress].orEmpty()
                        if (seededNodeId.isNotEmpty()) {
                            store.endpointNodeIds[macAddress] = seededNodeId
                            link.peerNodeId = seededNodeId
                        }

                        // A physical socket now exists, so publish it immediately. Previously the
                        // connected event was withheld until a SYSTEM pulse revealed the peer name, which
                        // left a live link missing from `connectedDevices`. The Radar then fell through to
                        // its scanned-device branch and mislabelled the peer "Connected (Via Relay)".
                        // Provisional links are surfaced to the UI but kept out of the routing tables.
                        val isProvisional = NodeIdentity.isPlaceholder(safePeerName)
                        handler.post {
                            if (!ownsServer()) return@post
                            onDeviceConnected?.invoke(
                                ConnectedDevice(
                                    endpointId = macAddress,
                                    name = safePeerName,
                                    isClassicConnected = true,
                                    isProvisional = isProvisional,
                                    nodeId = seededNodeId,
                                    isPayloadReady = false
                                )
                            )
                        }

                        if (isProvisional) {
                            AppLogger.d("BLE_MESH", "Server: Provisional device connected; identity deadline starts only after CCCD is READY.")
                        }
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        val link = store.links.current(macAddress, BleLinkRole.SERVER)
                        if (link == null || link !== disconnectOwner || link.serverDevice?.address != device.address) {
                            AppLogger.d("BLE_MESH", "Ignoring unowned SERVER disconnect on $macAddress status=$status")
                            return
                        }
                        store.links.transition(link, BleLinkState.DISCONNECTING)
                        finishRadioHandshake("server:${link.endpoint}:${link.generation}", "server disconnected")
                        AppLogger.d("BLE_MESH", "Link ${link.generation} SERVER $macAddress: DISCONNECTING radioStatus=$status")
                        AppLogger.d("BLE_MESH", "Server: Device ${macAddress} disconnected.")
                        AppLogger.removeLink(macAddress, "SERVER", link.generation)
                        store.activeServerConnections.remove(macAddress, device)
                        store.links.forget(link)
                        cleanupEndpointIfUnowned(macAddress)
                        // The client role may share this address and still be READY. Its identity and
                        // establishment state belong to the surviving role, not this server callback.
                        if (!store.activeConnections.containsKey(macAddress)) {
                            store.connectedEndpointIds.remove(macAddress)
                            store.connectedEndpointNames.remove(macAddress)
                            store.endpointNodeIds.remove(macAddress)
                            store.connectionEstablishTime.remove(macAddress)
                        }
                        handler.post {
                            if (!ownsServer()) return@post
                            onDeviceDisconnected?.invoke(macAddress)
                            sendSystemPulse()
                        }
                    }

                }
                handleCallback()
            }
        }
}
