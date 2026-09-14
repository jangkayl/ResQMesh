package com.example.testresqmesh.core.network.bluetooth.gatt

import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.model.ScanEvent
import com.example.testresqmesh.core.network.NativeBleManager
import com.example.testresqmesh.core.utils.AppLogger
import java.util.concurrent.ConcurrentLinkedQueue
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
        if (!store.isConnecting.compareAndSet(false, true)) {
            AppLogger.d("BLE_MESH", "Already connecting to another device. Queuing connection to $peerName for later.")
            store.connectionAttempts.remove(macAddress) // QA FIX: Allow immediate retry next scan
            return
        }
        
        store.connectingMacAddress = macAddress
        handler.post {
            // Force UI update to show SYNCING...
            notifyScanState(macAddress, peerName, isConnecting = true)
        }

        val device = bluetoothAdapter.getRemoteDevice(macAddress)
        
        val timeoutHandler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            if (store.isConnecting.get()) {
                AppLogger.d("BLE_MESH", "GATT Connection timed out after 15s. Forcing lock release for long-distance retry.")
                store.isConnecting.set(false)
                val oldMac = store.connectingMacAddress
                store.connectingMacAddress = null
                if (oldMac != null) {
                    handler.post { notifyScanState(oldMac, peerName, isConnecting = false) }
                }
            }
        }
        try {
            val callback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        AppLogger.d("BLE_MESH", "GATT Socket locked with ${peerName}. Requesting MTU 512...")
                        store.activeConnections[macAddress] = gatt
                        store.connectedEndpointNames[macAddress] = peerName
                        NodeIdentity.idOf(peerName)?.let { store.endpointNodeIds[macAddress] = it }
                        store.pendingQueues.putIfAbsent(macAddress, ConcurrentLinkedQueue<ByteArray>())
                        store.isWriting.putIfAbsent(macAddress, AtomicBoolean(false))
                        store.chunkBuffers.putIfAbsent(macAddress, ByteArray(0))
                        store.connectionInteractionTimes.putIfAbsent(macAddress, System.currentTimeMillis())
                        store.connectionEstablishTime[macAddress] = System.currentTimeMillis()
                        
                        handler.postDelayed({
                            updateInvisibilityCloak()
                        }, 2000)
                        
                        handler.post {
                            onDeviceConnected?.invoke(ConnectedDevice(macAddress, peerName, isClassicConnected = true))
                        }
                        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                        gatt.requestMtu(512)
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        AppLogger.d("BLE_MESH", "GATT Socket disconnected from ${peerName}.")
                        if (store.connectingMacAddress == macAddress) {
                            store.connectingMacAddress = null
                            handler.post { notifyScanState(macAddress, peerName, isConnecting = false) }
                        }
                        store.isConnecting.set(false)
                        store.activeConnections.remove(macAddress)
                        store.pendingQueues.remove(macAddress)
                        store.isWriting.remove(macAddress)
                        store.chunkBuffers.remove(macAddress)
                        updateInvisibilityCloak()
                        store.connectedEndpointIds.remove(macAddress)
                        store.connectedEndpointNames.remove(macAddress)
                        
                        handler.post {
                            onDeviceDisconnected?.invoke(macAddress)
                            sendSystemPulse()
                        }
                        gatt.close()
                    }
                }
    
                override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                    val mac = gatt.device.address
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        AppLogger.d("BLE_MESH", "MTU Expanded to .")
                        store.connectionMtu[mac] = mtu - 3
                        gatt.discoverServices()
                    } else {
                        AppLogger.d("BLE_MESH", "MTU Expansion failed. Samsung Fallback to 23 bytes.")
                        store.connectionMtu[mac] = 20
                        gatt.discoverServices()
                    }
                }
    
                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        AppLogger.d("BLE_MESH", "GATT Services discovered for ${macAddress}. Ready to transmit.")
                        
                        val service = gatt.getService(SERVICE_UUID)
                        val txChar = service?.getCharacteristic(TX_CHARACTERISTIC_UUID)
                        var descriptorWritePending = false
                        if (txChar != null) {
                            gatt.setCharacteristicNotification(txChar, true)
                            val descriptor = txChar.getDescriptor(CCC_DESCRIPTOR_UUID)
                            if (descriptor != null) {
                                descriptorWritePending = true
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                                } else {
                                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
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
                            if (store.connectingMacAddress == macAddress) {
                                store.connectingMacAddress = null
                                handler.post { notifyScanState(macAddress, peerName, isConnecting = false) }
                            }
                            store.isConnecting.set(false)
                            forceGattDisconnect(macAddress, gatt)
                        } else {
                            // QA FIX: Send the SYSTEM pulse immediately after requesting notifications. 
                            // Do not wait for onDescriptorWrite because some Android OEMs drop the callback!
                            handler.postDelayed({
                                store.isConnecting.set(false)
                                sendSystemPulse()
                                processNextPayload(gatt.device.address)
                            }, 500)
                        }
                    } else {
                        AppLogger.d("BLE_MESH", "GATT services discovery failed for ${macAddress}. Status: ${status}. Forcing UI disconnect.")
                        if (store.connectingMacAddress == macAddress) {
                            store.connectingMacAddress = null
                            handler.post { notifyScanState(macAddress, peerName, isConnecting = false) }
                        }
                        store.isConnecting.set(false)
                        forceGattDisconnect(macAddress, gatt)
                    }
                }
    
                override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        AppLogger.d("BLE_MESH", "GATT descriptor written successfully for ${macAddress}.")
                        
                        // Proceed to read the L2CAP PSM port
                        val psmChar = gatt.getService(SERVICE_UUID)?.getCharacteristic(L2CAP_PSM_CHARACTERISTIC_UUID)
                        if (psmChar != null) {
                            gatt.readCharacteristic(psmChar)
                        }
                        
                        if (store.connectingMacAddress == macAddress) {
                            store.connectingMacAddress = null
                            handler.post { notifyScanState(macAddress, peerName, isConnecting = false) }
                        }
                        store.isConnecting.set(false)
                    } else {
                        AppLogger.d("BLE_MESH", "GATT descriptor write failed for ${macAddress}. Status: ${status}. Forcing UI disconnect.")
                        if (store.connectingMacAddress == macAddress) {
                            store.connectingMacAddress = null
                            handler.post { notifyScanState(macAddress, peerName, isConnecting = false) }
                        }
                        store.isConnecting.set(false)
                        forceGattDisconnect(macAddress, gatt)
                    }
                }
    
                override fun onCharacteristicRead(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int
                ) {
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
                    val value = characteristic.value ?: return
                    val now = System.currentTimeMillis()
                    val lastInteraction = store.connectionInteractionTimes[macAddress] ?: 0L
                    if (now - lastInteraction > 5000 && (store.chunkBuffers[macAddress]?.size ?: 0) > 0) {
                        AppLogger.d("BLE_MESH", "Client Buffer timeout! Clearing corrupted chunk buffer for $macAddress")
                        store.chunkBuffers[macAddress] = ByteArray(0)
                    }
                    store.connectionInteractionTimes[macAddress] = now
                    
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
                    store.isWriting[macAddress]?.set(false)
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        processNextPayload(macAddress)
                    } else {
                        AppLogger.d("BLE_MESH", "GATT write failed for ${macAddress}. Status: ${status}. Forcing UI disconnect.")
                        forceGattDisconnect(macAddress, gatt)
                    }
                }
            }
            
            timeoutHandler.postDelayed(timeoutRunnable, 15000)
            
            // Reverted TRANSPORT_LE because it causes instant disconnects on some OEM chipsets!
            device.connectGatt(context, false, callback)
        } catch (e: Exception) {
            AppLogger.d("BLE_MESH", "Exception in connectGatt: ${e.message}")
            if (store.connectingMacAddress == macAddress) {
                store.connectingMacAddress = null
                handler.post { notifyScanState(macAddress, peerName, isConnecting = false) }
            }
            store.isConnecting.set(false)
            timeoutHandler.removeCallbacks(timeoutRunnable)
        }
    
        }
    }
}
