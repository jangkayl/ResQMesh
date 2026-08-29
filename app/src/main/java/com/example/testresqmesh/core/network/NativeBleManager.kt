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
    private val bleAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
    private val bleScanner = bluetoothAdapter?.bluetoothLeScanner

    private val SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
    private val RX_CHARACTERISTIC_UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")

    private var gattServer: BluetoothGattServer? = null
    
    private val connectedEndpointIds = mutableSetOf<String>()
    private val connectedEndpointNames = mutableMapOf<String, String>()
    private val seenMessageIds = java.util.LinkedHashSet<String>()
    
    private val isNodeActive = java.util.concurrent.atomic.AtomicBoolean(false)
    private val notificationHelper = NotificationHelper(context)
    
    private val MAX_CONNECTIONS = 4
    private val activeConnections = ConcurrentHashMap<String, BluetoothGatt>()
    private val pendingQueues = ConcurrentHashMap<String, ConcurrentLinkedQueue<ByteArray>>()
    private val isWriting = ConcurrentHashMap<String, AtomicBoolean>()
    private val chunkBuffers = ConcurrentHashMap<String, ByteArray>() // Stores incomplete binary payloads

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
                
                if (activeConnections.containsKey(macAddress)) {
                    entry.setValue(now)
                    continue
                }
                
                if (now - entry.value > 15000) { 
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
    }

    fun startMeshNode(teamKey: String) {
        if (bleAdvertiser == null || bleScanner == null) {
            onStatusChanged?.invoke("Hardware not fully supported")
            return
        }
        isNodeActive.set(true)
        activeAdvertiseCallback = advertiseCallback
        
        context.startService(android.content.Intent(context, BleCleanupService::class.java))
        
        startGattServer()
        startAdvertising(teamKey)
        startScanning()
        handler.post(timeoutRunnable)
        onStatusChanged?.invoke("Mesh Active [Persistent GATT/Protobuf]. Seeking peers...")
    }
    
    fun stopMeshNode() {
        isNodeActive.set(false)
        bleAdvertiser?.stopAdvertising(advertiseCallback)
        bleScanner?.stopScan(scanCallback)
        gattServer?.close()
        activeConnections.values.forEach { it.disconnect(); it.close() }
        activeConnections.clear()
        pendingQueues.clear()
        isWriting.clear()
        handler.removeCallbacks(timeoutRunnable)
        connectedEndpointIds.clear()
        connectedEndpointNames.clear()
        endpointLastSeen.clear()
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
            val peerName = serviceData?.let { String(it, Charsets.UTF_8) } ?: device.name ?: "Unknown Node"
val macAddress = device.address

            if (peerName != myDeviceName) {
                endpointLastSeen[macAddress] = System.currentTimeMillis()

                if (!connectedEndpointIds.contains(macAddress)) {
                    connectedEndpointIds.add(macAddress)
                    connectedEndpointNames[macAddress] = peerName
                    
                    onDeviceScanned?.invoke(macAddress, peerName, 50, "MEMBER", false)
                    onDeviceConnected?.invoke(ConnectedDevice(macAddress, peerName, isClassicConnected = false))
                    
                    if (!activeConnections.containsKey(macAddress) && activeConnections.size < MAX_CONNECTIONS) {
                        if (myDeviceName > peerName) {
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
                    processNextPayload(macAddress)
                } else {
                    gatt.disconnect()
                }
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
        val gatt = activeConnections[macAddress] ?: return
        val writing = isWriting[macAddress] ?: return
        val queue = pendingQueues[macAddress] ?: return

        if (writing.compareAndSet(false, true)) {
            val payload = queue.poll()
            if (payload != null) {
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
            } else {
                writing.set(false)
            }
        }
    }

    private fun startGattServer() {
        val serverCallback = object : BluetoothGattServerCallback() {
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
        gattServer?.addService(service)
    }

    private fun processBinaryPayload(endpointId: String, payloadBytes: ByteArray) {
        try {
            val payload = kotlinx.serialization.protobuf.ProtoBuf.decodeFromByteArray(com.example.testresqmesh.core.network.MeshPayload.serializer(), payloadBytes)
            val currentName = connectedEndpointNames[endpointId]
            if (payload.senderName.isNotEmpty() && currentName != payload.senderName) {
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
        val targets = connectedEndpointIds.filter { it != excludeEndpointId }
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
        
        val queue = pendingQueues[targetMacAddress]
        if (queue != null && activeConnections.containsKey(targetMacAddress)) {
            chunks.forEach { queue.add(it) }
            processNextPayload(targetMacAddress)
        } else {
            if (activeConnections.size < MAX_CONNECTIONS) {
                val newQueue = pendingQueues.getOrPut(targetMacAddress) { ConcurrentLinkedQueue<ByteArray>() }
                chunks.forEach { newQueue.add(it) }
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


