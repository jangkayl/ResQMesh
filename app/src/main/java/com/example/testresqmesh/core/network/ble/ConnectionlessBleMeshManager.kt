package com.example.testresqmesh.core.network.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.example.testresqmesh.core.utils.AppLogger
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class ConnectionlessBleMeshManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val advertiser: BluetoothLeAdvertiser? = bluetoothAdapter?.bluetoothLeAdvertiser
    private val scanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    // Manufacturer ID for custom data chunking (Espressif is 0x02E5, or 0xFFFF for testing)
    private val MANUF_ID = 0xFFFF

    private val bouncerCache = java.util.LinkedHashSet<Int>()
    private val chunkBuffer = mutableMapOf<Short, MutableMap<Int, ByteArray>>()
    private val handler = Handler(Looper.getMainLooper())

    var onMessageReceived: ((String, String) -> Unit)? = null // msgIdHash, payload
    var onDeviceDiscovered: ((android.bluetooth.BluetoothDevice) -> Unit)? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val manufData = result.scanRecord?.getManufacturerSpecificData(MANUF_ID)
            if (manufData != null) {
                // WE ONLY INJECT IF IT IS A VERIFIED RESQMESH PACKET!
                onDeviceDiscovered?.invoke(result.device)
                handleIncomingPacket(manufData)
            }
        }
        override fun onScanFailed(errorCode: Int) {
            AppLogger.d("ConnectionlessBle", "Scan failed with error: $errorCode")
        }
    }

    private fun handleIncomingPacket(data: ByteArray) {
        val chunk = BlePacketSerializer.deserializeChunk(data) ?: return
        val hashInt = chunk.msgIdHash.toInt() and 0xFFFF
        
        // Deduplication (Bouncer) for fully assembled messages
        if (bouncerCache.contains(hashInt)) return
        
        // Add to chunk buffer
        val messageChunks = chunkBuffer.getOrPut(chunk.msgIdHash) { mutableMapOf() }
        messageChunks[chunk.chunkIndex] = chunk.data
        
        // Check if fully assembled
        if (messageChunks.size == chunk.totalChunks) {
            // ASSEMBLE!
            val fullData = ByteArrayOutputStream()
            for (i in 0 until chunk.totalChunks) {
                fullData.write(messageChunks[i] ?: ByteArray(0))
            }
            val compressedBytes = fullData.toByteArray()
            val payloadStr = CompactBinarySerializer.expandBinaryToJson(compressedBytes)
            
            bouncerCache.add(hashInt)
            if (bouncerCache.size > 1000) bouncerCache.remove(bouncerCache.first())
            chunkBuffer.remove(chunk.msgIdHash) // Cleanup
            
            AppLogger.d("ConnectionlessBle", "Assembled fully chunked message! Hash: $hashInt")
            onMessageReceived?.invoke(hashInt.toString(), payloadStr)
            
            // Relaying (Store-Carry-Forward Flooding logic)
            if (chunk.ttl > 1) {
                val delay = (50..300).random().toLong()
                handler.postDelayed({
                    relayPayload(hashInt, compressedBytes, chunk.ttl - 1)
                }, delay)
            }
        }
    }
    
    private fun relayPayload(hashInt: Int, compressedBytes: ByteArray, ttl: Int) {
        if (!hasPermissions()) return
        val chunks = BlePacketSerializer.serializeToChunksWithHash(hashInt, compressedBytes, ttl)
        
        AppLogger.d("ConnectionlessBle", "Starting Relay Broadcast (${chunks.size} chunks) for hash: $hashInt")
        startChunkBroadcast(chunks)
    }

    fun startScanning() {
        if (!hasPermissions()) return
        // Brilliant Workaround: Android scanners require a filter to wake up, but passing null crashes some phones,
        // and passing empty byte arrays drops payloads > 0 bytes.
        // We pass a 1-byte array with a 0-byte mask, which creates a wildcard match for our MANUF_ID for ANY payload size!
        val scanFilter = ScanFilter.Builder()
            .setManufacturerData(MANUF_ID, byteArrayOf(0x00), byteArrayOf(0x00))
            .build()
            
        val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        try {
            scanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
            AppLogger.d("ConnectionlessBle", "Started BLE Connectionless Chunk Scanning")
        } catch (e: SecurityException) {
            AppLogger.d("ConnectionlessBle", "Security exception starting scan: ${e.message}")
        }
    }

    fun stopScanning() {
        if (!hasPermissions()) return
        try {
            scanner?.stopScan(scanCallback)
            AppLogger.d("ConnectionlessBle", "Stopped BLE Connectionless Scanning")
        } catch (e: SecurityException) {
            AppLogger.d("ConnectionlessBle", "Security exception stopping scan")
        }
    }

    fun broadcastPayload(msgId: String, payload: String, ttl: Int = 5) {
        if (!hasPermissions()) return
        val compressedBytes = CompactBinarySerializer.compressJsonToBinary(payload)
        val chunks = BlePacketSerializer.serializeToChunksWithHash(msgId.hashCode() and 0xFFFF, compressedBytes, ttl)
        bouncerCache.add((msgId.hashCode() and 0xFFFF))
        
        startChunkBroadcast(chunks)
    }

    private fun startChunkBroadcast(chunks: List<ByteArray>) {
        var currentChunk = 0
        var loopCount = 0
        val maxLoops = 3
        
        val runnable = object : Runnable {
            override fun run() {
                val dataBytes = chunks[currentChunk]
                val advertiseData = AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .addManufacturerData(MANUF_ID, dataBytes)
                    .build()

                // FORCE API 21 LEGACY ADVERTISING (Absolute Lowest Common Denominator)
                // We completely skip the API 26+ AdvertisingSet to guarantee that NO modern phone
                // accidentally transmits in Bluetooth 5.0 Extended mode, which older phones cannot hear.
                val settings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(false)
                    .build()

                val advertiseCallback = object : AdvertiseCallback() {
                    override fun onStartFailure(errorCode: Int) {
                        // Silently ignore hardware limits
                    }
                }
                
                try { 
                    advertiser?.startAdvertising(settings, advertiseData, advertiseCallback) 
                    handler.postDelayed({
                        try { advertiser?.stopAdvertising(advertiseCallback) } catch (e: SecurityException) {}
                    }, 150)
                } catch (e: Exception) {
                    // Silently ignore broadcast exception
                }
                
                currentChunk++
                if (currentChunk >= chunks.size) {
                    loopCount++
                    if (loopCount < maxLoops) {
                        currentChunk = 0 // Restart the sequence
                        handler.postDelayed(this, 300) // 300ms gap before repeating the whole sequence
                    }
                } else {
                    handler.postDelayed(this, 200) // 200ms gap between individual chunks
                }
            }
        }
        handler.post(runnable)
    }

    private fun hasPermissions(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }
}
