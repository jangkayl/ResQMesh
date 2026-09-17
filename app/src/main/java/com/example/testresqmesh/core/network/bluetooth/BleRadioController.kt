package com.example.testresqmesh.core.network.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.network.bluetooth.state.HandshakeRadioGate
import com.example.testresqmesh.core.utils.AppLogger
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

data class BleAdvertisement(
    val endpointId: String,
    val peerName: String,
    val nodeId: String,
    val electionScore: String,
    val directConnections: Int
)

/** Owns BLE advertising, scanning, advertisement parsing, and handshake scan suspension. */
@SuppressLint("MissingPermission")
class BleRadioController(
    context: Context,
    private val serviceUuid: UUID,
    private val isNodeActive: () -> Boolean,
    private val onAdvertisement: (BleAdvertisement) -> Unit
) {
    private val adapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val scanActive = AtomicBoolean(false)
    private val handshakeGate = HandshakeRadioGate()

    val isSupported: Boolean
        get() = adapter?.bluetoothLeAdvertiser != null && adapter.bluetoothLeScanner != null

    private val advertiseCallback = object : AdvertiseCallback() {}
    private val scanCallback = object : ScanCallback() {
        override fun onScanFailed(errorCode: Int) {
            scanActive.set(false)
            AppLogger.d("BLE_MESH", "Scanner failed with errorCode=$errorCode")
        }

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val bytes = result.scanRecord?.getManufacturerSpecificData(MANUFACTURER_ID) ?: return
            val encoded = String(bytes, Charsets.UTF_8).replace("\u0000", "").trim()
            val parts = encoded.split("|")
            val score: String
            val connections: Int
            val displayName: String
            val nodeId: String
            when {
                parts.size >= 4 -> {
                    score = parts[0]
                    connections = parts[1].toIntOrNull() ?: -1
                    nodeId = parts[2].trim().uppercase()
                    displayName = parts.drop(3).joinToString("|")
                }
                parts.size == 3 -> {
                    score = parts[0]
                    connections = parts[1].toIntOrNull() ?: -1
                    displayName = parts[2]
                    nodeId = NodeIdentity.idOf(displayName).orEmpty()
                }
                else -> {
                    AppLogger.d(
                        "BLE_MESH",
                        "Scanner: Ignored alien device ${result.device.address}. Invalid signature: $encoded"
                    )
                    return
                }
            }
            val peerName = if (nodeId.isNotEmpty()) {
                NodeIdentity.compose(displayName, nodeId)
            } else {
                displayName.trim()
            }
            if (peerName.isEmpty()) return
            onAdvertisement(
                BleAdvertisement(result.device.address, peerName, nodeId, score, connections)
            )
        }
    }

    fun startAdvertising(
        electionScore: String,
        directConnections: Int,
        deviceName: String,
        fallbackNodeId: String
    ) {
        val advertiser = adapter?.bluetoothLeAdvertiser ?: return
        val nodeId = NodeIdentity.idOf(deviceName) ?: fallbackNodeId
        val prefix = "$electionScore|$directConnections|$nodeId|"
        val remainingBytes = (MAX_ADVERT_PAYLOAD_BYTES - prefix.toByteArray().size).coerceAtLeast(0)
        val displayName = truncateToBytes(NodeIdentity.displayNameOf(deviceName), remainingBytes)
        val manufacturerData = (prefix + displayName).toByteArray().let { bytes ->
            if (bytes.size <= MAX_ADVERT_PAYLOAD_BYTES) bytes
            else bytes.copyOf(MAX_ADVERT_PAYLOAD_BYTES)
        }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(serviceUuid))
            .build()
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addManufacturerData(MANUFACTURER_ID, manufacturerData)
            .build()
        advertiser.startAdvertising(settings, data, scanResponse, advertiseCallback)
    }

    fun startScanning() {
        val scanner = adapter?.bluetoothLeScanner ?: return
        if (handshakeGate.isActive() || !scanActive.compareAndSet(false, true)) return
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(serviceUuid)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build()
        try {
            scanner.startScan(filters, settings, scanCallback)
        } catch (error: Exception) {
            scanActive.set(false)
            AppLogger.d("BLE_MESH", "Scanner start failed: ${error.message}")
        }
    }

    fun beginHandshake(owner: String) {
        if (!handshakeGate.begin(owner)) return
        stopScanning()
        AppLogger.d("BLE_MESH", "Radio handshake gate acquired by $owner; scanning paused")
    }

    fun finishHandshake(owner: String, reason: String) {
        if (!handshakeGate.finish(owner)) return
        AppLogger.d("BLE_MESH", "Radio handshake gate released by $owner ($reason); scanning resumed")
        if (isNodeActive()) startScanning()
    }

    fun isHandshakeActive(): Boolean = handshakeGate.isActive()

    fun rescan() {
        stopScanning()
        startScanning()
    }

    fun stop() {
        try { adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback) } catch (_: Exception) {}
        stopScanning()
        handshakeGate.clear()
    }

    private fun stopScanning() {
        if (!scanActive.compareAndSet(true, false)) return
        try { adapter?.bluetoothLeScanner?.stopScan(scanCallback) } catch (_: Exception) {}
    }

    private fun truncateToBytes(value: String, maxBytes: Int): String {
        if (maxBytes <= 0) return ""
        if (value.toByteArray().size <= maxBytes) return value
        var end = value.length
        while (end > 0) {
            val candidate = value.substring(0, end)
            if (candidate.toByteArray().size <= maxBytes) return candidate
            end--
        }
        return ""
    }

    private companion object {
        const val MANUFACTURER_ID = 0xFFFF
        const val MAX_ADVERT_PAYLOAD_BYTES = 26
    }
}
