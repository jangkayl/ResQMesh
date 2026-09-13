package com.example.testresqmesh.core.network.bluetooth.scanner

import android.annotation.SuppressLint
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import com.example.testresqmesh.core.utils.AppLogger
import java.util.UUID

interface BleScannerCallback {
    fun onNodeScanned(macAddress: String, peerName: String, peerScore: String, peerConnections: Int)
}

@SuppressLint("MissingPermission")
class BleScanner(
    private val scanner: BluetoothLeScanner?,
    private val serviceUuid: UUID,
    private val callback: BleScannerCallback
) {
    private var isScanning = false

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
            
            callback.onNodeScanned(device.address, peerName, peerScore, peerConnections)
        }
    }

    fun startScanning() {
        if (scanner == null || isScanning) return
        
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(serviceUuid)).build())
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
            
        try {
            scanner.startScan(filters, settings, scanCallback)
            isScanning = true
        } catch (e: Exception) {
            // Ignored gracefully
        }
    }

    fun stopScanning() {
        if (scanner == null || !isScanning) return
        try {
            scanner.stopScan(scanCallback)
            isScanning = false
        } catch (e: Exception) {
            // Ignored gracefully
        }
    }
}
