package com.example.testresqmesh.core.network.bluetooth.gatt

import android.bluetooth.*
import android.content.Context
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.network.NativeBleManager
import com.example.testresqmesh.core.network.bluetooth.state.BleLinkRole
import com.example.testresqmesh.core.network.bluetooth.state.BleLinkState
import com.example.testresqmesh.core.utils.AppLogger
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.nio.ByteBuffer

class GattServerManager(
    val context: Context,
    val manager: NativeBleManager
) {
    private val serverSetupTimeoutMs = 12_000L

    fun startGattServer() {
        with(manager) {
        val serverCallback = object : BluetoothGattServerCallback() {
            
            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                val macAddress = device.address
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    val peerName = store.connectedEndpointNames[macAddress]
                    if (peerName != null && isDeviceBlocked(peerName)) {
                        AppLogger.d("BLE_MESH", "Server: Rejected blocked device ${peerName}.")
                        gattServer?.cancelConnection(device)
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
                                gattServer?.cancelConnection(device)
                                return
                            }
                            AppLogger.d("BLE_MESH", "Dual-Link Collision with $peerName: yielding our link ($myId >= $theirId). Accepting inbound.")
                            disconnectFromEndpoint(existingEndpoint)
                        } else {
                            // The existing link is established and healthy. A redundant inbound
                            // connection must not be allowed to tear it down: that self-inflicted
                            // teardown was the connect/disconnect loop.
                            AppLogger.d("BLE_MESH", "Rejecting redundant inbound link from $peerName. Healthy link already on $existingEndpoint.")
                            gattServer?.cancelConnection(device)
                            return
                        }
                    }

                    val totalConnections = distinctLinkCount()
                    if (totalConnections >= MAX_TOTAL_CONNECTIONS) {
                        AppLogger.d("BLE_MESH", "Server: Rejected connection from ${device.address}. Mesh node is full.")
                        gattServer?.cancelConnection(device)
                        return
                    }
                    AppLogger.d("BLE_MESH", "Server: Device ${macAddress} connected.")
                    val queue = store.pendingQueues.computeIfAbsent(macAddress) { ConcurrentLinkedDeque() }
                    val writing = if (!store.activeConnections.containsKey(macAddress) &&
                        !store.activeServerConnections.containsKey(macAddress)) {
                        AtomicBoolean(false).also { store.isWriting[macAddress] = it }
                    } else store.isWriting.computeIfAbsent(macAddress) { AtomicBoolean(false) }
                    val link = store.links.begin(macAddress, BleLinkRole.SERVER, peerName?.let(NodeIdentity::idOf), queue, writing)
                    link.serverDevice = device
                    store.links.transition(link, BleLinkState.CONFIGURING)
                    AppLogger.d("BLE_MESH", "Link ${link.generation} SERVER $macAddress ${link.peerNodeId ?: "unknown"}: CONFIGURING radioStatus=$status")
                    val setupDeadline = object : Runnable {
                        override fun run() {
                            if (!store.links.isCurrent(link) || link.state != BleLinkState.CONFIGURING) return
                            if (store.links.hasReadyPeerExcept(link)) {
                                AppLogger.d("BLE_MESH", "Link ${link.generation} SERVER $macAddress: keeping unfinished role while peer has a READY link")
                                handler.postDelayed(this, serverSetupTimeoutMs)
                                return
                            }
                            if (store.links.expireConfiguring(link)) {
                                AppLogger.d("BLE_MESH", "Link ${link.generation} SERVER $macAddress: CCCD setup timed out; closing unfinished link")
                                try { gattServer?.cancelConnection(device) } catch (e: Exception) {
                                    AppLogger.d("BLE_MESH", "Server setup timeout disconnect failed on $macAddress: ${e.message}")
                                }
                            }
                        }
                    }
                    handler.postDelayed(setupDeadline, serverSetupTimeoutMs)
                    store.activeServerConnections[macAddress] = device
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
                    
                    handler.postDelayed({
                        updateInvisibilityCloak()
                    }, 2000)
                    
                    // A physical socket now exists, so publish it immediately. Previously the
                    // connected event was withheld until a SYSTEM pulse revealed the peer name, which
                    // left a live link missing from `connectedDevices`. The Radar then fell through to
                    // its scanned-device branch and mislabelled the peer "Connected (Via Relay)".
                    // Provisional links are surfaced to the UI but kept out of the routing tables.
                    val isProvisional = NodeIdentity.isPlaceholder(safePeerName)
                    handler.post {
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
                        AppLogger.d("BLE_MESH", "Server: Alien device connected. Waiting ${NAME_HANDSHAKE_TIMEOUT_MS}ms for name handshake...")
                        handler.postDelayed({
                            if (store.links.isCurrent(link) &&
                                NodeIdentity.isPlaceholder(store.connectedEndpointNames[macAddress])) {
                                AppLogger.d("BLE_MESH", "Server: Handshake timeout! Alien device $macAddress kicked from Mesh.")
                                gattServer?.cancelConnection(device)
                            }
                        }, NAME_HANDSHAKE_TIMEOUT_MS)
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    val link = store.links.current(macAddress, BleLinkRole.SERVER)
                    if (link == null || link.serverDevice?.address != device.address) {
                        AppLogger.d("BLE_MESH", "Ignoring unowned SERVER disconnect on $macAddress status=$status")
                        return
                    }
                    store.links.transition(link, BleLinkState.DISCONNECTING)
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
                        onDeviceDisconnected?.invoke(macAddress)
                        sendSystemPulse()
                    }
                }
            }

            override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                }
                val link = store.links.current(device.address, BleLinkRole.SERVER)
                if (link?.serverDevice?.address == device.address && descriptor.uuid == CCC_DESCRIPTOR_UUID &&
                    value?.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE) == true &&
                    store.links.transition(link, BleLinkState.READY)) {
                    AppLogger.d("BLE_MESH", "Link ${link.generation} SERVER ${device.address}: READY CCCD indications enabled")
                    handler.post {
                        if (store.links.isCurrent(link) && link.state == BleLinkState.READY) {
                            val name = store.connectedEndpointNames[device.address] ?: NodeIdentity.UNKNOWN_NAME
                            onDeviceConnected?.invoke(ConnectedDevice(
                                endpointId = device.address,
                                name = name,
                                isClassicConnected = true,
                                isProvisional = NodeIdentity.isPlaceholder(name),
                                nodeId = link.peerNodeId.orEmpty(),
                                isPayloadReady = true
                            ))
                        }
                    }
                    processNextPayload(device.address)
                }
            }

            override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
                AppLogger.d("BLE_MESH", "Server: MTU Expanded to $mtu for ${device.address}.")
                store.connectionMtu[device.address] = mtu - 3
                store.links.current(device.address, BleLinkRole.SERVER)?.takeIf { it.serverDevice?.address == device.address }?.mtu = mtu - 3
            }

            override fun onNotificationSent(device: BluetoothDevice, status: Int) {
                completeGattChunk(device.address, BleLinkRole.SERVER, status, device = device)
            }

            override fun onCharacteristicReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic
            ) {
                if (characteristic.uuid == L2CAP_PSM_CHARACTERISTIC_UUID) {
                    val psmBytes = java.nio.ByteBuffer.allocate(4).putInt(myL2capPsm).array()
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, psmBytes)
                } else {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
                }
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?
            ) {
                super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                }
                value?.let {
                    val macAddress = device.address
                    val now = System.currentTimeMillis()
                    val lastInteraction = store.connectionInteractionTimes[macAddress] ?: 0L
                    if (now - lastInteraction > 5000 && (store.chunkBuffers[macAddress]?.size ?: 0) > 0) {
                        AppLogger.d("BLE_MESH", "Server Buffer timeout! Clearing corrupted chunk buffer for $macAddress")
                        store.chunkBuffers[macAddress] = ByteArray(0)
                    }
                    store.connectionInteractionTimes[macAddress] = now
                    store.links.current(macAddress, BleLinkRole.SERVER)?.takeIf { it.serverDevice?.address == device.address }?.lastInteractionAt = now
                    
                    val currentBuffer = store.chunkBuffers[macAddress] ?: ByteArray(0)
                    val newBuffer = ByteArray(currentBuffer.size + it.size)
                    System.arraycopy(currentBuffer, 0, newBuffer, 0, currentBuffer.size)
                    System.arraycopy(it, 0, newBuffer, currentBuffer.size, it.size)
                    
                    var workingBuffer = newBuffer
                    
                    while (workingBuffer.size >= 4) {
                        val lengthBuffer = ByteBuffer.wrap(workingBuffer.sliceArray(0..3))
                        val expectedLength = lengthBuffer.int
                        
                        if (workingBuffer.size >= 4 + expectedLength) {
                            val payloadBytes = workingBuffer.sliceArray(4 until 4 + expectedLength)
                            processBinaryPayload(macAddress, payloadBytes)
                            
                            val remaining = workingBuffer.size - (4 + expectedLength)
                            val nextBuffer = ByteArray(remaining)
                            System.arraycopy(workingBuffer, 4 + expectedLength, nextBuffer, 0, remaining)
                            workingBuffer = nextBuffer
                        } else {
                            break
                        }
                    }
                    
                    store.chunkBuffers[macAddress] = workingBuffer
                }
            }
        }

        gattServer = bluetoothManager.openGattServer(context, serverCallback)
        
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        
        val rxChar = BluetoothGattCharacteristic(
            RX_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(rxChar)

        val txChar = BluetoothGattCharacteristic(
            TX_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_INDICATE,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        val cccDescriptor = BluetoothGattDescriptor(CCC_DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_WRITE)
        txChar.addDescriptor(cccDescriptor)
        service.addCharacteristic(txChar)
        
        val psmChar = BluetoothGattCharacteristic(
            L2CAP_PSM_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        service.addCharacteristic(psmChar)

        gattServer?.addService(service)
        startL2capServer()
    
        }
    }

    fun startL2capServer() {
        with(manager) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                l2capServerSocket = bluetoothAdapter?.listenUsingInsecureL2capChannel()
                myL2capPsm = l2capServerSocket?.psm ?: 0
                AppLogger.d("BLE_MESH", "L2CAP Server started on PSM: $myL2capPsm")
                
                l2capAcceptThread = Thread {
                    while (store.isNodeActive.get()) {
                        try {
                            val socket = l2capServerSocket?.accept()
                            if (socket != null) {
                                AppLogger.d("BLE_MESH", "L2CAP Connection Accepted from ${socket.remoteDevice.address}")
                                handleL2capConnection(socket.remoteDevice.address, socket)
                            }
                        } catch (e: Exception) {
                            if (store.isNodeActive.get()) {
                                AppLogger.d("BLE_MESH", "L2CAP Accept Thread error: ${e.message}")
                            }
                            break
                        }
                    }
                }
                l2capAcceptThread?.start()
            } catch (e: Exception) {
                AppLogger.d("BLE_MESH", "Failed to start L2CAP server: ${e.message}")
            }
        } else {
            AppLogger.d("BLE_MESH", "L2CAP CoC not supported on this Android version. Falling back to GATT exclusively.")
            myL2capPsm = 0
        }
    
        }
    }
}
