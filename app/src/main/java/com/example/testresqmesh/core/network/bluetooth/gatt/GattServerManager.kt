package com.example.testresqmesh.core.network.bluetooth.gatt

import android.bluetooth.*
import android.content.Context
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.network.NativeBleManager
import com.example.testresqmesh.core.utils.AppLogger
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.nio.ByteBuffer

class GattServerManager(
    val context: Context,
    val manager: NativeBleManager
) {
    fun startGattServer() {
        with(manager) {
        val serverCallback = object : BluetoothGattServerCallback() {
            
            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                val macAddress = device.address
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    val peerName = store.connectedEndpointNames[macAddress]
                    if (peerName != null && store.blockedDevices[peerName] == true) {
                        AppLogger.d("BLE_MESH", "Server: Rejected blocked device ${peerName}.")
                        gattServer?.cancelConnection(device)
                        return
                    }
                    
                    // COLLISION & ZOMBIE SOCKET RESOLUTION
                    val clientGatt = store.activeConnections[macAddress]
                    if (clientGatt != null) {
                        val age = System.currentTimeMillis() - (store.connectionEstablishTime[macAddress] ?: 0L)
                        if (age < 5000) {
                            val myScore = getElectionScore()
                            val theirScore = store.endpointLastScore[macAddress] ?: ""
                            if (myScore > theirScore) {
                                AppLogger.d("BLE_MESH", "Dual-Link Collision: We have superior score ($myScore > $theirScore). Rejecting incoming Server link.")
                                gattServer?.cancelConnection(device)
                                return
                            } else {
                                AppLogger.d("BLE_MESH", "Dual-Link Collision: We have inferior score. Killing our Client link and accepting Server link.")
                                forceGattDisconnect(macAddress, clientGatt)
                            }
                        } else {
                            AppLogger.d("BLE_MESH", "Zombie Socket Detected! Peer $macAddress is forcing a reconnection. Killing old Client link.")
                            forceGattDisconnect(macAddress, clientGatt)
                        }
                    }

                    val totalConnections = store.activeConnections.size + store.activeServerConnections.size
                    if (totalConnections >= MAX_TOTAL_CONNECTIONS) {
                        AppLogger.d("BLE_MESH", "Server: Rejected connection from ${device.address}. Mesh node is full.")
                        gattServer?.cancelConnection(device)
                        return
                    }
                    AppLogger.d("BLE_MESH", "Server: Device ${macAddress} connected.")
                    store.activeServerConnections[macAddress] = device
                    store.pendingQueues.putIfAbsent(macAddress, ConcurrentLinkedQueue<ByteArray>())
                    store.isWriting.putIfAbsent(macAddress, AtomicBoolean(false))
                    store.chunkBuffers.putIfAbsent(macAddress, ByteArray(0))
                    store.connectionInteractionTimes.putIfAbsent(macAddress, System.currentTimeMillis())
                    
                    val safePeerName = peerName ?: "Unknown Node"
                    store.connectedEndpointNames[macAddress] = safePeerName
                    
                    handler.postDelayed({
                        updateInvisibilityCloak()
                    }, 2000)
                    
                    if (safePeerName == "Unknown Node") {
                        AppLogger.d("BLE_MESH", "Server: Alien device connected. Waiting 5s for SYSTEM pulse handshake...")
                        handler.postDelayed({
                            if (store.connectedEndpointNames[macAddress] == "Unknown Node") {
                                AppLogger.d("BLE_MESH", "Server: Handshake timeout! Alien device $macAddress kicked from Mesh.")
                                gattServer?.cancelConnection(device)
                            }
                        }, 5000)
                    } else {
                        handler.post {
                            onDeviceConnected?.invoke(ConnectedDevice(macAddress, safePeerName, isClassicConnected = true))
                        }
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    AppLogger.d("BLE_MESH", "Server: Device ${macAddress} disconnected.")
                    store.activeServerConnections.remove(macAddress)
                    store.connectedEndpointIds.remove(macAddress)
                    store.connectedEndpointNames.remove(macAddress)
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
            }

            override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
                AppLogger.d("BLE_MESH", "Server: MTU Expanded to $mtu for ${device.address}.")
                store.connectionMtu[device.address] = mtu - 3
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
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
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
