package com.example.testresqmesh.core.network

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.utils.AppLogger
import com.example.testresqmesh.core.utils.NotificationHelper
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.nio.ByteBuffer
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf

@SuppressLint("MissingPermission")
class NativeBleManager(private val context: Context) {
    var onDeviceConnected: ((ConnectedDevice) -> Unit)? = null
    var onDeviceDisconnected: ((String) -> Unit)? = null
    var onDeviceScanned: ((String, String, Int, String, Boolean) -> Unit)? = null
    var onDeviceScanRemoved: ((String) -> Unit)? = null
    var onMessageReceived: ((String, String, String, String, Boolean, Boolean, String?, String?, Double?, Double?, String, List<String>) -> Unit)? = null
    var onMessageSeen: ((String, String) -> Unit)? = null
    var onMessageDelivered: ((String, String, List<String>) -> Unit)? = null
    var onPublicKeyReceived: ((String, String) -> Unit)? = null
    var onRoutingTableReceived: ((String, List<String>) -> Unit)? = null
    var onSosCancelled: (() -> Unit)? = null
    var onStatusChanged: ((String) -> Unit)? = null

    var myDeviceName: String = "ResQMesh_Node"

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val bleAdvertiser get() = bluetoothAdapter?.bluetoothLeAdvertiser
    private val bleScanner get() = bluetoothAdapter?.bluetoothLeScanner

    private val SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
    private val RX_CHARACTERISTIC_UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
    private val TX_CHARACTERISTIC_UUID = UUID.fromString("00002A1A-0000-1000-8000-00805F9B34FB")
    private val CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    private var gattServer: BluetoothGattServer? = null
    
    private val connectedEndpointIds = mutableSetOf<String>()
    private val connectedEndpointNames = mutableMapOf<String, String>()
    private val seenMessageIds = java.util.LinkedHashSet<String>()
    
    private val isNodeActive = java.util.concurrent.atomic.AtomicBoolean(false)
    private val notificationHelper = NotificationHelper(context)
    
    private val MAX_CONNECTIONS = 4
    private val activeConnections = ConcurrentHashMap<String, BluetoothGatt>()
    private val activeServerConnections = ConcurrentHashMap<String, BluetoothDevice>()
    private val pendingQueues = ConcurrentHashMap<String, ConcurrentLinkedQueue<ByteArray>>()
    private val isWriting = ConcurrentHashMap<String, AtomicBoolean>()
    private val chunkBuffers = ConcurrentHashMap<String, ByteArray>() // Stores incomplete binary payloads
    private val connectionAttempts = ConcurrentHashMap<String, Long>()

    private val payloadDispatcher = PayloadDispatcher(object : PayloadDispatcherCallback {
        override fun getMyDeviceName() = myDeviceName
        override fun getSeenMessageIds() = seenMessageIds
        override fun getEndpointMedium(endpointId: String) = "Persistent BLE Mesh"
        override fun getConnectedEndpointIdByName(name: String) = connectedEndpointNames.entries.find { it.value == name }?.key
        override fun sendDirectPayload(endpointId: String, payload: ByteArray) = this@NativeBleManager.sendDirectPayload(endpointId, payload)
        override fun broadcastPayload(payload: ByteArray, excludeEndpointId: String?) = this@NativeBleManager.broadcastPayload(payload, excludeEndpointId)
        override fun onMessageSeen(msgId: String, readerName: String) { onMessageSeen?.invoke(msgId, readerName) }
        override fun onMessageDelivered(msgId: String, readerName: String, returnRoute: List<String>) { onMessageDelivered?.invoke(msgId, readerName, returnRoute) }
        override fun onPublicKeyReceived(senderName: String, key: String) { onPublicKeyReceived?.invoke(senderName, key) }
        override fun onRoutingTableReceived(senderName: String, connectedNodes: List<String>) { onRoutingTableReceived?.invoke(senderName, connectedNodes) }
        override fun onMessageReceived(endpointId: String, msgId: String, senderName: String, text: String, isPrivate: Boolean, isSystem: Boolean, imageBase64: String?, audioBase64: String?, locationLat: Double?, locationLng: Double?, medium: String, routePath: List<String>) {
            this@NativeBleManager.onMessageReceived?.invoke(endpointId, msgId, senderName, text, isPrivate, isSystem, imageBase64, audioBase64, locationLat, locationLng, medium, routePath)
        }
        override fun onDeviceGoodbye(endpointId: String) {
            AppLogger.d("BLE_MESH", "Received GOODBYE packet from $endpointId. Disconnecting instantly.")
            this@NativeBleManager.disconnectFromEndpoint(endpointId)
            handler.post {
                this@NativeBleManager.onDeviceDisconnected?.invoke(endpointId)
            }
        }
        override fun onSosCancelled() { this@NativeBleManager.onSosCancelled?.invoke() }
        override fun showNotification(sender: String, text: String) { notificationHelper.showPrivateMessageNotification(sender, text) }
        override fun showSosEmergencyNotification(sender: String, text: String) {
            if (!com.example.testresqmesh.MainActivity.isAppInForeground) {
                notificationHelper.showSosEmergencyNotification(sender, text)
            }
        }
    })

    private val endpointLastSeen = mutableMapOf<String, Long>()
    private val handler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = object : Runnable {
        override fun run() {
            if (!isNodeActive.get()) return
            val now = System.currentTimeMillis()
            val iterator = endpointLastSeen.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val macAddress = entry.key
                
                if (activeConnections.containsKey(macAddress) || activeServerConnections.containsKey(macAddress)) {
                    entry.setValue(now)
                    continue
                }
                
                if (now - entry.value > 8000) { 
                    connectedEndpointIds.remove(macAddress)
                    connectedEndpointNames.remove(macAddress)
                    iterator.remove()
                    AppLogger.d("BLE_MESH", "Node Timed Out: ${macAddress}")
                    onDeviceDisconnected?.invoke(macAddress)
                    onDeviceScanRemoved?.invoke(macAddress)
                }
            }
            handler.postDelayed(this, 5000)
        }
    }

    companion object {
        var activeAdvertiseCallback: AdvertiseCallback? = null
        @SuppressLint("StaticFieldLeak")
        var instance: NativeBleManager? = null
    }

    fun startMeshNode(teamKey: String) {
        if (bleAdvertiser == null || bleScanner == null) {
            onStatusChanged?.invoke("Hardware not fully supported")
            return
        }
        isNodeActive.set(true)
        activeAdvertiseCallback = advertiseCallback
        instance = this
        
        context.startService(android.content.Intent(context, BleCleanupService::class.java))
        
        startGattServer()
        startAdvertising(teamKey)
        startScanning()
        handler.post(timeoutRunnable)
        onStatusChanged?.invoke("Mesh Active [Persistent GATT/Protobuf]. Seeking peers...")
    }
    
    
    private fun sendSystemPulse() {
        if (!isNodeActive.get()) return
        try {
            val pulseId = java.util.UUID.randomUUID().toString()
            val payload = com.example.testresqmesh.core.network.MeshPayload(
                id = pulseId,
                type = "SYSTEM",
                senderName = myDeviceName,
                connectedNodes = connectedEndpointNames.values.toList(),
                publicKey = com.example.testresqmesh.core.network.CryptoManager.getMyPublicKeyBase64()
            )
            val payloadBytes = kotlinx.serialization.protobuf.ProtoBuf.encodeToByteArray(com.example.testresqmesh.core.network.MeshPayload.serializer(), payload)
            broadcastPayload(payloadBytes)
        } catch (e: Exception) {
            AppLogger.d("BLE_MESH", "Failed to send system pulse: ${e.message}")
        }
    }

    fun stopMeshNode() {
        isNodeActive.set(false)
        bleAdvertiser?.stopAdvertising(advertiseCallback)
        bleScanner?.stopScan(scanCallback)
        
        try {
            val goodbyePayload = MeshPayload(
                id = java.util.UUID.randomUUID().toString(),
                type = "GOODBYE",
                senderName = myDeviceName
            )
            val bytes = kotlinx.serialization.protobuf.ProtoBuf.encodeToByteArray(goodbyePayload)
            broadcastPayload(bytes)
            
            // Give the BLE controller 200ms to flush the queued packet before severing the socket
            Thread.sleep(200)
        } catch (e: Exception) {}
        
        activeServerConnections.values.forEach { gattServer?.cancelConnection(it) }
        activeServerConnections.clear()
        gattServer?.close()
        activeConnections.values.forEach { it.disconnect(); it.close() }
        activeConnections.clear()
        pendingQueues.clear()
        isWriting.clear()
        handler.removeCallbacks(timeoutRunnable)
        connectedEndpointIds.clear()
        connectedEndpointNames.clear()
        endpointLastSeen.clear()
        instance = null
        onStatusChanged?.invoke("Offline")
    }

    private fun startAdvertising(teamKey: String) {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()
            
        val nameBytes = myDeviceName.take(20).toByteArray(Charsets.UTF_8)
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
            
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceData(ParcelUuid(SERVICE_UUID), nameBytes)
            .build()
            
        bleAdvertiser?.startAdvertising(settings, data, scanResponse, advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {}

    private fun startScanning() {
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build())
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
            
        bleScanner?.startScan(filters, settings, scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val serviceData = result.scanRecord?.getServiceData(ParcelUuid(SERVICE_UUID))
            if (serviceData == null) return
            
            val peerName = String(serviceData, Charsets.UTF_8).replace("\u0000", "").trim()
            val macAddress = device.address

            if (peerName != myDeviceName && peerName != myDeviceName.take(20)) {
                endpointLastSeen[macAddress] = System.currentTimeMillis()

                if (!connectedEndpointIds.contains(macAddress)) {
                    connectedEndpointIds.add(macAddress)
                    connectedEndpointNames[macAddress] = peerName
                    
                    onDeviceScanned?.invoke(macAddress, peerName, 50, "MEMBER", false)
                    onDeviceConnected?.invoke(ConnectedDevice(macAddress, peerName, isClassicConnected = false))
                }

                if (!activeConnections.containsKey(macAddress) && !activeServerConnections.containsKey(macAddress) && activeConnections.size < MAX_CONNECTIONS) {
                    if (myDeviceName > peerName) {
                        val lastAttempt = connectionAttempts[macAddress] ?: 0L
                        if (System.currentTimeMillis() - lastAttempt > 5000) {
                            connectionAttempts[macAddress] = System.currentTimeMillis()
                            AppLogger.d("BLE_MESH", "Auto-connecting Persistent GATT to ${peerName}")
                            connectToPersistentGatt(macAddress, peerName)
                        }
                    }
                }
            }
        }
    }

    private fun connectToPersistentGatt(macAddress: String, peerName: String) {
        val device = bluetoothAdapter.getRemoteDevice(macAddress)
        
        device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    AppLogger.d("BLE_MESH", "GATT Socket locked with ${peerName}. Requesting MTU 512...")
                    activeConnections[macAddress] = gatt
                    pendingQueues[macAddress] = ConcurrentLinkedQueue<ByteArray>()
                    isWriting[macAddress] = AtomicBoolean(false)
                    chunkBuffers[macAddress] = ByteArray(0)
                    
                    handler.post {
                        onDeviceConnected?.invoke(ConnectedDevice(macAddress, peerName, isClassicConnected = true))
                    }
                    gatt.requestMtu(512)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    AppLogger.d("BLE_MESH", "GATT Socket disconnected from ${peerName}.")
                    activeConnections.remove(macAddress)
                    pendingQueues.remove(macAddress)
                    isWriting.remove(macAddress)
                    chunkBuffers.remove(macAddress)
                    
                    handler.post {
                        onDeviceConnected?.invoke(ConnectedDevice(macAddress, peerName, isClassicConnected = false))
                    }
                    gatt.close()
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    gatt.discoverServices()
                } else {
                    gatt.disconnect()
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
                        handler.postDelayed({
                            sendSystemPulse()
                            processNextPayload(macAddress)
                        }, 500)
                    }
                } else {
                    gatt.disconnect()
                }
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    handler.postDelayed({
                        sendSystemPulse()
                        processNextPayload(gatt.device.address)
                    }, 500)
                } else {
                    gatt.disconnect()
                }
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                val value = characteristic.value ?: return
                
                val currentBuffer = chunkBuffers[macAddress] ?: ByteArray(0)
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
                chunkBuffers[macAddress] = workingBuffer
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, status: Int) {
                isWriting[macAddress]?.set(false)
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    processNextPayload(macAddress)
                } else {
                    AppLogger.d("BLE_MESH", "GATT write failed for ${macAddress}. Status: ${status}")
                    gatt.disconnect()
                }
            }
        })
    }

    private fun processNextPayload(macAddress: String) {
        val writing = isWriting[macAddress] ?: return
        val queue = pendingQueues[macAddress] ?: return

        if (writing.compareAndSet(false, true)) {
            val payload = queue.poll()
            if (payload != null) {
                // Check if we are connected as a Client
                val gatt = activeConnections[macAddress]
                if (gatt != null) {
                    val service = gatt.getService(SERVICE_UUID)
                    val characteristic = service?.getCharacteristic(RX_CHARACTERISTIC_UUID)
                    if (characteristic != null) {
                        characteristic.value = payload
                        val success = gatt.writeCharacteristic(characteristic)
                        if (!success) {
                            writing.set(false)
                            gatt.disconnect()
                        }
                    } else {
                        writing.set(false)
                        gatt.disconnect()
                    }
                    return
                }
                
                // Check if we are connected as a Server
                val serverDevice = activeServerConnections[macAddress]
                val txChar = gattServer?.getService(SERVICE_UUID)?.getCharacteristic(TX_CHARACTERISTIC_UUID)
                if (serverDevice != null && txChar != null) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        gattServer?.notifyCharacteristicChanged(serverDevice, txChar, false, payload)
                    } else {
                        txChar.value = payload
                        gattServer?.notifyCharacteristicChanged(serverDevice, txChar, false)
                    }
                    // Notifications don't get an immediate callback for WRITE_NO_RESPONSE on all versions easily in this setup without onNotificationSent.
                    // We artificially delay slightly to prevent buffer overflow, then process next.
                    handler.postDelayed({
                        writing.set(false)
                        processNextPayload(macAddress)
                    }, 20)
                    return
                }
                
                writing.set(false)
            } else {
                writing.set(false)
            }
        }
    }

    private fun startGattServer() {
        val serverCallback = object : BluetoothGattServerCallback() {
            
            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                val macAddress = device.address
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    AppLogger.d("BLE_MESH", "Server: Device ${macAddress} connected.")
                    activeServerConnections[macAddress] = device
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    AppLogger.d("BLE_MESH", "Server: Device ${macAddress} disconnected.")
                    activeServerConnections.remove(macAddress)
                    handler.post {
                        onDeviceDisconnected?.invoke(macAddress)
                    }
                }
            }

            override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
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
                    
                    val currentBuffer = chunkBuffers[macAddress] ?: ByteArray(0)
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
                    
                    chunkBuffers[macAddress] = workingBuffer
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

        gattServer?.addService(service)
    }

    private fun processBinaryPayload(endpointId: String, payloadBytes: ByteArray) {
        try {
            val payload = kotlinx.serialization.protobuf.ProtoBuf.decodeFromByteArray(com.example.testresqmesh.core.network.MeshPayload.serializer(), payloadBytes)
            if (payload.senderName.isNotEmpty()) {
                connectedEndpointNames[endpointId] = payload.senderName
                handler.post {
                    onDeviceConnected?.invoke(com.example.testresqmesh.core.model.ConnectedDevice(endpointId, payload.senderName, true))
                }
            }
        } catch (e: Exception) {
            AppLogger.d("BLE_MESH", "Failed to decode payload for auto-rename: ${e.message}")
        }
        
        payloadDispatcher.dispatch(endpointId, payloadBytes)
    }

    fun broadcastPayload(payloadBytes: ByteArray, excludeEndpointId: String? = null) {
        cacheOutgoingMessageId(payloadBytes)
        val targets = mutableSetOf<String>()
        targets.addAll(activeConnections.keys)
        targets.addAll(activeServerConnections.keys)
        targets.remove(excludeEndpointId)
        
        targets.forEach { targetId ->
            sendDirectPayload(targetId, payloadBytes)
        }
    }

    fun sendDirectPayload(targetMacAddress: String, payloadBytes: ByteArray) {
        if (!BluetoothAdapter.checkBluetoothAddress(targetMacAddress)) return
        
        cacheOutgoingMessageId(payloadBytes)
        
        val fullData = ByteArray(4 + payloadBytes.size)
        val lengthBuffer = ByteBuffer.allocate(4).putInt(payloadBytes.size).array()
        System.arraycopy(lengthBuffer, 0, fullData, 0, 4)
        System.arraycopy(payloadBytes, 0, fullData, 4, payloadBytes.size)
        
        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < fullData.size) {
            val length = Math.min(500, fullData.size - offset)
            val chunk = ByteArray(length)
            System.arraycopy(fullData, offset, chunk, 0, length)
            chunks.add(chunk)
            offset += length
        }
        
        val isServerConnected = activeServerConnections.containsKey(targetMacAddress)
        val isClientConnected = activeConnections.containsKey(targetMacAddress)
        
        val queue = pendingQueues.getOrPut(targetMacAddress) { ConcurrentLinkedQueue<ByteArray>() }
        chunks.forEach { queue.add(it) }
        
        isWriting.putIfAbsent(targetMacAddress, AtomicBoolean(false))

        if (isServerConnected || isClientConnected) {
            processNextPayload(targetMacAddress)
        } else {
            if (activeConnections.size < MAX_CONNECTIONS) {
                connectToPersistentGatt(targetMacAddress, connectedEndpointNames[targetMacAddress] ?: "Unknown")
            } else {
                AppLogger.d("BLE_MESH", "Dropped payload for ${targetMacAddress} because GATT Mesh is at MAX_CONNECTIONS capacity.")
            }
        }
    }

    private fun cacheOutgoingMessageId(payloadBytes: ByteArray) {
        try {
            val payload = ProtoBuf.decodeFromByteArray<MeshPayload>(payloadBytes)
            if (payload.id.isNotEmpty()) seenMessageIds.add(payload.id)
        } catch (e: Exception) {}
    }

    fun disconnectFromEndpoint(endpointId: String) {
        activeConnections[endpointId]?.disconnect()
    }
    
    fun blockDevice(deviceName: String, sendNotification: Boolean = true) {}
    fun unblockDevice(deviceName: String) {}
    
    fun rescan() {
        bleScanner?.stopScan(scanCallback)
        startScanning()
    }
    
    fun forceConnectToDevice(endpointId: String, endpointName: String) {
        if (!activeConnections.containsKey(endpointId)) {
            connectToPersistentGatt(endpointId, endpointName)
        }
    }

    fun broadcastSeenReceipt(targetMessageId: String, isPrivate: Boolean, targetId: String? = null) {
        val payload = MeshPayload(
            id = UUID.randomUUID().toString(),
            type = "SEEN",
            targetMessageId = targetMessageId,
            reader = myDeviceName,
            isPrivate = isPrivate
        )
        val bytes = ProtoBuf.encodeToByteArray(payload)
        
        if (targetId != null) {
            sendDirectPayload(targetId, bytes)
        } else {
            broadcastPayload(bytes)
        }
    }

    fun broadcastDeliveredReceipt(targetMessageId: String, isPrivate: Boolean, targetId: String? = null, directedReturnRoute: List<String> = emptyList()) {
        val payload = MeshPayload(
            id = UUID.randomUUID().toString(),
            type = "DELIVERED",
            targetMessageId = targetMessageId,
            reader = myDeviceName,
            isPrivate = isPrivate,
            returnRoute = listOf(myDeviceName),
            directedRoute = directedReturnRoute
        )
        val bytes = ProtoBuf.encodeToByteArray(payload)
        
        if (targetId != null) {
            sendDirectPayload(targetId, bytes)
        } else {
            broadcastPayload(bytes)
        }
    }
}


