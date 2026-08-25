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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.UUID

@SuppressLint("MissingPermission")
class NativeBleManager(private val context: Context) {
    // Callbacks required by MeshRepository
    var onDeviceConnected: ((ConnectedDevice) -> Unit)? = null
    var onDeviceDisconnected: ((String) -> Unit)? = null
    var onConnectionFailed: ((String) -> Unit)? = null
    var onMessageReceived: ((String, String, String, String, Boolean, Boolean, String?, String?, Double?, Double?, String, List<String>) -> Unit)? = null
    var onMessageSeen: ((String, String) -> Unit)? = null
    var onMessageDelivered: ((String, String, List<String>) -> Unit)? = null
    var onSosCancelled: (() -> Unit)? = null
    var onRoutingTableReceived: ((String, List<String>) -> Unit)? = null
    var onPublicKeyReceived: ((String, String) -> Unit)? = null
    var onStatusChanged: ((String) -> Unit)? = null
    var onDeviceScanned: ((String, String, Int, String, Boolean) -> Unit)? = null
    var onDeviceScanRemoved: ((String) -> Unit)? = null

    var myDeviceName: String = ""
    private val _blockedDeviceNames = MutableStateFlow<Set<String>>(emptySet())
    val blockedDeviceNames: StateFlow<Set<String>> = _blockedDeviceNames.asStateFlow()
    
    // BLE specific
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val bleAdvertiser: BluetoothLeAdvertiser? = bluetoothAdapter.bluetoothLeAdvertiser
    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter.bluetoothLeScanner
    
    private var gattServer: BluetoothGattServer? = null
    private val connectedEndpointIds = mutableSetOf<String>()
    private val connectedEndpointNames = mutableMapOf<String, String>()
    
    // UUIDs
    private val SERVICE_UUID = UUID.fromString("14389025-01e4-4114-9988-8255018652c7")
    private val RX_CHARACTERISTIC_UUID = UUID.fromString("6b2c287a-32fb-4e1b-9f1e-e7cb359480fc")

    private val seenMessageIds = java.util.LinkedHashSet<String>()
    private val notificationHelper = NotificationHelper(context)
    private val isNodeActive = java.util.concurrent.atomic.AtomicBoolean(false)
    
    // Dispatcher
    private val payloadDispatcher = PayloadDispatcher(object : PayloadDispatcherCallback {
        override fun getMyDeviceName() = myDeviceName
        override fun getSeenMessageIds() = seenMessageIds
        override fun getEndpointMedium(endpointId: String) = "Native BLE 5.0"
        override fun getConnectedEndpointIdByName(name: String) = connectedEndpointNames.entries.find { it.value == name }?.key
        override fun sendDirectPayload(endpointId: String, payload: String) = this@NativeBleManager.sendDirectPayload(endpointId, payload)
        override fun broadcastPayload(payload: String, excludeEndpointId: String?) = this@NativeBleManager.broadcastPayload(payload, excludeEndpointId)
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
            val iterator = endpointLastSeen.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                // If we haven't seen an advertisement in 5 seconds, assume they closed the app
                if (now - entry.value > 5000) { 
                    val macAddress = entry.key
                    connectedEndpointIds.remove(macAddress)
                    connectedEndpointNames.remove(macAddress)
                    iterator.remove()
                    AppLogger.d("BLE_MESH", "Node Timed Out (Disconnected): $macAddress")
                    onDeviceDisconnected?.invoke(macAddress)
                    onDeviceScanRemoved?.invoke(macAddress)
                }
            }
            handler.postDelayed(this, 1500) // check every 1.5 seconds
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
        
        // Start the cleanup service so if the user swipes away the app, the OS kills our advertisement
        context.startService(android.content.Intent(context, BleCleanupService::class.java))
        
        startGattServer()
        startAdvertising(teamKey)
        startScanning()
        handler.post(timeoutRunnable)
        onStatusChanged?.invoke("Node Active [BLE]. Seeking peers...")
    }
    
    fun stopMeshNode() {
        isNodeActive.set(false)
        bleAdvertiser?.stopAdvertising(advertiseCallback)
        bleScanner?.stopScan(scanCallback)
        gattServer?.close()
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
            .addManufacturerData(1024, nameBytes)
            .build()
            
        bleAdvertiser?.startAdvertising(settings, data, scanResponse, advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            AppLogger.d("BLE_MESH", "Advertising started.")
        }
        override fun onStartFailure(errorCode: Int) {
            AppLogger.d("BLE_MESH", "Advertising failed: $errorCode")
        }
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
            val manufacturerData = result.scanRecord?.getManufacturerSpecificData(1024)
            val peerName = manufacturerData?.let { String(it, Charsets.UTF_8) } ?: "Unknown Node"
            val macAddress = device.address

            if (peerName != myDeviceName) {
                // Update the heartbeat timestamp every time we "hear" them
                endpointLastSeen[macAddress] = System.currentTimeMillis()

                if (!connectedEndpointIds.contains(macAddress)) {
                    connectedEndpointIds.add(macAddress)
                    connectedEndpointNames[macAddress] = peerName
                    AppLogger.d("BLE_MESH", "Discovered & Connected to: $peerName ($macAddress)")
                    
                    onDeviceScanned?.invoke(macAddress, peerName, 50, "MEMBER", false)
                    onDeviceConnected?.invoke(ConnectedDevice(macAddress, peerName))
                }
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
                    val jsonString = String(it, Charsets.UTF_8)
                    AppLogger.d("BLE_MESH", "Received GATT Write: ${jsonString.take(50)}...")
                    processJsonPayload(device.address, jsonString)
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
    
    private fun processJsonPayload(endpointId: String, jsonString: String) {
        payloadDispatcher.dispatch(endpointId, jsonString)
    }

    fun broadcastPayload(jsonString: String, excludeEndpointId: String? = null) {
        cacheOutgoingMessageId(jsonString)
        val targets = connectedEndpointIds.filter { it != excludeEndpointId }
        targets.forEach { targetId ->
            sendDirectPayload(targetId, jsonString)
        }
    }

    fun sendDirectPayload(targetMacAddress: String, jsonString: String) {
        cacheOutgoingMessageId(jsonString)
        val device = bluetoothAdapter.getRemoteDevice(targetMacAddress)
        
        var gattRef: BluetoothGatt? = null
        
        // Failsafe: Force close the connection after 5 seconds if it gets stuck
        val timeoutRunnable = Runnable {
            gattRef?.disconnect()
            gattRef?.close()
        }
        handler.postDelayed(timeoutRunnable, 5000)

        gattRef = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.requestMtu(512)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    handler.removeCallbacks(timeoutRunnable)
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
                val service = gatt.getService(SERVICE_UUID)
                val characteristic = service?.getCharacteristic(RX_CHARACTERISTIC_UUID)
                if (characteristic != null) {
                    characteristic.value = jsonString.toByteArray(Charsets.UTF_8)
                    val success = gatt.writeCharacteristic(characteristic)
                    if (!success) {
                        AppLogger.d("BLE_MESH", "Failed to initiate write. Disconnecting.")
                        gatt.disconnect()
                    }
                } else {
                    gatt.disconnect()
                }
            }
            override fun onCharacteristicWrite(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, status: Int) {
                AppLogger.d("BLE_MESH", "Payload Sent to ${gatt.device.address}. Status: $status. Disconnecting.")
                gatt.disconnect()
            }
        })
    }

    private fun cacheOutgoingMessageId(jsonString: String) {
        try {
            val jsonObject = JSONObject(jsonString)
            if (jsonObject.has("id")) seenMessageIds.add(jsonObject.getString("id"))
        } catch (e: Exception) {}
    }

    fun disconnectFromEndpoint(endpointId: String) {}
    fun blockDevice(deviceName: String, sendNotification: Boolean = true) {}
    fun unblockDevice(deviceName: String) {}
    fun rescan() {
        bleScanner?.stopScan(scanCallback)
        startScanning()
    }
    fun forceConnectToDevice(endpointId: String, endpointName: String) {}

    fun broadcastSeenReceipt(targetMessageId: String, isPrivate: Boolean, targetId: String? = null) {
        val jsonString = JSONObject().apply {
            put("id", UUID.randomUUID().toString())
            put("type", "SEEN")
            put("targetMessageId", targetMessageId)
            put("reader", myDeviceName)
            put("isPrivate", isPrivate)
        }.toString()
        broadcastPayload(jsonString)
    }

    fun broadcastDeliveredReceipt(targetMessageId: String, isPrivate: Boolean, targetId: String? = null, directedReturnRoute: List<String> = emptyList()) {
        val jsonString = JSONObject().apply {
            put("id", UUID.randomUUID().toString())
            put("type", "DELIVERED")
            put("targetMessageId", targetMessageId)
            put("reader", myDeviceName)
            put("isPrivate", isPrivate)
            put("returnRoute", org.json.JSONArray().apply { put(myDeviceName) })
            if (directedReturnRoute.isNotEmpty()) put("directedRoute", org.json.JSONArray(directedReturnRoute))
        }.toString()
        broadcastPayload(jsonString)
    }
}
