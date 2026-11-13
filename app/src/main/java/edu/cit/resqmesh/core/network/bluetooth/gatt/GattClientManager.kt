package edu.cit.resqmesh.core.network.bluetooth.gatt

import edu.cit.resqmesh.core.network.bluetooth.BleConfig
import edu.cit.resqmesh.core.network.bluetooth.gatt.client.GattClientAttempt
import edu.cit.resqmesh.core.network.bluetooth.gatt.client.GattClientCallback
import edu.cit.resqmesh.core.network.bluetooth.gatt.client.createGattClientPayloadSetup
import edu.cit.resqmesh.core.network.bluetooth.gatt.client.notifyScanState
import android.bluetooth.*
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import edu.cit.resqmesh.core.model.NodeIdentity
import edu.cit.resqmesh.core.network.NativeBleManager
import edu.cit.resqmesh.core.network.bluetooth.BleConnectStartResult
import edu.cit.resqmesh.core.network.bluetooth.PhyPolicy
import edu.cit.resqmesh.core.network.bluetooth.ReadyPayloadSetup
import edu.cit.resqmesh.core.network.bluetooth.state.BleLink
import edu.cit.resqmesh.core.network.bluetooth.state.BleLinkRole
import edu.cit.resqmesh.core.logging.AppLogger
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
        if (cannotConnect(peerName)) return BleConnectStartResult.REJECTED
        val epoch = manager.transportGeneration
        return preflightRejection(macAddress, peerName) ?: startConnect(macAddress, peerName, epoch)
    }

    /** Missing BLUETOOTH_CONNECT or an inactive node rejects before any link bookkeeping. */
    private fun cannotConnect(peerName: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            AppLogger.d("BLE_MESH", "GATT connect skipped for $peerName: BLUETOOTH_CONNECT is not granted")
            return true
        }
        return !manager.store.isNodeActive.get()
    }

    /**
     * Rejects or defers before opening an ACL; null means go ahead. Checked in order, and the last
     * check acquires the connect lock when it succeeds.
     */
    private fun preflightRejection(macAddress: String, peerName: String): BleConnectStartResult? = with(manager) {
        when {
            distinctLinkCount() >= BleConfig.MAX_TOTAL_CONNECTIONS -> BleConnectStartResult.REJECTED
            isDeviceBlocked(peerName) -> {
                AppLogger.d("BLE_MESH", "Skipping GATT connect to blocked peer $peerName")
                BleConnectStartResult.REJECTED
            }
            isAlreadyLinked(peerName) -> BleConnectStartResult.REJECTED
            // A delayed election/reversal may fire after an inbound setup has already started on a
            // different private address. Do not create a second ACL while that handshake is alive.
            isRadioHandshakeActive() -> {
                AppLogger.d("BLE_MESH", "Deferring outbound GATT to $peerName; another handshake owns the radio")
                BleConnectStartResult.DEFERRED
            }
            !tryAcquireConnectLock(macAddress) -> {
                AppLogger.d("BLE_MESH", "Connect already in progress; deferring $peerName until its cooldown expires.")
                BleConnectStartResult.DEFERRED
            }
            else -> null
        }
    }

    /**
     * DUPLICATE LINK GUARD: resolve by identity, not by MAC. A peer already connected inbound
     * on its Central MAC used to look absent here, so we would open a second redundant link.
     */
    private fun GattClientHost.isAlreadyLinked(peerName: String): Boolean {
        val existingEndpoint = findLinkEndpointByIdentity(peerName) ?: return false
        AppLogger.d("BLE_MESH", "Skipping connect to $peerName: already linked on $existingEndpoint.")
        return true
    }

    // BLUETOOTH_CONNECT is checked by cannotConnect() in connectToPersistentGatt before this runs.
    @SuppressLint("MissingPermission")
    private fun startConnect(macAddress: String, peerName: String, epoch: Long): BleConnectStartResult = with(manager) {
        handler.post {
            if (!isTransportGenerationCurrent(epoch)) return@post
            // Force UI update to show SYNCING...
            notifyScanState(macAddress, peerName, isConnecting = true)
        }

        val device = getRemoteDevice(macAddress)
        val link = beginClientLink(macAddress, peerName)
        val payloadSetup = createGattClientPayloadSetup(manager, epoch, link, macAddress,
            readPort = { characteristic -> link.gatt?.readCharacteristic(characteristic) == true },
            requestMtu = { link.gatt?.requestMtu(BleConfig.REQUESTED_MTU) == true })
        setups[macAddress] = link to payloadSetup
        val stablePeerId = link.peerNodeId ?: NodeIdentity.idOf(peerName)
        val useExplicitLeTransport = stablePeerId != null &&
            store.explicitLeTransportPeers.contains(stablePeerId)
        val radioOwner = "client:${link.endpoint}:${link.generation}"
        beginRadioHandshake(radioOwner)
        AppLogger.d("BLE_MESH", "Link ${link.generation} CLIENT $macAddress ${link.peerNodeId ?: "unknown"}: CONNECTING")

        val attempt = GattClientAttempt(manager, macAddress, peerName, epoch, link,
            payloadSetup, stablePeerId, useExplicitLeTransport, radioOwner,
            discoverServices = { gatt -> gatt.discoverServices() },
            retireTimedOutGatt = { link.gatt?.disconnect(); link.gatt?.close() })
        openGatt(device, attempt)
    }

    private fun beginClientLink(macAddress: String, peerName: String): BleLink = with(manager) {
        val queue = store.pendingQueues.computeIfAbsent(macAddress) { ConcurrentLinkedDeque() }
        val writing = if (!store.activeConnections.containsKey(macAddress) &&
            !store.activeServerConnections.containsKey(macAddress)) {
            AtomicBoolean(false).also { store.isWriting[macAddress] = it }
        } else store.isWriting.computeIfAbsent(macAddress) { AtomicBoolean(false) }
        store.links.begin(macAddress, BleLinkRole.CLIENT, NodeIdentity.idOf(peerName), queue, writing)
    }

    /** Arms the connect timeout and calls connectGatt; a throw or null handle retires the link. */
    // Reached only through startConnect, after the same permission check.
    @SuppressLint("MissingPermission")
    private fun openGatt(device: BluetoothDevice, attempt: GattClientAttempt): BleConnectStartResult {
        val link = attempt.link
        val peerName = attempt.peerName
        try {
            val callback = GattClientCallback(attempt)

            attempt.timeoutHandler.postDelayed(attempt.connectTimeoutRunnable, attempt.connectTimeoutMs)

            // AUTO remains the compatibility default because explicit LE caused immediate
            // disconnects on older OEM pairs. A peer-specific retry switches to LE only after
            // AUTO connected but failed at ATT primary-service discovery.
            // A peer found only on Coded advertises from a Coded-only address, so the initiator must
            // be allowed to connect on Coded. The transport choice below is kept unchanged.
            val codedPeer = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                manager.store.codedPhyEndpoints.containsKey(device.address) &&
                PhyPolicy.supportsCodedLinks(Build.VERSION.SDK_INT, PhyPolicy.capabilities(context))
            link.gatt = if (codedPeer) {
                val transport = if (attempt.useExplicitLeTransport) BluetoothDevice.TRANSPORT_LE
                    else BluetoothDevice.TRANSPORT_AUTO
                AppLogger.d("BLE_PHY", "Connecting to $peerName with 1M|CODED initiator phys")
                device.connectGatt(context, false, callback, transport,
                    BluetoothDevice.PHY_LE_1M_MASK or BluetoothDevice.PHY_LE_CODED_MASK)
            } else if (attempt.useExplicitLeTransport) {
                AppLogger.d("BLE_MESH", "Connecting to $peerName with explicit LE transport after AUTO discovery timeout")
                device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                AppLogger.d("BLE_MESH", "Connecting to $peerName with AUTO transport")
                device.connectGatt(context, false, callback)
            }
        } catch (e: Exception) {
            AppLogger.d("BLE_MESH", "Exception in connectGatt: ${e.javaClass.simpleName}")
            attempt.finishConnectPhase("connectGatt threw")
            manager.store.links.forget(link)
            return BleConnectStartResult.REJECTED
        }
        return if (link.gatt != null) BleConnectStartResult.STARTED else {
            attempt.finishConnectPhase("connectGatt returned null")
            manager.store.links.forget(link)
            BleConnectStartResult.REJECTED
        }
    }
}
