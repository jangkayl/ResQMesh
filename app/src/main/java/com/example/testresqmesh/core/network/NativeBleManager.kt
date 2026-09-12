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
    var onDeviceBlocked: ((String) -> Unit)? = null
    var onDeviceUnblocked: ((String) -> Unit)? = null

    var myDeviceName: String = "ResQMesh_Node"
    val myHex = java.util.UUID.randomUUID().toString().substring(0, 4).uppercase()

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val bleAdvertiser get() = bluetoothAdapter?.bluetoothLeAdvertiser
    private val bleScanner get() = bluetoothAdapter?.bluetoothLeScanner

    private val SERVICE_UUID = UUID.fromString("B9A34F5C-7462-4C61-8935-7C2D4A15A3E4") // ResQMesh Custom Service
    private val RX_CHARACTERISTIC_UUID = UUID.fromString("6A81C2E5-309F-4D88-B270-4A9A65D8B6C7")
    private val TX_CHARACTERISTIC_UUID = UUID.fromString("1E4D9C7B-6F2A-4B9E-981D-F8A32C5B4E10")
    private val CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB") // Standard CCCD (Required for Notifications)

    private var gattServer: BluetoothGattServer? = null
    
    private val connectedEndpointIds = mutableSetOf<String>()
    private val connectedEndpointNames = mutableMapOf<String, String>()
    private val seenMessageIds = java.util.LinkedHashSet<String>()
    
    private val isNodeActive = java.util.concurrent.atomic.AtomicBoolean(false)
    private val notificationHelper = NotificationHelper(context)
    private var currentTeamKey: String = ""
    private var isCloaked = false
    private val MAX_TOTAL_CONNECTIONS = 3 // MUST BE 3! If set to 1, it causes an infinite eviction loop.
    private val isConnecting = java.util.concurrent.atomic.AtomicBoolean(false)
    private val MAX_CONNECTIONS = 4
    private val activeConnections = ConcurrentHashMap<String, BluetoothGatt>()
    private val activeServerConnections = ConcurrentHashMap<String, BluetoothDevice>()
    private val pendingQueues = ConcurrentHashMap<String, ConcurrentLinkedQueue<ByteArray>>()
    private val isWriting = ConcurrentHashMap<String, AtomicBoolean>()
    private val chunkBuffers = ConcurrentHashMap<String, ByteArray>()
    private val connectionMtu = ConcurrentHashMap<String, Int>() // Stores incomplete binary payloads
    private val connectionAttempts = ConcurrentHashMap<String, Long>()
    private val connectionInteractionTimes = ConcurrentHashMap<String, Long>()
    private val blockedDevices = ConcurrentHashMap<String, Boolean>()
    private val orphanDetectionTime = ConcurrentHashMap<String, Long>()

    private val payloadDispatcherCallback = object : PayloadDispatcherCallback {
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
        override fun onDeviceNameSync(endpointId: String, realName: String) {
            // Deprecated: We now handle this safely in processBinaryPayload 
            // by enforcing routePath.isEmpty() to prevent relayed pulses from corrupting the routing table.
        }
        override fun onDeviceGoodbye(endpointId: String) {
            AppLogger.d("BLE_MESH", "Received GOODBYE packet from $endpointId. Disconnecting instantly.")
            this@NativeBleManager.disconnectFromEndpoint(endpointId)
            handler.post {
                this@NativeBleManager.onDeviceDisconnected?.invoke(endpointId)
            }
        }
        override fun onSosCancelled() { this@NativeBleManager.onSosCancelled?.invoke() }
        override fun isDeviceBlocked(deviceName: String) = blockedDevices[deviceName] == true
        override fun onDeviceBlocked(deviceName: String) { this@NativeBleManager.onDeviceBlocked?.invoke(deviceName) }
        override fun onDeviceUnblocked(deviceName: String) { this@NativeBleManager.onDeviceUnblocked?.invoke(deviceName) }
        override fun showNotification(sender: String, text: String) { notificationHelper.showPrivateMessageNotification(sender, text) }
        override fun showSosEmergencyNotification(sender: String, text: String) {
            if (!com.example.testresqmesh.MainActivity.isAppInForeground) {
                notificationHelper.showSosEmergencyNotification(sender, text)
            }
        }
    }
    
    private val payloadDispatcher = PayloadDispatcher(payloadDispatcherCallback)
    private val endpointLastSeen = mutableMapOf<String, Long>()
    private val endpointFirstSeen = mutableMapOf<String, Long>()
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
                    sendSystemPulse()
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

    private var connectingMacAddress: String? = null

    fun startMeshNode(teamKey: String) {
        currentTeamKey = teamKey
        isCloaked = false
        if (bleAdvertiser == null || bleScanner == null) {
            onStatusChanged?.invoke("Hardware not fully supported")
            return
        }
        isNodeActive.set(true)
        activeAdvertiseCallback = advertiseCallback
        instance = this
        
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
        endpointFirstSeen.clear()
        instance = null
        onStatusChanged?.invoke("Offline")
    }

    private fun getElectionScore(): String {
        // Master Election based on Device Specs (RAM + CPU Cores)
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        
        val ramGb = (memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)).toInt()
        val cores = Runtime.getRuntime().availableProcessors()
        
        // Formula: RAM (GB) * 10 + Cores. (e.g. 8GB RAM + 8 Cores = 88). Capped at 999.
        var specScore = (ramGb * 10) + cores
        if (specScore > 999) specScore = 999
        if (specScore < 0) specScore = 0
        
        return String.format("%03d%s", specScore, myHex.take(2)) // e.g. "0889F", sorts by Specs, breaks ties with Hex
    }

    private fun startAdvertising(teamKey: String) {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()
            
        val totalConnections = activeConnections.size + activeServerConnections.size
        val electionScore = getElectionScore()
        val combinedName = "$electionScore|$totalConnections|$myDeviceName"
        var nameBytes = combinedName.toByteArray(Charsets.UTF_8)
        if (nameBytes.size > 20) {
            nameBytes = nameBytes.sliceArray(0 until 20)
        }
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
            
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addManufacturerData(0xFFFF, nameBytes)
            .build()
            
        bleAdvertiser?.startAdvertising(settings, data, scanResponse, advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {}

    private fun updateInvisibilityCloak() {
        if (!isNodeActive.get() || currentTeamKey.isEmpty()) return
        try {
            bleAdvertiser?.stopAdvertising(advertiseCallback)
            startAdvertising(currentTeamKey)
        } catch (e: Exception) {}
    }

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
            val manufacturerData = result.scanRecord?.getManufacturerSpecificData(0xFFFF)
            if (manufacturerData == null) return
            
            val serviceDataStr = String(manufacturerData, Charsets.UTF_8).replace("\u0000", "").trim()
            val parts = serviceDataStr.split("|", limit = 3)
            
            // STRICT FILTER: If the advertisement does not perfectly match the ResQMesh signature, ignore it completely!
            if (parts.size != 3) {
                AppLogger.d("BLE_MESH", "Scanner: Ignored alien device ${device.address}. Invalid signature: $serviceDataStr")
                return
            }
            
            val peerScore = parts[0]
            val peerConnections = parts[1].toIntOrNull() ?: -1
            val peerName = parts[2]
            val macAddress = device.address

            if (blockedDevices[peerName] == true) {
                return
            }

            if (peerName != myDeviceName && peerName != myDeviceName.take(20)) {
                val now = System.currentTimeMillis()
                endpointLastSeen[macAddress] = now
                if (!endpointFirstSeen.containsKey(macAddress)) {
                    endpointFirstSeen[macAddress] = now
                }

                if (!connectedEndpointIds.contains(macAddress)) {
                    connectedEndpointIds.add(macAddress)
                    connectedEndpointNames[macAddress] = peerName
                    
                    handler.post {
                        onDeviceScanned?.invoke(macAddress, peerName, peerConnections, peerScore, false)
                        sendSystemPulse()
                    }
                }

                val isClient = activeConnections.keys.any { connectedEndpointNames[it] == peerName }
                val isServer = activeServerConnections.keys.any { connectedEndpointNames[it] == peerName }
                val isAlreadyConnected = isClient || isServer || activeConnections.containsKey(macAddress) || activeServerConnections.containsKey(macAddress)

                if (!isAlreadyConnected) {
                    val totalConnections = activeConnections.size + activeServerConnections.size
                    
                    if (totalConnections >= MAX_TOTAL_CONNECTIONS || (totalConnections >= 2 && peerConnections > 0)) {
                        if (peerConnections == 0 && totalConnections >= MAX_TOTAL_CONNECTIONS) {
                            if (!orphanDetectionTime.containsKey(macAddress)) {
                                orphanDetectionTime[macAddress] = now
                            } else {
                                if (now - (orphanDetectionTime[macAddress] ?: now) > 5000) {
                                    AppLogger.d("BLE_MESH", "Orphan Preemption: Found orphan $peerName. Dropping weakest link to rescue.")
                                    val lruMac = connectionInteractionTimes
                                        .filterKeys { activeConnections.containsKey(it) }
                                        .filterKeys { pendingQueues[it]?.isEmpty() != false } // QA FIX: Only evict idle connections
                                        .minByOrNull { it.value }?.key
                                        
                                    if (lruMac != null) {
                                        activeConnections[lruMac]?.disconnect()
                                        activeConnections[lruMac]?.close()
                                        activeConnections.remove(lruMac)
                                        pendingQueues.remove(lruMac)
                                        isWriting.remove(lruMac)
                                        chunkBuffers.remove(lruMac)
                                        connectionInteractionTimes.remove(lruMac)
                                        handler.post {
                                            onDeviceDisconnected?.invoke(lruMac)
                                        }
                                        connectToPersistentGatt(macAddress, peerName)
                                    }
                                }
                            }
                        } else {
                            orphanDetectionTime.remove(macAddress)
                        }
                    } else {
                        val lastAttempt = connectionAttempts[macAddress] ?: 0L
                        if (now - lastAttempt > 5000) {
                            connectionAttempts[macAddress] = now
                            
                            val myScore = getElectionScore()
                            if (peerScore.isNotEmpty() && myScore > peerScore) {
                                AppLogger.d("BLE_MESH", "Battery Master Election: $myScore > $peerScore. Initiating connection with jitter.")
                                handler.postDelayed({
                                    connectToPersistentGatt(macAddress, peerName)
                                }, (100L..1000L).random())
                            } else if (peerScore.isEmpty()) {
                                if (myDeviceName > peerName) {
                                    AppLogger.d("BLE_MESH", "Legacy Alphabetical Rule: $myDeviceName > $peerName. Initiating connection.")
                                    connectToPersistentGatt(macAddress, peerName)
                                }
                            } else {
                                AppLogger.d("BLE_MESH", "Battery Master Election: $myScore <= $peerScore. Yielding.")
                                // QA FIX: If the Master fails to initiate, the Slave steps up after 3 seconds!
                                handler.postDelayed({
                                    if (!activeServerConnections.containsKey(macAddress) && !activeConnections.containsKey(macAddress)) {
                                        connectToPersistentGatt(macAddress, peerName)
                                    }
                                }, 3000)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun connectToPersistentGatt(macAddress: String, peerName: String) {
        if (!isConnecting.compareAndSet(false, true)) {
            AppLogger.d("BLE_MESH", "Already connecting to another device. Queuing connection to $peerName for later.")
            connectionAttempts.remove(macAddress) // QA FIX: Allow immediate retry next scan
            return
        }
        
        connectingMacAddress = macAddress
        handler.post {
            // Force UI update to show SYNCING...
            onDeviceScanned?.invoke(macAddress, peerName, 0, "", true)
        }

        val device = bluetoothAdapter.getRemoteDevice(macAddress)
        
        val timeoutHandler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            if (isConnecting.get()) {
                AppLogger.d("BLE_MESH", "GATT Connection timed out after 15s. Forcing lock release for long-distance retry.")
                isConnecting.set(false)
                val oldMac = connectingMacAddress
                connectingMacAddress = null
                if (oldMac != null) {
                    handler.post { onDeviceScanned?.invoke(oldMac, peerName, 0, "", false) }
                }
            }
        }
        try {
            val callback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        AppLogger.d("BLE_MESH", "GATT Socket locked with ${peerName}. Requesting MTU 512...")
                        activeConnections[macAddress] = gatt
                        connectedEndpointNames[macAddress] = peerName // QA FIX: Register name instantly to prevent MAC rotation loops
                        pendingQueues.putIfAbsent(macAddress, ConcurrentLinkedQueue<ByteArray>())
                        isWriting.putIfAbsent(macAddress, AtomicBoolean(false))
                        chunkBuffers.putIfAbsent(macAddress, ByteArray(0))
                        connectionInteractionTimes.putIfAbsent(macAddress, System.currentTimeMillis())
                        
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
                        if (connectingMacAddress == macAddress) {
                            connectingMacAddress = null
                            handler.post { onDeviceScanned?.invoke(macAddress, peerName, 0, "", false) }
                        }
                        isConnecting.set(false)
                        activeConnections.remove(macAddress)
                        pendingQueues.remove(macAddress)
                        isWriting.remove(macAddress)
                        chunkBuffers.remove(macAddress)
                        updateInvisibilityCloak()
                        connectedEndpointIds.remove(macAddress)
                        connectedEndpointNames.remove(macAddress)
                        
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
                        connectionMtu[mac] = mtu - 3
                        gatt.discoverServices()
                    } else {
                        AppLogger.d("BLE_MESH", "MTU Expansion failed. Samsung Fallback to 23 bytes.")
                        connectionMtu[mac] = 20
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
                            if (connectingMacAddress == macAddress) {
                                connectingMacAddress = null
                                handler.post { onDeviceScanned?.invoke(macAddress, "", 0, "", false) }
                            }
                            isConnecting.set(false)
                            forceGattDisconnect(macAddress, gatt)
                        } else {
                            // QA FIX: Send the SYSTEM pulse immediately after requesting notifications. 
                            // Do not wait for onDescriptorWrite because some Android OEMs drop the callback!
                            handler.postDelayed({
                                isConnecting.set(false)
                                sendSystemPulse()
                                processNextPayload(gatt.device.address)
                            }, 500)
                        }
                    } else {
                        AppLogger.d("BLE_MESH", "GATT services discovery failed for ${macAddress}. Status: ${status}. Forcing UI disconnect.")
                        if (connectingMacAddress == macAddress) {
                            connectingMacAddress = null
                            handler.post { onDeviceScanned?.invoke(macAddress, "", 0, "", false) }
                        }
                        isConnecting.set(false)
                        forceGattDisconnect(macAddress, gatt)
                    }
                }
    
                override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        AppLogger.d("BLE_MESH", "GATT descriptor written successfully for ${macAddress}.")
                        if (connectingMacAddress == macAddress) {
                            connectingMacAddress = null
                            handler.post { onDeviceScanned?.invoke(macAddress, "", 0, "", false) }
                        }
                        isConnecting.set(false)
                    } else {
                        AppLogger.d("BLE_MESH", "GATT descriptor write failed for ${macAddress}. Status: ${status}. Forcing UI disconnect.")
                        if (connectingMacAddress == macAddress) {
                            connectingMacAddress = null
                            handler.post { onDeviceScanned?.invoke(macAddress, "", 0, "", false) }
                        }
                        isConnecting.set(false)
                        forceGattDisconnect(macAddress, gatt)
                    }
                }
    
                override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                    val value = characteristic.value ?: return
                    connectionInteractionTimes[macAddress] = System.currentTimeMillis()
                    
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
            if (connectingMacAddress == macAddress) {
                connectingMacAddress = null
                handler.post { onDeviceScanned?.invoke(macAddress, peerName, 0, "", false) }
            }
            isConnecting.set(false)
            timeoutHandler.removeCallbacks(timeoutRunnable)
        }
    }

    private val writeFailureCount = java.util.concurrent.ConcurrentHashMap<String, Int>()

    private fun processNextPayload(macAddress: String) {
        val writing = isWriting[macAddress] ?: return
        val queue = pendingQueues[macAddress] ?: return

        if (writing.compareAndSet(false, true)) {
            val payload = queue.poll()
            if (payload != null) {
                connectionInteractionTimes[macAddress] = System.currentTimeMillis()
                
                // Check if we are connected as a Client
                val gatt = activeConnections[macAddress]
                if (gatt != null) {
                    val service = gatt.getService(SERVICE_UUID)
                    val characteristic = service?.getCharacteristic(RX_CHARACTERISTIC_UUID)
                    if (characteristic != null) {
                        val isSuccess = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            gatt.writeCharacteristic(characteristic, payload, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) == android.bluetooth.BluetoothStatusCodes.SUCCESS
                        } else {
                            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                            characteristic.value = payload
                            gatt.writeCharacteristic(characteristic)
                        }
                        if (!isSuccess) {
                            val failures = (writeFailureCount[macAddress] ?: 0) + 1
                            writeFailureCount[macAddress] = failures
                            if (failures >= 5) {
                                AppLogger.d("BLE_MESH", "GATT write failed 5 times for $macAddress. Assuming Zombie Socket. Forcing UI disconnect.")
                                writing.set(false)
                                writeFailureCount.remove(macAddress)
                                forceGattDisconnect(macAddress, gatt)
                            } else {
                                AppLogger.d("BLE_MESH", "GATT write busy for $macAddress. Retrying... ($failures/5)")
                                val newQueue = java.util.concurrent.ConcurrentLinkedQueue<ByteArray>()
                                newQueue.add(payload)
                                newQueue.addAll(queue)
                                pendingQueues[macAddress] = newQueue
                                handler.postDelayed({
                                    writing.set(false)
                                    processNextPayload(macAddress)
                                }, 200)
                            }
                        } else {
                            writeFailureCount.remove(macAddress)
                            // Safety net for WRITE_NO_RESPONSE missing callbacks on older OS versions
                            handler.postDelayed({
                                writing.set(false)
                                processNextPayload(macAddress)
                            }, 30)
                        }
                    } else {
                        AppLogger.d("BLE_MESH", "GATT characteristics missing for $macAddress. Forcing UI disconnect.")
                        writing.set(false)
                        forceGattDisconnect(macAddress, gatt)
                    }
                    return
                }
                
                // Check if we are connected as a Server
                val serverDevice = activeServerConnections[macAddress]
                val txChar = gattServer?.getService(SERVICE_UUID)?.getCharacteristic(TX_CHARACTERISTIC_UUID)
                if (serverDevice != null && txChar != null) {
                    val isSuccess = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        gattServer?.notifyCharacteristicChanged(serverDevice, txChar, false, payload) == android.bluetooth.BluetoothStatusCodes.SUCCESS
                    } else {
                        txChar.value = payload
                        gattServer?.notifyCharacteristicChanged(serverDevice, txChar, false) == true
                    }
                    
                    if (!isSuccess) {
                        val failures = (writeFailureCount[macAddress] ?: 0) + 1
                        writeFailureCount[macAddress] = failures
                        if (failures >= 5) {
                            AppLogger.d("BLE_MESH", "Server GATT notify failed 5 times for $macAddress. Assuming dead link.")
                            writing.set(false)
                            writeFailureCount.remove(macAddress)
                            gattServer?.cancelConnection(serverDevice)
                        } else {
                            val newQueue = java.util.concurrent.ConcurrentLinkedQueue<ByteArray>()
                            newQueue.add(payload)
                            newQueue.addAll(queue)
                            pendingQueues[macAddress] = newQueue
                            handler.postDelayed({
                                writing.set(false)
                                processNextPayload(macAddress)
                            }, 200)
                        }
                    } else {
                        writeFailureCount.remove(macAddress)
                        handler.postDelayed({
                            writing.set(false)
                            processNextPayload(macAddress)
                        }, 20)
                    }
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
                    val peerName = connectedEndpointNames[macAddress]
                    if (peerName != null && blockedDevices[peerName] == true) {
                        AppLogger.d("BLE_MESH", "Server: Rejected blocked device ${peerName}.")
                        gattServer?.cancelConnection(device)
                        return
                    }
                    val totalConnections = activeConnections.size + activeServerConnections.size
                    if (totalConnections >= MAX_TOTAL_CONNECTIONS) {
                        AppLogger.d("BLE_MESH", "Server: Rejected connection from ${device.address}. Mesh node is full.")
                        gattServer?.cancelConnection(device)
                        return
                    }
                    AppLogger.d("BLE_MESH", "Server: Device ${macAddress} connected.")
                    activeServerConnections[macAddress] = device
                    pendingQueues.putIfAbsent(macAddress, ConcurrentLinkedQueue<ByteArray>())
                    isWriting.putIfAbsent(macAddress, AtomicBoolean(false))
                    chunkBuffers.putIfAbsent(macAddress, ByteArray(0))
                    connectionInteractionTimes.putIfAbsent(macAddress, System.currentTimeMillis())
                    
                    val safePeerName = peerName ?: "Unknown Node"
                    connectedEndpointNames[macAddress] = safePeerName
                    
                    handler.postDelayed({
                        updateInvisibilityCloak()
                    }, 2000)
                    
                    if (safePeerName == "Unknown Node") {
                        AppLogger.d("BLE_MESH", "Server: Alien device connected. Waiting 5s for SYSTEM pulse handshake...")
                        handler.postDelayed({
                            if (connectedEndpointNames[macAddress] == "Unknown Node") {
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
                    activeServerConnections.remove(macAddress)
                    connectedEndpointIds.remove(macAddress)
                    connectedEndpointNames.remove(macAddress)
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
                connectionMtu[device.address] = mtu - 3
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
                    connectionInteractionTimes[macAddress] = System.currentTimeMillis()
                    
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
            
            // Only auto-rename the physical socket if this is a direct SYSTEM pulse (not relayed)
            if (payload.type == "SYSTEM" && payload.routePath.isEmpty() && payload.senderName.isNotEmpty()) {
                val oldName = connectedEndpointNames[endpointId]
                if (oldName == null || oldName.contains("Unknown")) {
                    AppLogger.d("BLE_MESH", "Auto-rename: $endpointId is now ${payload.senderName}")
                    connectedEndpointNames[endpointId] = payload.senderName
                    handler.post {
                        val isClient = activeConnections.containsKey(endpointId)
                        onDeviceConnected?.invoke(com.example.testresqmesh.core.model.ConnectedDevice(endpointId, payload.senderName, isClient))
                        sendSystemPulse()
                    }
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
            val chunkSize = connectionMtu[targetMacAddress] ?: 20
            val length = Math.min(chunkSize, fullData.size - offset)
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
            if (activeConnections.size >= MAX_CONNECTIONS) {
                // VIP BOUNCER (LRU EVICTION)
                val lruMac = connectionInteractionTimes
                    .filterKeys { activeConnections.containsKey(it) }
                    .filterKeys { pendingQueues[it]?.isEmpty() != false } // QA FIX
                    .minByOrNull { it.value }?.key
                    
                val macToEvict = lruMac ?: activeConnections.keys.firstOrNull { pendingQueues[it]?.isEmpty() != false }
                
                if (macToEvict != null) {
                    AppLogger.d("BLE_MESH", "Evicting $macToEvict to make room for VIP connection to $targetMacAddress")
                    activeConnections[macToEvict]?.disconnect()
                    activeConnections[macToEvict]?.close()
                    activeConnections.remove(macToEvict)
                    pendingQueues.remove(macToEvict)
                    isWriting.remove(macToEvict)
                    chunkBuffers.remove(macToEvict)
                    connectionInteractionTimes.remove(macToEvict)
                    handler.post {
                        onDeviceDisconnected?.invoke(macToEvict)
                    }
                } else {
                    AppLogger.d("BLE_MESH", "VIP Bouncer failed: Cannot evict any connections because all are actively transmitting.")
                    return // Abort connecting to the new node to protect current data streams
                }
            }
            connectToPersistentGatt(targetMacAddress, connectedEndpointNames[targetMacAddress] ?: "Unknown")
        }
    }

    private fun cacheOutgoingMessageId(payloadBytes: ByteArray) {
        try {
            val payload = ProtoBuf.decodeFromByteArray<MeshPayload>(payloadBytes)
            if (payload.id.isNotEmpty()) seenMessageIds.add(payload.id)
        } catch (e: Exception) {}
    }

    private fun forceGattDisconnect(macAddress: String, gatt: BluetoothGatt?) {
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (e: Exception) { }
        
        activeConnections.remove(macAddress)
        pendingQueues.remove(macAddress)
        isWriting.remove(macAddress)
        chunkBuffers.remove(macAddress)
        connectedEndpointIds.remove(macAddress)
        connectedEndpointNames.remove(macAddress)
        
        handler.post {
            onDeviceDisconnected?.invoke(macAddress)
            sendSystemPulse()
        }
    }

    fun disconnectFromEndpoint(endpointId: String) {
        activeConnections[endpointId]?.disconnect()
        activeServerConnections[endpointId]?.let { device ->
            gattServer?.cancelConnection(device)
        }
    }
    
    fun blockDevice(deviceName: String, sendNotification: Boolean = true) {
        blockedDevices[deviceName] = true
        // Find and disconnect if currently connected
        val macAddress = connectedEndpointNames.entries.find { it.value == deviceName }?.key
        if (macAddress != null) {
            disconnectFromEndpoint(macAddress)
            connectedEndpointIds.remove(macAddress)
            connectedEndpointNames.remove(macAddress)
            handler.post {
                onDeviceDisconnected?.invoke(macAddress)
                sendSystemPulse()
            }
        }
    }
    fun unblockDevice(deviceName: String) {
        blockedDevices.remove(deviceName)
    }
    
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


