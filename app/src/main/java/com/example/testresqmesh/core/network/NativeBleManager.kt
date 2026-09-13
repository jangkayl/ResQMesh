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
class NativeBleManager(val context: Context) {
    val gattServerManager = com.example.testresqmesh.core.network.bluetooth.gatt.GattServerManager(context, this)
    val gattClientManager = com.example.testresqmesh.core.network.bluetooth.gatt.GattClientManager(context, this)
    val store = com.example.testresqmesh.core.network.bluetooth.state.BleStateStore()
    var onDeviceConnected: ((ConnectedDevice) -> Unit)? = null
    var onDeviceDisconnected: ((String) -> Unit)? = null
    var onDeviceScanned: ((String, String, Int, String, Boolean) -> Unit)? = null
    var onDeviceScanRemoved: ((String) -> Unit)? = null
    var onMessageReceived: ((String, String, String, String, Boolean, Boolean, String?, String?, Double?, Double?, String, List<String>, String) -> Unit)? = null
    var onMessageSeen: ((String, String) -> Unit)? = null
    var stpNeighborsProvider: (() -> Set<String>)? = null
    var onLiveAudioChunk: ((String, String, ByteArray) -> Unit)? = null
    var onMessageDelivered: ((String, String, List<String>) -> Unit)? = null
    var onPublicKeyReceived: ((String, String) -> Unit)? = null
    var onRoutingTableReceived: ((String, List<String>) -> Unit)? = null
    var onSosCancelled: (() -> Unit)? = null
    var onStatusChanged: ((String) -> Unit)? = null
    var onDeviceBlocked: ((String) -> Unit)? = null
    var onDeviceUnblocked: ((String) -> Unit)? = null
    var checkRouteExists: ((String) -> Boolean)? = null

    var myDeviceName: String = "ResQMesh_Node"
    val myHex = java.util.UUID.randomUUID().toString().substring(0, 4).uppercase()

    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter = bluetoothManager.adapter
    val bleAdvertiser get() = bluetoothAdapter?.bluetoothLeAdvertiser
    val bleScanner get() = bluetoothAdapter?.bluetoothLeScanner

    val SERVICE_UUID = UUID.fromString("B9A34F5C-7462-4C61-8935-7C2D4A15A3E4") // ResQMesh Custom Service
    val RX_CHARACTERISTIC_UUID = UUID.fromString("6A81C2E5-309F-4D88-B270-4A9A65D8B6C7")
    val TX_CHARACTERISTIC_UUID = UUID.fromString("1E4D9C7B-6F2A-4B9E-981D-F8A32C5B4E10")
    val L2CAP_PSM_CHARACTERISTIC_UUID = UUID.fromString("8C91321D-4A22-4215-99A1-3E2A15C81F4B") // Exposes dynamic L2CAP Port
    val CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB") // Standard CCCD (Required for Notifications)

    var gattServer: BluetoothGattServer? = null
    var l2capServerSocket: android.bluetooth.BluetoothServerSocket? = null
    var myL2capPsm: Int = 0
    var l2capAcceptThread: Thread? = null
    
    
    val notificationHelper = NotificationHelper(context)
    var currentTeamKey: String = ""
    var isCloaked = false
    var MAX_TOTAL_CONNECTIONS = 3 // Dynamically scales down in dense rooms
    val MAX_CONNECTIONS = 4

    val payloadDispatcherCallback = object : PayloadDispatcherCallback {
        override fun getMyDeviceName() = myDeviceName
        override fun getSeenMessageIds() = store.seenMessageIds
        override fun getEndpointMedium(endpointId: String) = "Persistent BLE Mesh"
        override fun getConnectedEndpointIdByName(name: String) = store.connectedEndpointNames.entries.find { it.value == name }?.key
        override fun getStpNeighbors(): Set<String> {
            return this@NativeBleManager.stpNeighborsProvider?.invoke() ?: emptySet()
        }
        override fun sendDirectPayload(endpointId: String, payload: ByteArray) = this@NativeBleManager.sendDirectPayload(endpointId, payload)
        override fun broadcastPayload(payload: ByteArray, excludeEndpointId: String?) = this@NativeBleManager.broadcastPayload(payload, excludeEndpointId)
        override fun onMessageSeen(msgId: String, readerName: String) { onMessageSeen?.invoke(msgId, readerName) }
        override fun onMessageDelivered(msgId: String, readerName: String, returnRoute: List<String>) { onMessageDelivered?.invoke(msgId, readerName, returnRoute) }
        override fun onPublicKeyReceived(senderName: String, key: String) { onPublicKeyReceived?.invoke(senderName, key) }
        override fun onRoutingTableReceived(senderName: String, connectedNodes: List<String>) { onRoutingTableReceived?.invoke(senderName, connectedNodes) }
        override fun onMessageReceived(endpointId: String, msgId: String, senderName: String, text: String, isPrivate: Boolean, isSystem: Boolean, imageBase64: String?, audioBase64: String?, locationLat: Double?, locationLng: Double?, medium: String, routePath: List<String>, channelId: String) {
            this@NativeBleManager.onMessageReceived?.invoke(endpointId, msgId, senderName, text, isPrivate, isSystem, imageBase64, audioBase64, locationLat, locationLng, medium, routePath, channelId)
        }
        override fun onLiveAudioChunk(sender: String, channelId: String, chunk: ByteArray) {
            this@NativeBleManager.onLiveAudioChunk?.invoke(sender, channelId, chunk)
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
        override fun isDeviceBlocked(deviceName: String) = store.blockedDevices[deviceName] == true
        override fun onDeviceBlocked(deviceName: String) { this@NativeBleManager.onDeviceBlocked?.invoke(deviceName) }
        override fun onDeviceUnblocked(deviceName: String) { this@NativeBleManager.onDeviceUnblocked?.invoke(deviceName) }
        override fun showNotification(sender: String, text: String) { notificationHelper.showPrivateMessageNotification(sender, text) }
        override fun showSosEmergencyNotification(sender: String, text: String) {
            if (!com.example.testresqmesh.MainActivity.isAppInForeground) {
                notificationHelper.showSosEmergencyNotification(sender, text)
            }
        }
    }
    
    val payloadDispatcher = PayloadDispatcher(payloadDispatcherCallback)
    val handler = Handler(Looper.getMainLooper())
    val timeoutRunnable = object : Runnable {
        override fun run() {
            if (!store.isNodeActive.get()) return
            val now = System.currentTimeMillis()
            val iterator = store.endpointLastSeen.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val macAddress = entry.key
                
                val isClient = store.activeConnections.containsKey(macAddress)
                val isServer = store.activeServerConnections.containsKey(macAddress)
                
                if (isClient || isServer) {
                    val lastInteraction = store.connectionInteractionTimes[macAddress] ?: now
                    if (now - lastInteraction > 20000) { // 20s without a SYSTEM pulse means dead link
                        AppLogger.d("BLE_MESH", "Zombie Socket Detected! No data from $macAddress for 20s. Forcing disconnect.")
                        store.activeConnections[macAddress]?.let { forceGattDisconnect(macAddress, it) }
                        store.activeServerConnections[macAddress]?.let { gattServer?.cancelConnection(it) }
                    } else {
                        store.endpointLastSeen[macAddress] = now // Keep alive in discovery list
                    }
                    continue
                }
                
                if (now - entry.value > 15000) { // Increased timeout to 15s to allow watchdog to rescue
                    store.connectedEndpointIds.remove(macAddress)
                    store.connectedEndpointNames.remove(macAddress)
                    store.endpointLastSeen.remove(macAddress)
                    AppLogger.d("BLE_MESH", "Node Timed Out: ${macAddress}")
                    onDeviceDisconnected?.invoke(macAddress)
                    onDeviceScanRemoved?.invoke(macAddress)
                    sendSystemPulse()
                }
            }

            // WATCHDOG RECONNECTION LOOP: Bypass Scanner Throttling Deadlock
            val totalConns = store.activeConnections.size + store.activeServerConnections.size
            if (totalConns < MAX_TOTAL_CONNECTIONS) {
                val disconnectedMacs = store.endpointLastSeen.keys.filter { 
                    !store.activeConnections.containsKey(it) && !store.activeServerConnections.containsKey(it) && store.blockedDevices[store.connectedEndpointNames[it] ?: ""] != true
                }
                val bestMac = disconnectedMacs.maxByOrNull { store.endpointLastScore[it] ?: "" }
                if (bestMac != null) {
                    val lastAttempt = store.connectionAttempts[bestMac] ?: 0L
                    if (now - lastAttempt > 6000) {
                        AppLogger.d("BLE_MESH", "Watchdog: Bypassing Scanner Throttle to forcefully rescue $bestMac")
                        store.connectionAttempts[bestMac] = now
                        gattClientManager.connectToPersistentGatt(bestMac, store.connectedEndpointNames[bestMac] ?: "Unknown")
                    }
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
        currentTeamKey = teamKey
        isCloaked = false
        if (bleAdvertiser == null || bleScanner == null) {
            onStatusChanged?.invoke("Hardware not fully supported")
            return
        }
        store.isNodeActive.set(true)
        activeAdvertiseCallback = advertiseCallback
        instance = this
        
        startGattServer()
        startAdvertising(teamKey)
        startScanning()
        handler.post(timeoutRunnable)
        onStatusChanged?.invoke("Mesh Active [Persistent GATT/Protobuf]. Seeking peers...")
    }
    
    
    fun sendSystemPulse() {
        if (!store.isNodeActive.get()) return
        try {
            val pulseId = java.util.UUID.randomUUID().toString()
            val payload = com.example.testresqmesh.core.network.MeshPayload(
                id = pulseId,
                type = "SYSTEM",
                senderName = myDeviceName,
                connectedNodes = store.connectedEndpointNames.values.toList(),
                publicKey = com.example.testresqmesh.core.network.CryptoManager.getMyPublicKeyBase64()
            )
            val payloadBytes = kotlinx.serialization.protobuf.ProtoBuf.encodeToByteArray(com.example.testresqmesh.core.network.MeshPayload.serializer(), payload)
            broadcastPayload(payloadBytes)
        } catch (e: Exception) {
            AppLogger.d("BLE_MESH", "Failed to send system pulse: ${e.message}")
        }
    }

    fun stopMeshNode() {
        store.isNodeActive.set(false)
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
        
        store.activeServerConnections.values.forEach { gattServer?.cancelConnection(it) }
        store.activeServerConnections.clear()
        gattServer?.close()
        
        try {
            l2capServerSocket?.close()
            l2capAcceptThread?.interrupt()
        } catch (e: Exception) {}
        
        store.activeConnections.values.forEach { it.disconnect(); it.close() }
        store.activeConnections.clear()
        store.pendingQueues.clear()
        store.isWriting.clear()
        handler.removeCallbacks(timeoutRunnable)
        store.connectedEndpointIds.clear()
        store.connectedEndpointNames.clear()
        store.endpointLastSeen.clear()
        store.endpointFirstSeen.clear()
        instance = null
        onStatusChanged?.invoke("Offline")
    }

    fun getElectionScore(): String {
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

    fun startAdvertising(teamKey: String) {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()
            
        val totalConnections = store.activeConnections.size + store.activeServerConnections.size
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

    val advertiseCallback = object : AdvertiseCallback() {}

    fun updateInvisibilityCloak() {
        if (!store.isNodeActive.get() || currentTeamKey.isEmpty()) return
        try {
            bleAdvertiser?.stopAdvertising(advertiseCallback)
            startAdvertising(currentTeamKey)
        } catch (e: Exception) {}
    }

    fun startScanning() {
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build())
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
            
        bleScanner?.startScan(filters, settings, scanCallback)
    }

    val scanCallback = object : ScanCallback() {
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

            if (store.blockedDevices[peerName] == true) {
                return
            }

            if (peerName != myDeviceName && peerName != myDeviceName.take(20)) {
                val now = System.currentTimeMillis()
                store.endpointLastSeen[macAddress] = now
                store.endpointLastScore[macAddress] = peerScore
                if (!store.endpointFirstSeen.containsKey(macAddress)) {
                    store.endpointFirstSeen[macAddress] = now
                }

                if (!store.connectedEndpointIds.contains(macAddress)) {
                    store.connectedEndpointIds.add(macAddress)
                    store.connectedEndpointNames[macAddress] = peerName
                    
                    handler.post {
                        onDeviceScanned?.invoke(macAddress, peerName, peerConnections, peerScore, false)
                        sendSystemPulse()
                    }
                }

                val isClient = store.activeConnections.keys.any { store.connectedEndpointNames[it] == peerName }
                val isServer = store.activeServerConnections.keys.any { store.connectedEndpointNames[it] == peerName }
                val isAlreadyConnected = isClient || isServer || store.activeConnections.containsKey(macAddress) || store.activeServerConnections.containsKey(macAddress)
                val hasIndirectRoute = checkRouteExists?.invoke(peerName) == true

                if (!isAlreadyConnected && !hasIndirectRoute) {
                // SELF-LIMITING (LEAF NODE) TOPOLOGY: Prevent dense cluster cliques
                val now = System.currentTimeMillis()
                val activePeersNearby = store.endpointLastSeen.count { now - it.value < 20000 }
                if (activePeersNearby >= 5) {
                    MAX_TOTAL_CONNECTIONS = 2 // Restrict to a Spanning Tree Chain
                } else {
                    MAX_TOTAL_CONNECTIONS = 3 // Standard Scatternet Backbone
                }

                val totalConnections = store.activeConnections.size + store.activeServerConnections.size
                
                if (totalConnections >= MAX_TOTAL_CONNECTIONS || (totalConnections >= 2 && peerConnections > 0)) {
                        if (peerConnections == 0 && totalConnections >= MAX_TOTAL_CONNECTIONS) {
                            if (!store.orphanDetectionTime.containsKey(macAddress)) {
                                store.orphanDetectionTime[macAddress] = now
                            } else {
                                if (now - (store.orphanDetectionTime[macAddress] ?: now) > 5000) {
                                    AppLogger.d("BLE_MESH", "Orphan Preemption: Found orphan $peerName. Dropping weakest link to rescue.")
                                    val lruMac = store.connectionInteractionTimes
                                        .filterKeys { store.activeConnections.containsKey(it) }
                                        .filterKeys { store.pendingQueues[it]?.isEmpty() != false } // QA FIX: Only evict idle connections
                                        .minByOrNull { it.value }?.key
                                        
                                    if (lruMac != null) {
                                        store.activeConnections[lruMac]?.disconnect()
                                        store.activeConnections[lruMac]?.close()
                                        store.activeConnections.remove(lruMac)
                                        store.pendingQueues.remove(lruMac)
                                        store.isWriting.remove(lruMac)
                                        store.chunkBuffers.remove(lruMac)
                                        store.connectionInteractionTimes.remove(lruMac)
                                        handler.post {
                                            onDeviceDisconnected?.invoke(lruMac)
                                        }
                                        connectToPersistentGatt(macAddress, peerName)
                                    }
                                }
                            }
                        } else {
                            store.orphanDetectionTime.remove(macAddress)
                        }
                    } else {
                        val lastAttempt = store.connectionAttempts[macAddress] ?: 0L
                        if (now - lastAttempt > 5000) {
                            store.connectionAttempts[macAddress] = now
                            
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
                                // QA FIX: If the Master fails to initiate due to hardware bugs, the Slave seizes control after 10 seconds!
                                handler.postDelayed({
                                    if (!store.activeServerConnections.containsKey(macAddress) && !store.activeConnections.containsKey(macAddress)) {
                                        AppLogger.d("BLE_MESH", "Master-Slave Reversal! Designated Master failed. Initiating as Client.")
                                        connectToPersistentGatt(macAddress, peerName)
                                    }
                                }, 10000)
                            }
                        }
                    }
                }
            }
        }
    }

    fun connectToPersistentGatt(macAddress: String, peerName: String) { gattClientManager.connectToPersistentGatt(macAddress, peerName) }


    fun processNextPayload(macAddress: String) {
        val writing = store.isWriting[macAddress] ?: return
        val queue = store.pendingQueues[macAddress] ?: return

        if (writing.compareAndSet(false, true)) {
            val payload = queue.poll()
            if (payload != null) {
                store.connectionInteractionTimes[macAddress] = System.currentTimeMillis()
                
                // Check if we are connected as a Client
                val gatt = store.activeConnections[macAddress]
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
                            val failures = (store.writeFailureCount[macAddress] ?: 0) + 1
                            store.writeFailureCount[macAddress] = failures
                            if (failures >= 5) {
                                AppLogger.d("BLE_MESH", "GATT write failed 5 times for $macAddress. Assuming Zombie Socket. Forcing UI disconnect.")
                                writing.set(false)
                                store.writeFailureCount.remove(macAddress)
                                forceGattDisconnect(macAddress, gatt)
                            } else {
                                AppLogger.d("BLE_MESH", "GATT write busy for $macAddress. Retrying... ($failures/5)")
                                val newQueue = java.util.concurrent.ConcurrentLinkedQueue<ByteArray>()
                                newQueue.add(payload)
                                newQueue.addAll(queue)
                                store.pendingQueues[macAddress] = newQueue
                                handler.postDelayed({
                                    writing.set(false)
                                    processNextPayload(macAddress)
                                }, 200)
                            }
                        } else {
                            store.writeFailureCount.remove(macAddress)
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
                val serverDevice = store.activeServerConnections[macAddress]
                val txChar = gattServer?.getService(SERVICE_UUID)?.getCharacteristic(TX_CHARACTERISTIC_UUID)
                if (serverDevice != null && txChar != null) {
                    val isSuccess = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        gattServer?.notifyCharacteristicChanged(serverDevice, txChar, false, payload) == android.bluetooth.BluetoothStatusCodes.SUCCESS
                    } else {
                        txChar.value = payload
                        gattServer?.notifyCharacteristicChanged(serverDevice, txChar, false) == true
                    }
                    
                    if (!isSuccess) {
                        val failures = (store.writeFailureCount[macAddress] ?: 0) + 1
                        store.writeFailureCount[macAddress] = failures
                        if (failures >= 5) {
                            AppLogger.d("BLE_MESH", "Server GATT notify failed 5 times for $macAddress. Assuming dead link.")
                            writing.set(false)
                            store.writeFailureCount.remove(macAddress)
                            gattServer?.cancelConnection(serverDevice)
                        } else {
                            val newQueue = java.util.concurrent.ConcurrentLinkedQueue<ByteArray>()
                            newQueue.add(payload)
                            newQueue.addAll(queue)
                            store.pendingQueues[macAddress] = newQueue
                            handler.postDelayed({
                                writing.set(false)
                                processNextPayload(macAddress)
                            }, 200)
                        }
                    } else {
                        store.writeFailureCount.remove(macAddress)
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

    fun startGattServer() { gattServerManager.startGattServer() }

    fun startL2capServer() { gattServerManager.startL2capServer() }

    fun handleL2capConnection(macAddress: String, socket: BluetoothSocket) {
        store.activeL2capSockets[macAddress] = socket
        Thread {
            try {
                val din = java.io.DataInputStream(socket.inputStream)
                while (store.isNodeActive.get() && socket.isConnected) {
                    val length = din.readInt()
                    if (length > 0 && length < 10 * 1024 * 1024) { // Max 10MB sanity check
                        store.connectionInteractionTimes[macAddress] = System.currentTimeMillis()
                        val payloadBytes = ByteArray(length)
                        din.readFully(payloadBytes)
                        AppLogger.d("BLE_MESH", "L2CAP Received ${length} bytes from $macAddress")
                        processBinaryPayload(macAddress, payloadBytes)
                    }
                }
            } catch (e: Exception) {
                AppLogger.d("BLE_MESH", "L2CAP stream disconnected for $macAddress: ${e.message}")
            } finally {
                store.activeL2capSockets.remove(macAddress)
                try { socket.close() } catch (e: Exception) {}
            }
        }.start()
    }

    fun processBinaryPayload(endpointId: String, payloadBytes: ByteArray) {
        try {
            val payload = kotlinx.serialization.protobuf.ProtoBuf.decodeFromByteArray(com.example.testresqmesh.core.network.MeshPayload.serializer(), payloadBytes)
            
            // Only auto-rename the physical socket if this is a direct SYSTEM pulse (not relayed)
            if (payload.type == "SYSTEM" && payload.routePath.isEmpty() && payload.senderName.isNotEmpty()) {
                val oldName = store.connectedEndpointNames[endpointId]
                if (oldName == null || oldName.contains("Unknown")) {
                    AppLogger.d("BLE_MESH", "Auto-rename: $endpointId is now ${payload.senderName}")
                    store.connectedEndpointNames[endpointId] = payload.senderName
                    handler.post {
                        val isDirectlyConnected = store.activeConnections.containsKey(endpointId) || store.activeServerConnections.containsKey(endpointId)
                        onDeviceConnected?.invoke(com.example.testresqmesh.core.model.ConnectedDevice(endpointId, payload.senderName, isDirectlyConnected))
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
        targets.addAll(store.activeConnections.keys)
        targets.addAll(store.activeServerConnections.keys)
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

        // PHASE 2 L2CAP ROUTING: Bypass GATT entirely if high-speed socket is available
        val l2capSocket = store.activeL2capSockets[targetMacAddress]
        if (l2capSocket != null && l2capSocket.isConnected) {
            store.connectionInteractionTimes[targetMacAddress] = System.currentTimeMillis()
            Thread {
                try {
                    synchronized(l2capSocket) {
                        val dout = java.io.DataOutputStream(l2capSocket.outputStream)
                        dout.writeInt(payloadBytes.size)
                        dout.write(payloadBytes)
                        dout.flush()
                    }
                    AppLogger.d("BLE_MESH", "L2CAP Sent ${payloadBytes.size} bytes directly to $targetMacAddress")
                } catch (e: Exception) {
                    AppLogger.d("BLE_MESH", "L2CAP write failed to $targetMacAddress: ${e.message}")
                }
            }.start()
            return
        }
        
        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < fullData.size) {
            val chunkSize = store.connectionMtu[targetMacAddress] ?: 20
            val length = Math.min(chunkSize, fullData.size - offset)
            val chunk = ByteArray(length)
            System.arraycopy(fullData, offset, chunk, 0, length)
            chunks.add(chunk)
            offset += length
        }
        
        val isServerConnected = store.activeServerConnections.containsKey(targetMacAddress)
        val isClientConnected = store.activeConnections.containsKey(targetMacAddress)
        
        val queue = store.pendingQueues.getOrPut(targetMacAddress) { ConcurrentLinkedQueue<ByteArray>() }
        chunks.forEach { queue.add(it) }
        
        store.isWriting.putIfAbsent(targetMacAddress, AtomicBoolean(false))

        if (isServerConnected || isClientConnected) {
            processNextPayload(targetMacAddress)
        } else {
            if (store.activeConnections.size >= MAX_CONNECTIONS) {
                // VIP BOUNCER (LRU EVICTION)
                val lruMac = store.connectionInteractionTimes
                    .filterKeys { store.activeConnections.containsKey(it) }
                    .filterKeys { store.pendingQueues[it]?.isEmpty() != false } // QA FIX
                    .minByOrNull { it.value }?.key
                    
                val macToEvict = lruMac ?: store.activeConnections.keys.firstOrNull { store.pendingQueues[it]?.isEmpty() != false }
                
                if (macToEvict != null) {
                    AppLogger.d("BLE_MESH", "Evicting $macToEvict to make room for VIP connection to $targetMacAddress")
                    store.activeConnections[macToEvict]?.disconnect()
                    store.activeConnections[macToEvict]?.close()
                    store.activeConnections.remove(macToEvict)
                    store.pendingQueues.remove(macToEvict)
                    store.isWriting.remove(macToEvict)
                    store.chunkBuffers.remove(macToEvict)
                    store.connectionInteractionTimes.remove(macToEvict)
                    handler.post {
                        onDeviceDisconnected?.invoke(macToEvict)
                    }
                } else {
                    AppLogger.d("BLE_MESH", "VIP Bouncer failed: Cannot evict any connections because all are actively transmitting.")
                    return // Abort connecting to the new node to protect current data streams
                }
            }
            connectToPersistentGatt(targetMacAddress, store.connectedEndpointNames[targetMacAddress] ?: "Unknown")
        }
    }

    fun cacheOutgoingMessageId(payloadBytes: ByteArray) {
        try {
            val payload = ProtoBuf.decodeFromByteArray<MeshPayload>(payloadBytes)
            if (payload.id.isNotEmpty()) store.seenMessageIds.add(payload.id)
        } catch (e: Exception) {}
    }

    fun forceGattDisconnect(macAddress: String, gatt: BluetoothGatt?) {
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (e: Exception) { }
        
        store.activeConnections.remove(macAddress)
        store.pendingQueues.remove(macAddress)
        store.isWriting.remove(macAddress)
        store.chunkBuffers.remove(macAddress)
        store.connectedEndpointIds.remove(macAddress)
        store.connectedEndpointNames.remove(macAddress)
        
        handler.post {
            onDeviceDisconnected?.invoke(macAddress)
            sendSystemPulse()
        }
    }

    fun disconnectFromEndpoint(endpointId: String) {
        store.activeConnections[endpointId]?.disconnect()
        store.activeServerConnections[endpointId]?.let { device ->
            gattServer?.cancelConnection(device)
        }
    }
    
    fun blockDevice(deviceName: String, sendNotification: Boolean = true) {
        store.blockedDevices[deviceName] = true
        // Find and disconnect if currently connected
        val macAddress = store.connectedEndpointNames.entries.find { it.value == deviceName }?.key
        if (macAddress != null) {
            disconnectFromEndpoint(macAddress)
            store.connectedEndpointIds.remove(macAddress)
            store.connectedEndpointNames.remove(macAddress)
            handler.post {
                onDeviceDisconnected?.invoke(macAddress)
                sendSystemPulse()
            }
        }
    }
    fun unblockDevice(deviceName: String) {
        store.blockedDevices.remove(deviceName)
    }
    
    fun rescan() {
        bleScanner?.stopScan(scanCallback)
        startScanning()
    }
    
    fun forceConnectToDevice(endpointId: String, endpointName: String) {
        if (!store.activeConnections.containsKey(endpointId)) {
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


