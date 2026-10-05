package com.example.testresqmesh.core.network.bluetooth.session

import com.example.testresqmesh.core.network.transport.NativeOutboundDispatcher
import com.example.testresqmesh.core.network.bluetooth.BlePresencePublisher
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.testresqmesh.core.network.bluetooth.BleAvailability
import com.example.testresqmesh.core.network.bluetooth.BleSessionLifecycle
import com.example.testresqmesh.core.network.bluetooth.MeshTransportState
import android.os.Handler
import com.example.testresqmesh.core.network.bluetooth.BleLifecycleSupervisor
import com.example.testresqmesh.core.network.bluetooth.BlePeerAdmissionController
import com.example.testresqmesh.core.network.bluetooth.BleRadioController
import com.example.testresqmesh.core.network.bluetooth.L2capTransport
import com.example.testresqmesh.core.network.bluetooth.state.HeartbeatCoordinator
import com.example.testresqmesh.core.utils.AppLogger
import com.example.testresqmesh.core.network.bluetooth.session.BleTransportResources
import com.example.testresqmesh.core.network.bluetooth.session.BleTransportSessionController
import com.example.testresqmesh.core.network.DirectedReceiptQueue
import com.example.testresqmesh.core.network.ReliableMeshTransfers
import com.example.testresqmesh.core.network.bluetooth.state.BleStateStore

@SuppressLint("MissingPermission")
internal class BleTransportSessionController(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?,
    private val store: BleStateStore,
    private val handler: Handler,
    private val resources: BleTransportResources,
    private val radioController: BleRadioController,
    private val peerAdmissionController: BlePeerAdmissionController,
    private val lifecycleSupervisor: BleLifecycleSupervisor,
    private val l2capTransport: L2capTransport,
    private val clearClientSetups: () -> Unit,
    private val outboundDispatcher: NativeOutboundDispatcher,
    private val privateReceipts: DirectedReceiptQueue,
    private val heartbeatCoordinator: HeartbeatCoordinator,
    private val transfers: () -> ReliableMeshTransfers,
    private val presence: () -> BlePresencePublisher,
    private val distinctReadyLinkCount: () -> Int,
    private val distinctLinkCount: () -> Int,
    private val startGattServer: () -> Unit,
    private val getElectionScore: () -> String,
    private val releaseConnectLock: (String?, String, Boolean) -> Unit,
    private val readTeamKey: () -> String,
    private val writeTeamKey: (String) -> Unit,
    private val readCloaked: () -> Boolean,
    private val writeCloaked: (Boolean) -> Unit,
    private val deviceName: () -> String,
    private val nodeId: () -> String,
    private val myHex: String,
    private val status: () -> ((String) -> Unit)?,
    private val transportState: () -> ((MeshTransportState) -> Unit)?,
    private val disconnected: () -> ((String) -> Unit)?,
    private val scanRemoved: () -> ((String) -> Unit)?
) {
    private val reliableTransfers get() = transfers()
    private val presencePublisher get() = presence()
    private val onStatusChanged get() = status()
    private val onTransportStateChanged get() = transportState()
    private val onDeviceDisconnected get() = disconnected()
    private val onDeviceScanRemoved get() = scanRemoved()
    private val myDeviceName get() = deviceName()
    private val myNodeId get() = nodeId()
    private var currentTeamKey: String
        get() = readTeamKey()
        set(value) { writeTeamKey(value) }
    private var isCloaked: Boolean
        get() = readCloaked()
        set(value) { writeCloaked(value) }
    private var gattServer: BluetoothGattServer?
        get() = resources.gattServer
        set(value) { resources.gattServer = value }
    private var l2capServerSocket: BluetoothServerSocket?
        get() = resources.l2capServerSocket
        set(value) { resources.l2capServerSocket = value }
    private var l2capAcceptThread: Thread?
        get() = resources.l2capAcceptThread
        set(value) { resources.l2capAcceptThread = value }
    private var myL2capPsm: Int
        get() = resources.myL2capPsm
        set(value) { resources.myL2capPsm = value }
    private var serverRegistrationGeneration: Long
        get() = resources.serverRegistrationGeneration
        set(value) { resources.serverRegistrationGeneration = value }
    val transportGeneration get() = sessionLifecycle.generation
    val isServiceReady get() = serviceReady
    private fun isTransportGenerationCurrent(generation: Long) = sessionLifecycle.owns(generation)
    private fun startScanning() = radioController.startScanning()
    val sessionLifecycle: BleSessionLifecycle = BleSessionLifecycle(
        startTransport = ::startTransport,
        stopTransport = ::stopTransport,
        publishState = { state ->
            AppLogger.d("BLE_RECOVERY", "transport=${state.name} generation=$transportGeneration")
            onTransportStateChanged?.invoke(state)
            onStatusChanged?.invoke(state.status)
        }
    )
    private var receiverRegistered = false
    private var serviceReady = false
    private var restartAttempts = 0
    private var advertisingAttempts = 0
    private val pendingL2capSockets = mutableSetOf<BluetoothSocket>()
    fun trackPendingL2capSocket(socket: BluetoothSocket, generation: Long): Boolean {
        if (!isTransportGenerationCurrent(generation)) {
            try { socket.close() } catch (_: Exception) {}
            return false
        }
        pendingL2capSockets.add(socket)
        return true
    }
    fun finishPendingL2capSocket(socket: BluetoothSocket) { pendingL2capSockets.remove(socket) }
    private val advertisingRetry = Runnable {
        if (store.isNodeActive.get() && serviceReady) startAdvertising(currentTeamKey)
    }
    fun retryAdvertising() {
        advertisingAttempts++
        handler.removeCallbacks(advertisingRetry)
        handler.postDelayed(advertisingRetry,
            com.example.testresqmesh.core.network.bluetooth.BleScanRecoveryPolicy.retryDelay(advertisingAttempts) +
                kotlin.random.Random.nextLong(2_001L))
    }
    private val startupTimeout = Runnable { failTransport(transportGeneration) }
    private val recoveryCheck = object : Runnable {
        override fun run() {
            if (!sessionLifecycle.requested) return
            val availability = bluetoothAvailability()
            sessionLifecycle.reconcile(availability)
            if (sessionLifecycle.state == MeshTransportState.ERROR && availability == BleAvailability.AVAILABLE) return
            if (serviceReady && sessionLifecycle.running) {
                sessionLifecycle.peers(distinctReadyLinkCount())
                radioController.reconcileHandshakeOwners(store.links::isSetupOwner)
                radioController.startScanning()
                peerAdmissionController.recover()
            }
            handler.removeCallbacks(this)
            handler.postDelayed(this, 5_000L)
        }
    }
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            handler.post {
                if (!sessionLifecycle.requested) return@post
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state != BluetoothAdapter.ERROR) {
                    // Read actual state: an old OFF broadcast may arrive after a rapid ON.
                    reconcileTransport()
                }
            }
        }
    }

    private var lastAdvertisedConnections: Int = -1
    private val updateAdvertisingRunnable = Runnable {
        if (!store.isNodeActive.get()) return@Runnable
        val currentConnections = distinctLinkCount()
        if (currentConnections != lastAdvertisedConnections) {
            lastAdvertisedConnections = currentConnections
            AppLogger.d("BLE_MESH", "Updating BLE advertisement: directConnections=$currentConnections")
            startAdvertising(currentTeamKey)
        }
    }


    fun bluetoothAvailability(): BleAvailability {
        fun allowed(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (!allowed(Manifest.permission.BLUETOOTH_CONNECT) || !allowed(Manifest.permission.BLUETOOTH_SCAN) ||
                !allowed(Manifest.permission.BLUETOOTH_ADVERTISE))) return BleAvailability.PERMISSION_REQUIRED
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !allowed(Manifest.permission.ACCESS_FINE_LOCATION)) {
            return BleAvailability.PERMISSION_REQUIRED
        }
        return try {
            when {
                bluetoothAdapter == null -> BleAvailability.UNSUPPORTED
                !bluetoothAdapter.isEnabled -> BleAvailability.BLUETOOTH_OFF
                else -> BleAvailability.AVAILABLE
            }
        } catch (_: SecurityException) { BleAvailability.PERMISSION_REQUIRED }
    }

    fun reconcileTransport() {
        handler.post {
            if (!sessionLifecycle.requested) return@post
            handler.removeCallbacks(recoveryCheck)
            recoveryCheck.run()
        }
    }

    fun startMeshNode(teamKey: String) {
        handler.post {
            currentTeamKey = teamKey
            isCloaked = false
            if (!receiverRegistered) {
                ContextCompat.registerReceiver(context.applicationContext, bluetoothStateReceiver,
                    IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
                receiverRegistered = true
            }
            sessionLifecycle.start(bluetoothAvailability())
            if (sessionLifecycle.state != MeshTransportState.ERROR) {
                handler.removeCallbacks(recoveryCheck)
                handler.postDelayed(recoveryCheck, 5_000L)
            }
        }
    }

    fun startTransport(generation: Long) {
        store.isNodeActive.set(true)
        reliableTransfers.wake()
        serviceReady = false
        presencePublisher.resetTransport()
        handler.removeCallbacks(startupTimeout)
        handler.postDelayed(startupTimeout, 5_000L)
        try {
            startGattServer()
        } catch (_: Exception) { failTransport(generation) }
    }

    fun onGattServerReady(generation: Long) {
        if (!isTransportGenerationCurrent(generation) || serviceReady) return
        handler.removeCallbacks(startupTimeout)
        serviceReady = true
        restartAttempts = 0
        advertisingAttempts = 0
        sessionLifecycle.ready(generation)
        sessionLifecycle.peers(distinctReadyLinkCount())
        startAdvertising(currentTeamKey)
        startScanning()
        lifecycleSupervisor.start()
    }

    fun failTransport(generation: Long) {
        if (!isTransportGenerationCurrent(generation)) return
        sessionLifecycle.failed(generation)
        restartAttempts++
        handler.removeCallbacks(recoveryCheck)
        val delay = com.example.testresqmesh.core.network.bluetooth.BleScanRecoveryPolicy.retryDelay(restartAttempts) +
            kotlin.random.Random.nextLong(2_001L)
        handler.postDelayed(recoveryCheck, delay)
    }

    fun stopMeshNode() {
        handler.post {
            handler.removeCallbacks(recoveryCheck)
            sessionLifecycle.stop()
            if (receiverRegistered) {
                context.applicationContext.unregisterReceiver(bluetoothStateReceiver)
                receiverRegistered = false
            }
        }
    }

    fun stopTransport() {
        store.isNodeActive.set(false)
        reliableTransfers.pause()
        outboundDispatcher.clearFallback()
        outboundDispatcher.clearLegacyFrames()
        clearClientSetups()
        privateReceipts.clear()
        serviceReady = false
        handler.removeCallbacks(startupTimeout)
        handler.removeCallbacks(advertisingRetry)
        pendingL2capSockets.forEach { try { it.close() } catch (_: Exception) {} }
        pendingL2capSockets.clear()
        peerAdmissionController.stop()
        val retiringLinks = store.links.snapshot()
        val endpoints = (store.activeConnections.keys + store.activeServerConnections.keys + store.connectedEndpointIds +
            retiringLinks.map { it.endpoint }).toSet()
        AppLogger.clearLinks()
        radioController.stop()

        store.links.clear()

        store.activeServerConnections.values.forEach { try { gattServer?.cancelConnection(it) } catch (_: Exception) {} }
        store.activeServerConnections.clear()
        serverRegistrationGeneration++
        store.serverIndications.reset(serverRegistrationGeneration)
        try { gattServer?.close() } catch (_: Exception) {}
        gattServer = null

        try {
            l2capServerSocket?.close()
            l2capAcceptThread?.interrupt()
        } catch (e: Exception) {}
        l2capServerSocket = null
        l2capAcceptThread = null
        myL2capPsm = 0

        (store.activeConnections.values + retiringLinks.mapNotNull { it.gatt }).distinct().forEach {
            try { it.disconnect() } catch (_: Exception) {}
            try { it.close() } catch (_: Exception) {}
        }
        store.activeConnections.clear()
        l2capTransport.stop()
        store.pendingQueues.clear()
        store.gattFlights.clear()
        heartbeatCoordinator.clear()
        store.isWriting.clear()
        lifecycleSupervisor.stop()
        handler.removeCallbacks(updateAdvertisingRunnable)
        lastAdvertisedConnections = -1
        store.connectedEndpointIds.clear()
        store.connectedEndpointNames.clear()
        store.endpointLastSeen.clear()
        store.endpointFirstSeen.clear()
        store.endpointNodeIds.clear()
        store.endpointLastScore.clear()
        store.connectionEstablishTime.clear()
        store.connectionInteractionTimes.clear()
        store.l2capOutboundProgressTimes.clear()
        store.chunkBuffers.clear()
        store.connectionMtu.clear()
        store.writeFailureCount.clear()
        store.orphanDetectionTime.clear()
        releaseConnectLock(null, "transport suspended", true)
        endpoints.forEach { endpoint ->
            onDeviceDisconnected?.invoke(endpoint)
            onDeviceScanRemoved?.invoke(endpoint)
        }
    }

    fun scheduleAdvertisingUpdate(delayMs: Long = 1000L) {
        handler.removeCallbacks(updateAdvertisingRunnable)
        handler.postDelayed(updateAdvertisingRunnable, delayMs)
    }

    fun startAdvertising(teamKey: String) {
        if (!store.isNodeActive.get() || !serviceReady) return
        lastAdvertisedConnections = distinctLinkCount()
        radioController.startAdvertising(
            electionScore = getElectionScore(),
            directConnections = lastAdvertisedConnections,
            deviceName = myDeviceName,
            fallbackNodeId = myNodeId.ifEmpty { myHex }
        )
    }

    fun rescan() {
        handler.post {
            if (!sessionLifecycle.requested) return@post
            sessionLifecycle.reconcile(bluetoothAvailability())
            if (!serviceReady) return@post
            radioController.reconcileHandshakeOwners(store.links::isSetupOwner)
            peerAdmissionController.drainCandidates()
            radioController.rescan()
        }
    }

    fun renewIdleGattServerIfNeeded() {
        if (!store.isNodeActive.get() || store.activeServerConnections.isNotEmpty() ||
            !store.serverIndications.hasUnresolved()) return
        // A missing indication callback cannot safely be attributed to a same-address replacement.
        // Renew only an idle server registration; CLIENT roles and other peers' sockets survive.
        AppLogger.d("BLE_MESH", "SERVER_REGISTRATION_RENEW unresolved indication; no server peers")
        serverRegistrationGeneration++
        try { gattServer?.close() } catch (_: Exception) {}
        gattServer = null
        serviceReady = false
        handler.removeCallbacks(startupTimeout)
        handler.postDelayed(startupTimeout, 5_000L)
        try { startGattServer() } catch (_: Exception) { failTransport(transportGeneration) }
    }
}
