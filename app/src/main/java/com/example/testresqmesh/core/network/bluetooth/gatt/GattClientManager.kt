package com.example.testresqmesh.core.network.bluetooth.gatt

import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.model.ScanEvent
import com.example.testresqmesh.core.network.NativeBleManager
import com.example.testresqmesh.core.network.bluetooth.state.BleLinkRole
import com.example.testresqmesh.core.network.bluetooth.state.BleLinkState
import com.example.testresqmesh.core.utils.AppLogger
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.nio.ByteBuffer

class GattClientManager(
    val context: Context,
    val manager: NativeBleManager
) {
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

    fun connectToPersistentGatt(macAddress: String, peerName: String) {
        with(manager) {
        // DUPLICATE LINK GUARD: resolve by identity, not by MAC. A peer already connected inbound
        // on its Central MAC used to look absent here, so we would open a second redundant link.
        val existingEndpoint = findLinkEndpointByIdentity(peerName)
        if (existingEndpoint != null) {
            AppLogger.d("BLE_MESH", "Skipping connect to $peerName: already linked on $existingEndpoint.")
            return
        }

        // A delayed election/reversal may fire after an inbound setup has already started on a
        // different private address. Do not create a second ACL while that handshake is alive.
        if (isRadioHandshakeActive()) {
            AppLogger.d("BLE_MESH", "Deferring outbound GATT to $peerName; another handshake owns the radio")
            return
        }

        if (!tryAcquireConnectLock(macAddress)) {
            AppLogger.d("BLE_MESH", "Connect already in progress; deferring $peerName until its cooldown expires.")
            return
        }

        handler.post {
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
                    if (!store.links.isCurrent(link) || (link.gatt != null && link.gatt !== gatt)) {
                        AppLogger.d("BLE_MESH", "Ignoring $event from stale CLIENT link ${link.generation} on $macAddress")
                        return false
                    }
                    if (link.gatt == null) link.gatt = gatt
                    return true
                }

                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (!owns(gatt, "connection state $newState/$status")) {
                        if (newState == BluetoothProfile.STATE_DISCONNECTED) gatt.close()
                        return
                    }
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        if (!store.links.transition(link, BleLinkState.DISCOVERING)) return
                        AppLogger.d("BLE_MESH", "Link ${link.generation} CLIENT $macAddress: DISCOVERING radioStatus=$status")
                        AppLogger.updateLink(macAddress, peerName, "CLIENT", link.generation, "DISCOVERING")
                        AppLogger.d("BLE_MESH", "GATT Socket locked with ${peerName}. Starting service discovery with default MTU.")
                        store.activeConnections[macAddress] = gatt
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
                        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
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
                            onDeviceDisconnected?.invoke(macAddress)
                            sendSystemPulse()
                        }
                        gatt.close()
                        store.links.forget(link)
                    }
                }
    
                override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
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
                }
    
                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
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
                            gatt.setCharacteristicNotification(txChar, true)
                            val descriptor = txChar.getDescriptor(CCC_DESCRIPTOR_UUID)
                            if (descriptor != null) {
                                descriptorWritePending = true
                                link.currentOperation = "WRITE_CCCD"
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                                } else {
                                    descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                                    gatt.writeDescriptor(descriptor)
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
    
                override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                    if (!owns(gatt, "descriptor $status")) return
                    link.currentOperation = null
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        if (!store.links.transition(link, BleLinkState.READY)) return
                        AppLogger.d("BLE_MESH", "Link ${link.generation} CLIENT $macAddress: READY descriptorStatus=$status")
                        AppLogger.updateLink(macAddress, peerName, "CLIENT", link.generation, "READY")
                        AppLogger.d("BLE_MESH", "GATT descriptor written successfully for ${macAddress}.")
                        handler.post {
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
                        
                        // Proceed to read the L2CAP PSM port
                        val psmChar = gatt.getService(SERVICE_UUID)?.getCharacteristic(L2CAP_PSM_CHARACTERISTIC_UUID)
                        if (psmChar != null) {
                            gatt.readCharacteristic(psmChar)
                        }
                        
                        finishConnectPhase("descriptor written")
                        sendSystemPulse()
                        processNextPayload(macAddress)
                    } else {
                        AppLogger.d("BLE_MESH", "GATT descriptor write failed for ${macAddress}. Status: ${status}. Forcing UI disconnect.")
                        finishConnectPhase("descriptor write failed")
                        forceGattDisconnect(macAddress, gatt)
                    }
                }
    
                override fun onCharacteristicRead(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int
                ) {
                    if (!owns(gatt, "characteristic read $status")) return
                    if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == L2CAP_PSM_CHARACTERISTIC_UUID) {
                        val psmBytes = characteristic.value
                        if (psmBytes != null && psmBytes.size == 4) {
                            val psm = java.nio.ByteBuffer.wrap(psmBytes).int
                            if (psm > 0 && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                AppLogger.d("BLE_MESH", "Discovered Peer PSM: $psm for $macAddress. Opening L2CAP Socket...")
                                Thread {
                                    try {
                                        val l2capSocket = gatt.device.createInsecureL2capChannel(psm)
                                        l2capSocket.connect()
                                        AppLogger.d("BLE_MESH", "Successfully connected L2CAP to $macAddress!")
                                        handleL2capConnection(macAddress, l2capSocket)
                                    } catch (e: Exception) {
                                        AppLogger.d("BLE_MESH", "L2CAP Connection failed to $macAddress: ${e.message}")
                                    }
                                }.start()
                            }
                        }
                    }
                }

                override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                    if (!owns(gatt, "notification")) return
                    val value = characteristic.value ?: return
                    val now = System.currentTimeMillis()
                    val lastInteraction = store.connectionInteractionTimes[macAddress] ?: 0L
                    if (now - lastInteraction > 5000 && (store.chunkBuffers[macAddress]?.size ?: 0) > 0) {
                        AppLogger.d("BLE_MESH", "Client Buffer timeout! Clearing corrupted chunk buffer for $macAddress")
                        store.chunkBuffers[macAddress] = ByteArray(0)
                    }
                    store.connectionInteractionTimes[macAddress] = now
                    link.lastInteractionAt = now
                    
                    val currentBuffer = store.chunkBuffers[macAddress] ?: ByteArray(0)
                    val newBuffer = ByteArray(currentBuffer.size + value.size)
                    System.arraycopy(currentBuffer, 0, newBuffer, 0, currentBuffer.size)
                    System.arraycopy(value, 0, newBuffer, currentBuffer.size, value.size)
                    
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
    
                override fun onCharacteristicWrite(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, status: Int) {
                    if (!owns(gatt, "characteristic write $status")) return
                    completeGattChunk(macAddress, BleLinkRole.CLIENT, status, gatt)
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
        }
    
        }
    }
}
