package edu.cit.resqmesh.core.network.bluetooth.gatt.server

import edu.cit.resqmesh.core.network.bluetooth.BleConfig
import android.bluetooth.*
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import edu.cit.resqmesh.core.model.ConnectedDevice
import edu.cit.resqmesh.core.model.NodeIdentity
import edu.cit.resqmesh.core.network.bluetooth.state.BleLink
import edu.cit.resqmesh.core.network.bluetooth.state.BleLinkRole
import edu.cit.resqmesh.core.network.bluetooth.state.BleLinkState
import edu.cit.resqmesh.core.logging.AppLogger
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import edu.cit.resqmesh.core.network.bluetooth.gatt.GattServerHost

internal class GattServerLinkEvents(
    override val registration: GattServerRegistration
) : GattServerHost by registration, GattServerRegistrationContext {
    fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val disconnectOwner = if (newState == BluetoothProfile.STATE_DISCONNECTED)
                store.links.current(device.address, BleLinkRole.SERVER) else null
            handler.post {
                if (!ownsServer()) return@post
                if (isConnectPermissionRevoked()) {
                    AppLogger.d("BLE_MESH", "Ignoring SERVER connection callback: BLUETOOTH_CONNECT was revoked")
                    return@post
                }
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> onServerConnected(device, status)
                    BluetoothProfile.STATE_DISCONNECTED -> onServerDisconnected(device, status, disconnectOwner)
                }
            }
        }

    private fun onServerConnected(device: BluetoothDevice, status: Int) {
        val macAddress = device.address
        if (sharesOutboundClientLink(macAddress) || awaitsOldIndication(device, macAddress)) return
        val peerName = store.connectedEndpointNames[macAddress]
        if (rejectsBlockedPeer(device, peerName) || rejectsDuplicateLink(device, macAddress, peerName) ||
            rejectsWhenFull(device)) return
        AppLogger.d("BLE_MESH", "Server: Device ${macAddress} connected.")
        val link = beginServerLink(device, peerName, status)
        val radioOwner = "server:${link.endpoint}:${link.generation}"
        handler.postDelayed(ServerSetupDeadline(link, device, radioOwner), serverSetupTimeoutMs)
        registerServerEndpoint(device)
        publishServerConnected(link, macAddress, peerName)
    }

    private fun beginServerLink(device: BluetoothDevice, peerName: String?, status: Int): BleLink {
        val macAddress = device.address
        val queue = store.pendingQueues.computeIfAbsent(macAddress) { ConcurrentLinkedDeque() }
        val writing = if (!store.activeConnections.containsKey(macAddress) &&
            !store.activeServerConnections.containsKey(macAddress)) {
            AtomicBoolean(false).also { store.isWriting[macAddress] = it }
        } else store.isWriting.computeIfAbsent(macAddress) { AtomicBoolean(false) }
        val link = store.links.begin(macAddress, BleLinkRole.SERVER, peerName?.let(NodeIdentity::idOf), queue, writing)
        beginRadioHandshake("server:${link.endpoint}:${link.generation}")
        link.serverDevice = device
        store.links.transition(link, BleLinkState.CONFIGURING)
        AppLogger.d("BLE_MESH",
            "Link ${link.generation} SERVER $macAddress ${link.peerNodeId ?: "unknown"}: " +
                "CONFIGURING radioStatus=$status")
        return link
    }

    private fun registerServerEndpoint(device: BluetoothDevice) {
        val macAddress = device.address
        store.activeServerConnections[macAddress] = device
        manager.scheduleAdvertisingUpdate()
        store.pendingQueues.putIfAbsent(macAddress, ConcurrentLinkedDeque())
        store.isWriting.putIfAbsent(macAddress, AtomicBoolean(false))
        store.chunkBuffers.putIfAbsent(macAddress, ByteArray(0))
        store.connectionInteractionTimes.putIfAbsent(macAddress, System.currentTimeMillis())
        // Recorded for server links too, so duplicate-link resolution can compare ages.
        store.connectionEstablishTime[macAddress] = System.currentTimeMillis()
    }

    private fun publishServerConnected(link: BleLink, macAddress: String, peerName: String?) {
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
    }

    private fun onServerDisconnected(device: BluetoothDevice, status: Int, disconnectOwner: BleLink?) {
        val macAddress = device.address
        val link = store.links.current(macAddress, BleLinkRole.SERVER)
        if (link == null || link !== disconnectOwner || link.serverDevice?.address != device.address) {
            AppLogger.d("BLE_MESH", "Ignoring unowned SERVER disconnect on $macAddress status=$status")
            return
        }
        store.links.transition(link, BleLinkState.DISCONNECTING)
        finishRadioHandshake("server:${link.endpoint}:${link.generation}", "server disconnected")
        AppLogger.d("BLE_MESH", "Link ${link.generation} SERVER $macAddress: DISCONNECTING radioStatus=$status")
        AppLogger.d("BLE_MESH", "Server: Device ${macAddress} disconnected.")
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

    /** Cancels an inbound connection; logs [revokedMessage] when BLUETOOTH_CONNECT was revoked. */
    internal fun cancelInbound(device: BluetoothDevice, revokedMessage: String) {
        try {
            gattServer?.cancelConnection(device)
        } catch (e: SecurityException) {
            AppLogger.d("BLE_MESH", revokedMessage)
        }
    }

    /** Closes a SERVER role that never reached READY, unless the peer is already READY on another role. */
    private inner class ServerSetupDeadline(
        private val link: BleLink,
        private val device: BluetoothDevice,
        private val radioOwner: String
    ) : Runnable {
        override fun run() {
            if (!store.links.isCurrent(link) || link.state != BleLinkState.CONFIGURING) return
            val macAddress = device.address
            when {
                isConnectPermissionRevoked() -> {
                    AppLogger.d("BLE_MESH", "SERVER setup deadline ended: BLUETOOTH_CONNECT was revoked")
                    finishRadioHandshake(radioOwner, "Bluetooth permission revoked")
                    store.links.transition(link, BleLinkState.DISCONNECTING)
                    store.links.forget(link)
                }
                store.links.hasReadyPeerExcept(link) -> {
                    AppLogger.d("BLE_MESH",
                        "Link ${link.generation} SERVER $macAddress: " +
                            "keeping unfinished role while peer has a READY link")
                    handler.postDelayed(this, serverSetupTimeoutMs)
                }
                store.links.expireConfiguring(link) -> {
                    AppLogger.d("BLE_MESH",
                        "Link ${link.generation} SERVER $macAddress: CCCD setup timed out; closing unfinished link")
                    finishRadioHandshake(radioOwner, "server setup timeout")
                    try { gattServer?.cancelConnection(device) } catch (e: SecurityException) {
                        AppLogger.d(
                            "BLE_MESH",
                            "Setup-timeout disconnect failed on $macAddress: ${e.javaClass.simpleName}"
                        )
                    }
                }
            }
        }
    }
}

private fun GattServerRegistrationContext.isConnectPermissionRevoked(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
        PackageManager.PERMISSION_GRANTED

/**
 * Android may report the local GATT server side of an ACL that this process
 * opened as a client. It is not a second inbound mesh link. Creating a SERVER
 * record here schedules a CCCD timeout which can later cancel the healthy
 * outbound ACL, especially on Samsung devices.
 */
private fun GattServerRegistrationContext.sharesOutboundClientLink(macAddress: String): Boolean {
    val ownedClient = store.links.current(macAddress, BleLinkRole.CLIENT)
    val shares = ownedClient != null && store.links.hasLiveRole(macAddress, BleLinkRole.CLIENT)
    if (shares) {
        AppLogger.d(
            "BLE_MESH",
            "Server view shares outbound CLIENT link ${ownedClient?.generation} on $macAddress; " +
                "no duplicate SERVER setup"
        )
    }
    return shares
}

/** A replacement on the same address waits until the old link's indication resolves. */
private fun GattServerRegistrationContext.awaitsOldIndication(device: BluetoothDevice, macAddress: String): Boolean {
    val waits = store.links.current(macAddress, BleLinkRole.SERVER) == null &&
        store.serverIndications.unresolved(macAddress)
    if (waits) {
        AppLogger.d("BLE_MESH", "SERVER_CALLBACK_WAIT rejecting same-address replacement until old indication resolves")
        try { gattServer?.cancelConnection(device) } catch (_: SecurityException) {}
    }
    return waits
}

private fun GattServerLinkEvents.rejectsBlockedPeer(device: BluetoothDevice, peerName: String?): Boolean {
    if (peerName == null || !isDeviceBlocked(peerName)) return false
    AppLogger.d("BLE_MESH", "Server: Rejected blocked device ${peerName}.")
    cancelInbound(device, "Could not reject blocked device: BLUETOOTH_CONNECT was revoked")
    return true
}

/**
 * COLLISION & ZOMBIE SOCKET RESOLUTION
 * Resolved by identity rather than MAC. The inbound device arrives on its
 * Central MAC, so `activeConnections[macAddress]` was almost always null here
 * and the collision went undetected, leaving two sockets to one peer. The old
 * score comparison was also dead code: `endpointLastScore` is empty for a MAC
 * we never scanned, so `myScore > ""` was always true.
 *
 * Returns true when the inbound link was rejected. A simultaneous connect that this side loses
 * yields the existing link and accepts the inbound one.
 */
private fun GattServerLinkEvents.rejectsDuplicateLink(
    device: BluetoothDevice,
    macAddress: String,
    peerName: String?
): Boolean {
    val existingEndpoint = peerName?.let { findLinkEndpointByIdentity(it) }
    return when {
        existingEndpoint == null || existingEndpoint == macAddress -> false
        System.currentTimeMillis() - linkEstablishedAt(existingEndpoint) < BleConfig.DUPLICATE_LINK_GRACE_MS ->
            resolveSimultaneousConnect(device, existingEndpoint, peerName)
        else -> {
    // The existing link is established and healthy. A redundant inbound
    // connection must not be allowed to tear it down: that self-inflicted
    // teardown was the connect/disconnect loop.
    AppLogger.d("BLE_MESH",
        "Rejecting redundant inbound link from $peerName. Healthy link already on $existingEndpoint.")
    cancelInbound(device, "Could not reject redundant inbound link: BLUETOOTH_CONNECT was revoked")
            true
        }
    }
}

/**
 * Simultaneous connect. Returns true when this side keeps its outbound link and rejects the
 * inbound one; otherwise yields the existing link and accepts the inbound one.
 */
private fun GattServerLinkEvents.resolveSimultaneousConnect(
    device: BluetoothDevice,
    existingEndpoint: String,
    peerName: String?
): Boolean {
    // Simultaneous connect. Break the tie deterministically so exactly one
    // side yields: the node with the lower ID keeps its outbound link.
    val myId = NodeIdentity.idOf(myDeviceName) ?: myNodeId
    val theirId = NodeIdentity.idOf(peerName) ?: ""
    val keepOurs = myId < theirId
    if (keepOurs) {
        AppLogger.d("BLE_MESH",
            "Dual-Link Collision with $peerName: we keep our link ($myId < $theirId). Rejecting inbound.")
        cancelInbound(device, "Could not reject duplicate inbound link: BLUETOOTH_CONNECT was revoked")
    } else {
        AppLogger.d("BLE_MESH",
            "Dual-Link Collision with $peerName: yielding our link ($myId >= $theirId). Accepting inbound.")
        disconnectFromEndpoint(existingEndpoint)
    }
    return keepOurs
}

private fun GattServerLinkEvents.rejectsWhenFull(device: BluetoothDevice): Boolean {
    if (distinctLinkCount() < BleConfig.MAX_TOTAL_CONNECTIONS) return false
    AppLogger.d("BLE_MESH", "Server: Rejected connection from ${device.address}. Mesh node is full.")
    cancelInbound(device, "Could not reject full-mesh connection: BLUETOOTH_CONNECT was revoked")
    return true
}
