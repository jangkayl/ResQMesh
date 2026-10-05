package com.example.testresqmesh.core.network.bluetooth.gatt

import com.example.testresqmesh.core.network.bluetooth.gatt.client.GattClientAttempt
import com.example.testresqmesh.core.network.bluetooth.gatt.client.GattClientCallback
import com.example.testresqmesh.core.network.bluetooth.gatt.client.createGattClientPayloadSetup
import com.example.testresqmesh.core.network.bluetooth.gatt.client.notifyScanState
import android.bluetooth.*
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.network.NativeBleManager
import com.example.testresqmesh.core.network.bluetooth.BleConnectStartResult
import com.example.testresqmesh.core.network.bluetooth.ReadyPayloadSetup
import com.example.testresqmesh.core.network.bluetooth.state.BleLink
import com.example.testresqmesh.core.network.bluetooth.state.BleLinkRole
import com.example.testresqmesh.core.utils.AppLogger
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean

class GattClientManager(
    val context: Context,
    val manager: GattClientHost
) {
    constructor(context: Context, manager: NativeBleManager) : this(context, NativeGattHost(manager))

    private val setups = java.util.concurrent.ConcurrentHashMap<String, Pair<BleLink, ReadyPayloadSetup>>()
    fun onGattIdle(endpoint: String) { setups[endpoint]?.takeIf { manager.store.links.isCurrent(it.first) }?.second?.onGattIdle() }
    fun onSocketLost(endpoint: String) { setups[endpoint]?.takeIf { manager.store.links.isCurrent(it.first) }?.second?.onSocketLost() }
    fun clearSetups() { setups.clear() }
    /**
     * Publishes a connection-state change for a peer that is already in the discovery list.
     * `peerConnections` / `peerScore` are deliberately left null so the repository keeps the values
     * learned from the peer's last real advertisement.
     */


    fun connectToPersistentGatt(macAddress: String, peerName: String): BleConnectStartResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            AppLogger.d("BLE_MESH", "GATT connect skipped for $peerName: BLUETOOTH_CONNECT is not granted")
            return BleConnectStartResult.REJECTED
        }
        with(manager) {
        if (!store.isNodeActive.get()) return BleConnectStartResult.REJECTED
        val epoch = transportGeneration
        if (distinctLinkCount() >= MAX_TOTAL_CONNECTIONS) return BleConnectStartResult.REJECTED
        if (isDeviceBlocked(peerName)) {
            AppLogger.d("BLE_MESH", "Skipping GATT connect to blocked peer $peerName")
            return BleConnectStartResult.REJECTED
        }
        // DUPLICATE LINK GUARD: resolve by identity, not by MAC. A peer already connected inbound
        // on its Central MAC used to look absent here, so we would open a second redundant link.
        val existingEndpoint = findLinkEndpointByIdentity(peerName)
        if (existingEndpoint != null) {
            AppLogger.d("BLE_MESH", "Skipping connect to $peerName: already linked on $existingEndpoint.")
            return BleConnectStartResult.REJECTED
        }

        // A delayed election/reversal may fire after an inbound setup has already started on a
        // different private address. Do not create a second ACL while that handshake is alive.
        if (isRadioHandshakeActive()) {
            AppLogger.d("BLE_MESH", "Deferring outbound GATT to $peerName; another handshake owns the radio")
            return BleConnectStartResult.DEFERRED
        }

        if (!tryAcquireConnectLock(macAddress)) {
            AppLogger.d("BLE_MESH", "Connect already in progress; deferring $peerName until its cooldown expires.")
            return BleConnectStartResult.DEFERRED
        }

        handler.post {
            if (!isTransportGenerationCurrent(epoch)) return@post
            // Force UI update to show SYNCING...
            notifyScanState(macAddress, peerName, isConnecting = true)
        }

        val device = getRemoteDevice(macAddress)
        val queue = store.pendingQueues.computeIfAbsent(macAddress) { ConcurrentLinkedDeque() }
        val writing = if (!store.activeConnections.containsKey(macAddress) &&
            !store.activeServerConnections.containsKey(macAddress)) {
            AtomicBoolean(false).also { store.isWriting[macAddress] = it }
        } else store.isWriting.computeIfAbsent(macAddress) { AtomicBoolean(false) }
        val link = store.links.begin(macAddress, BleLinkRole.CLIENT, NodeIdentity.idOf(peerName), queue, writing)
        val payloadSetup = createGattClientPayloadSetup(manager, epoch, link, macAddress,
            readPort = { characteristic -> link.gatt?.readCharacteristic(characteristic) == true },
            requestMtu = { link.gatt?.requestMtu(247) == true })
        setups[macAddress] = link to payloadSetup
        val stablePeerId = link.peerNodeId ?: NodeIdentity.idOf(peerName)
        val useExplicitLeTransport = stablePeerId != null &&
            store.explicitLeTransportPeers.contains(stablePeerId)
        val radioOwner = "client:${link.endpoint}:${link.generation}"
        beginRadioHandshake(radioOwner)
        AppLogger.d("BLE_MESH", "Link ${link.generation} CLIENT $macAddress ${link.peerNodeId ?: "unknown"}: CONNECTING")
        AppLogger.updateLink(macAddress, peerName, "CLIENT", link.generation, "CONNECTING")

        val attempt = GattClientAttempt(manager, macAddress, peerName, epoch, link,
            payloadSetup, stablePeerId, useExplicitLeTransport, radioOwner,
            discoverServices = { gatt -> gatt.discoverServices() },
            retireTimedOutGatt = { link.gatt?.disconnect(); link.gatt?.close() })

        try {
            val callback = GattClientCallback(attempt)

            attempt.timeoutHandler.postDelayed(attempt.connectTimeoutRunnable, CONNECT_TIMEOUT_MS)

            // AUTO remains the compatibility default because explicit LE caused immediate
            // disconnects on older OEM pairs. A peer-specific retry switches to LE only after
            // AUTO connected but failed at ATT primary-service discovery.
            link.gatt = if (useExplicitLeTransport) {
                AppLogger.d("BLE_MESH", "Connecting to $peerName with explicit LE transport after AUTO discovery timeout")
                device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                AppLogger.d("BLE_MESH", "Connecting to $peerName with AUTO transport")
                device.connectGatt(context, false, callback)
            }
        } catch (e: Exception) {
            AppLogger.d("BLE_MESH", "Exception in connectGatt: ${e.message}")
            attempt.finishConnectPhase("connectGatt threw")
            store.links.forget(link)
            return BleConnectStartResult.REJECTED
        }
        return if (link.gatt != null) BleConnectStartResult.STARTED else {
            attempt.finishConnectPhase("connectGatt returned null")
            store.links.forget(link)
            BleConnectStartResult.REJECTED
        }
        }
    }
}
