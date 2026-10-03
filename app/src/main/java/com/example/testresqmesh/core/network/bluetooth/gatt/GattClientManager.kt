package com.example.testresqmesh.core.network.bluetooth.gatt

import android.bluetooth.*
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.model.ScanEvent
import com.example.testresqmesh.core.network.NativeBleManager
import com.example.testresqmesh.core.network.bluetooth.BleConnectStartResult
import com.example.testresqmesh.core.network.bluetooth.ReadyPayloadSetup
import com.example.testresqmesh.core.network.bluetooth.state.BleLink
import com.example.testresqmesh.core.network.bluetooth.state.BleLinkRole
import com.example.testresqmesh.core.network.bluetooth.state.BleLinkState
import com.example.testresqmesh.core.network.bluetooth.state.MeshFrameCodec
import com.example.testresqmesh.core.utils.AppLogger
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean

class GattClientManager(
    val context: Context,
    val manager: NativeBleManager
) {
    private val setups = java.util.concurrent.ConcurrentHashMap<String, Pair<BleLink, ReadyPayloadSetup>>()
    fun onGattIdle(endpoint: String) { setups[endpoint]?.takeIf { manager.store.links.isCurrent(it.first) }?.second?.onGattIdle() }
    fun onSocketLost(endpoint: String) { setups[endpoint]?.takeIf { manager.store.links.isCurrent(it.first) }?.second?.onSocketLost() }
    fun clearSetups() { setups.clear() }
    /**
     * Publishes a connection-state change for a peer that is already in the discovery list.
     * `peerConnections` / `peerScore` are deliberately left null so the repository keeps the values
     * learned from the peer's last real advertisement.
     */
    private fun NativeBleManager.notifyScanState(macAddress: String, peerName: String, isConnecting: Boolean) {
        onDeviceScanned?.invoke(
            ScanEvent(
                endpointId = macAddress,
                name = peerName,
                nodeId = NodeIdentity.idOf(peerName) ?: store.endpointNodeIds[macAddress].orEmpty(),
                isConnecting = isConnecting
            )
        )
    }

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

        val device = bluetoothAdapter.getRemoteDevice(macAddress)
        val queue = store.pendingQueues.computeIfAbsent(macAddress) { ConcurrentLinkedDeque() }
        val writing = if (!store.activeConnections.containsKey(macAddress) &&
            !store.activeServerConnections.containsKey(macAddress)) {
            AtomicBoolean(false).also { store.isWriting[macAddress] = it }
        } else store.isWriting.computeIfAbsent(macAddress) { AtomicBoolean(false) }
        val link = store.links.begin(macAddress, BleLinkRole.CLIENT, NodeIdentity.idOf(peerName), queue, writing)
        val payloadSetup = ReadyPayloadSetup(
            isCurrentReady = { isTransportGenerationCurrent(epoch) && store.links.isCurrent(link) && link.state == BleLinkState.READY },
            isGattIdle = { !store.gattFlights.containsKey(macAddress) && !store.serverIndications.unresolved(macAddress) && link.currentOperation == null },
            hasL2cap = { store.activeL2capSockets[macAddress]?.isConnected == true },
            requestPort = {
                val characteristic = link.gatt?.getService(SERVICE_UUID)?.getCharacteristic(L2CAP_PSM_CHARACTERISTIC_UUID)
                characteristic != null && runCatching { link.gatt?.readCharacteristic(characteristic) == true }.getOrDefault(false)
            },
            requestMtu = { runCatching { link.gatt?.requestMtu(247) == true }.getOrDefault(false) },
            openPort = { port, done ->
                val socket = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    runCatching { link.gatt?.device?.createInsecureL2capChannel(port) }.getOrNull() else null
                if (socket == null || !trackPendingL2capSocket(socket, epoch)) done(false)
                else {
                    val finished = AtomicBoolean(false)
                    handler.postDelayed({
                        if (finished.compareAndSet(false, true)) {
                            runCatching { socket.close() }; finishPendingL2capSocket(socket); done(false)
                        }
                    }, 10_000L)
                    Thread {
                        val connected = runCatching { socket.connect(); true }.getOrDefault(false)
                        handler.post {
                            finishPendingL2capSocket(socket)
                            if (finished.compareAndSet(false, true)) {
                                val owned = connected && isTransportGenerationCurrent(epoch) && store.links.isCurrent(link)
                                if (owned) handleL2capConnection(macAddress, socket) else runCatching { socket.close() }
                                done(owned)
                            } else runCatching { socket.close() }
                        }
                    }.start()
                }
            },
            schedule = { delay, action -> handler.postDelayed({ action() }, delay) },
            gate = { operation ->
                if (store.links.isCurrent(link)) {
                    link.currentOperation = operation
                    if (operation == null) processNextPayload(macAddress)
                }
            },
            log = { AppLogger.d("BLE_MESH", "PAYLOAD_SETUP generation=${link.generation} endpoint=$macAddress $it") },
            supportsPort = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && com.example.testresqmesh.BuildConfig.BLE_L2CAP_ENABLED,
            onStalled = { link.gattQuarantined = true; forceGattDisconnect(macAddress, link.gatt) }
        )
        setups[macAddress] = link to payloadSetup
        val stablePeerId = link.peerNodeId ?: NodeIdentity.idOf(peerName)
        val useExplicitLeTransport = stablePeerId != null &&
            store.explicitLeTransportPeers.contains(stablePeerId)
        val radioOwner = "client:${link.endpoint}:${link.generation}"
        beginRadioHandshake(radioOwner)
        AppLogger.d("BLE_MESH", "Link ${link.generation} CLIENT $macAddress ${link.peerNodeId ?: "unknown"}: CONNECTING")
        AppLogger.updateLink(macAddress, peerName, "CLIENT", link.generation, "CONNECTING")

        val timeoutHandler = Handler(Looper.getMainLooper())
        // Service discovery is the only readiness-critical operation started after CONNECTED.
        // MTU negotiation used to run first, but some Samsung stacks begin their own cache/service
        // work at connection time. The MTU request then overlapped that work and the later discovery
        // fallback was rejected as "already has a pending command".
        val servicesRequested = AtomicBoolean(false)
        var discoveryRequestAttempts = 0

        fun requestServicesOnce(gatt: BluetoothGatt) {
            if (store.links.isCurrent(link) && servicesRequested.compareAndSet(false, true)) {
                discoveryRequestAttempts += 1
                link.currentOperation = "DISCOVER_SERVICES"
                val accepted = gatt.discoverServices()
                AppLogger.d("BLE_MESH", "Service discovery request for $peerName accepted=$accepted attempt=$discoveryRequestAttempts")
                if (!accepted) {
                    link.currentOperation = null
                    servicesRequested.set(false)
                    if (discoveryRequestAttempts < MAX_DISCOVERY_REQUEST_ATTEMPTS) {
                        timeoutHandler.postDelayed({ requestServicesOnce(gatt) }, DISCOVERY_REQUEST_RETRY_MS)
                    }
                }
            }
        }

        fun finishConnectPhase(reason: String) {
            timeoutHandler.removeCallbacksAndMessages(null)
            finishRadioHandshake(radioOwner, reason)
            if (!store.links.isCurrent(link)) return
            releaseConnectLock(macAddress, reason)
            if (store.connectingMacAddress == null) {
                handler.post { notifyScanState(macAddress, peerName, isConnecting = false) }
            }
        }

        val connectTimeoutRunnable = Runnable {
            if (!store.links.isCurrent(link)) return@Runnable
            AppLogger.d("BLE_MESH", "GATT Connection timed out after 15s. Forcing lock release for long-distance retry.")
            AppLogger.removeLink(macAddress, "CLIENT", link.generation)
            finishConnectPhase("connect timeout")
            store.links.transition(link, BleLinkState.DISCONNECTING)
            try { link.gatt?.disconnect(); link.gatt?.close() } catch (e: Exception) {}
            store.links.forget(link)
        }

        try {
            val callback = object : BluetoothGattCallback() {
                fun owns(gatt: BluetoothGatt, event: String): Boolean {
                    if (!isTransportGenerationCurrent(epoch) || !store.links.isCurrent(link) || (link.gatt != null && link.gatt !== gatt)) {
                        AppLogger.d("BLE_MESH", "Ignoring $event from stale CLIENT link ${link.generation} on $macAddress")
                        return false
                    }
                    if (link.gatt == null) link.gatt = gatt
                    return true
                }

                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
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

                override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                    handler.post {
                        if (!isTransportGenerationCurrent(epoch)) return@post
                        fun handleCallback() {
                            if (!owns(gatt, "MTU $status")) return
                            val mac = gatt.device.address
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                AppLogger.d("BLE_MESH", "MTU Expanded to $mtu.")
                                store.connectionMtu[mac] = mtu - 3
                                link.mtu = mtu - 3
                            } else {
                                AppLogger.d("BLE_MESH", "MTU Expansion failed. Samsung Fallback to 23 bytes.")
                                store.connectionMtu[mac] = 20
                                link.mtu = 20
                            }
                            requestServicesOnce(gatt)
                            payloadSetup.onMtu()

                        }
                        handleCallback()
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    handler.post {
                        if (!isTransportGenerationCurrent(epoch)) return@post
                        fun handleCallback() {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                            ) {
                                AppLogger.d("BLE_MESH", "Ignoring CLIENT service callback for $peerName: BLUETOOTH_CONNECT was revoked")
                                finishConnectPhase("Bluetooth permission revoked")
                                store.links.transition(link, BleLinkState.DISCONNECTING)
                                store.links.forget(link)
                                return
                            }
                            if (!owns(gatt, "services $status")) return
                            link.currentOperation = null
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                if (!store.links.transition(link, BleLinkState.CONFIGURING)) return
                                AppLogger.d("BLE_MESH", "Link ${link.generation} CLIENT $macAddress: CONFIGURING serviceStatus=$status")
                                AppLogger.updateLink(macAddress, peerName, "CLIENT", link.generation, "CONFIGURING")
                                AppLogger.d("BLE_MESH", "GATT Services discovered for ${macAddress}. Ready to transmit.")

                                val service = gatt.getService(SERVICE_UUID)
                                val txChar = service?.getCharacteristic(TX_CHARACTERISTIC_UUID)
                                var descriptorWritePending = false
                                if (txChar != null) {
                                    try {
                                        gatt.setCharacteristicNotification(txChar, true)
                                    } catch (e: SecurityException) {
                                        AppLogger.d("BLE_MESH", "CLIENT notification setup skipped: BLUETOOTH_CONNECT was revoked")
                                        finishConnectPhase("Bluetooth permission revoked")
                                        store.links.transition(link, BleLinkState.DISCONNECTING)
                                        store.links.forget(link)
                                        return
                                    }
                                    val descriptor = txChar.getDescriptor(CCC_DESCRIPTOR_UUID)
                                    if (descriptor != null) {
                                        descriptorWritePending = true
                                        link.currentOperation = "WRITE_CCCD"
                                        try {
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                                            } else {
                                                descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                                                gatt.writeDescriptor(descriptor)
                                            }
                                        } catch (e: SecurityException) {
                                            AppLogger.d("BLE_MESH", "CLIENT descriptor setup skipped: BLUETOOTH_CONNECT was revoked")
                                            finishConnectPhase("Bluetooth permission revoked")
                                            store.links.transition(link, BleLinkState.DISCONNECTING)
                                            store.links.forget(link)
                                            return
                                        }
                                    }
                                }

                                if (!descriptorWritePending) {
                                    AppLogger.d("BLE_MESH", "Failed to setup TX Char/Descriptor (GATT Cache issue). Clearing Cache & Disconnecting.")
                                    try {
                                        val localMethod = gatt.javaClass.getMethod("refresh")
                                        localMethod.invoke(gatt)
                                    } catch (e: Exception) {}
                                    finishConnectPhase("tx characteristic missing")
                                    forceGattDisconnect(macAddress, gatt)
                                }
                            } else {
                                AppLogger.d("BLE_MESH", "GATT services discovery failed for ${macAddress}. Status: ${status}. Forcing UI disconnect.")
                                finishConnectPhase("service discovery failed")
                                forceGattDisconnect(macAddress, gatt)
                            }

                        }
                        handleCallback()
                    }
                }

                override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                    handler.post {
                        if (!isTransportGenerationCurrent(epoch)) return@post
                        fun handleCallback() {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                            ) {
                                AppLogger.d("BLE_MESH", "Ignoring CLIENT descriptor callback for $peerName: BLUETOOTH_CONNECT was revoked")
                                finishConnectPhase("Bluetooth permission revoked")
                                store.links.transition(link, BleLinkState.DISCONNECTING)
                                store.links.forget(link)
                                return
                            }
                            if (!owns(gatt, "descriptor $status")) return
                            link.currentOperation = null
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                if (!store.links.transition(link, BleLinkState.READY)) return
                                AppLogger.d("BLE_MESH", "Link ${link.generation} CLIENT $macAddress: READY descriptorStatus=$status")
                                AppLogger.updateLink(macAddress, peerName, "CLIENT", link.generation, "READY")
                                AppLogger.d("BLE_MESH", "GATT descriptor written successfully for ${macAddress}.")
                                handler.post {
                                    if (!isTransportGenerationCurrent(epoch)) return@post
                                    if (store.links.isCurrent(link) && link.state == BleLinkState.READY) {
                                        onDeviceConnected?.invoke(ConnectedDevice(
                                            endpointId = macAddress,
                                            name = store.connectedEndpointNames[macAddress] ?: peerName,
                                            isClassicConnected = true,
                                            nodeId = link.peerNodeId.orEmpty(),
                                            isPayloadReady = true
                                        ))
                                    }
                                }

                                finishConnectPhase("descriptor written")
                                payloadSetup.start()
                                sendSystemPulse(forceFull = true)
                                processNextPayload(macAddress)
                            } else {
                                AppLogger.d("BLE_MESH", "GATT descriptor write failed for ${macAddress}. Status: ${status}. Forcing UI disconnect.")
                                finishConnectPhase("descriptor write failed")
                                forceGattDisconnect(macAddress, gatt)
                            }

                        }
                        handleCallback()
                    }
                }

                override fun onCharacteristicRead(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int
                ) {
                    val capturedValue = characteristic.value?.copyOf()
                    handler.post {
                        if (!isTransportGenerationCurrent(epoch)) return@post
                        fun handleCallback() {
                            if (!owns(gatt, "characteristic read $status")) return
                            if (characteristic.uuid == L2CAP_PSM_CHARACTERISTIC_UUID) {
                                val port = if (status == BluetoothGatt.GATT_SUCCESS && capturedValue?.size == 4)
                                    java.nio.ByteBuffer.wrap(capturedValue).int else null
                                payloadSetup.onPort(port)
                            }

                        }
                        handleCallback()
                    }
                }

                override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                    val capturedValue = characteristic.value?.copyOf()
                    handler.post {
                        if (!isTransportGenerationCurrent(epoch)) return@post
                        fun handleCallback() {
                            if (!owns(gatt, "notification")) return
                            val value = capturedValue ?: return
                            val now = System.currentTimeMillis()
                            if (now - link.lastGattChunkAt > 5000 && link.receiveBuffer.isNotEmpty()) {
                                AppLogger.d("BLE_MESH", "Client Buffer timeout! Clearing corrupted chunk buffer for $macAddress")
                                link.receiveBuffer = byteArrayOf()
                            }
                            link.lastGattChunkAt = now
                            store.connectionInteractionTimes[macAddress] = now
                            link.lastInteractionAt = now

                            when (val result = MeshFrameCodec.append(link.receiveBuffer, value)) {
                                is MeshFrameCodec.AppendResult.Accepted -> {
                                    result.payloads.forEach { processBinaryPayload(macAddress, it) }
                                    link.receiveBuffer = result.remainder
                                }
                                is MeshFrameCodec.AppendResult.Rejected -> {
                                    AppLogger.d("BLE_MESH", "Rejected malformed CLIENT frame from $macAddress: ${result.reason}")
                                    link.receiveBuffer = byteArrayOf()
                                }
                            }

                        }
                        handleCallback()
                    }
                }

                override fun onCharacteristicWrite(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, status: Int) {
                    handler.post {
                        if (!isTransportGenerationCurrent(epoch)) return@post
                        fun handleCallback() {
                            if (!owns(gatt, "characteristic write $status")) return
                            completeGattChunk(macAddress, BleLinkRole.CLIENT, status, gatt)

                        }
                        handleCallback()
                    }
                }
            }

            timeoutHandler.postDelayed(connectTimeoutRunnable, CONNECT_TIMEOUT_MS)

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
            finishConnectPhase("connectGatt threw")
            store.links.forget(link)
            return BleConnectStartResult.REJECTED
        }
        return if (link.gatt != null) BleConnectStartResult.STARTED else {
            finishConnectPhase("connectGatt returned null")
            store.links.forget(link)
            BleConnectStartResult.REJECTED
        }
        }
    }
}
