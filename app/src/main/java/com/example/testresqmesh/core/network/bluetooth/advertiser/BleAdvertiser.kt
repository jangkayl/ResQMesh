package com.example.testresqmesh.core.network.bluetooth.advertiser

import android.annotation.SuppressLint
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.os.ParcelUuid
import java.util.UUID

@SuppressLint("MissingPermission")
class BleAdvertiser(
    private val advertiser: BluetoothLeAdvertiser?,
    private val serviceUuid: UUID,
    private val getElectionScore: () -> String,
    private val getMyDeviceName: () -> String,
    private val getTotalConnections: () -> Int
) {
    private var currentTeamKey: String = ""
    private var isAdvertising = false

    private val advertiseCallback = object : AdvertiseCallback() {}

    fun startAdvertising(teamKey: String) {
        if (advertiser == null) return
        currentTeamKey = teamKey
        
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()
            
        val totalConnections = getTotalConnections()
        val electionScore = getElectionScore()
        val combinedName = "$electionScore|$totalConnections|${getMyDeviceName()}"
        var nameBytes = combinedName.toByteArray(Charsets.UTF_8)
        if (nameBytes.size > 20) {
            nameBytes = nameBytes.sliceArray(0 until 20)
        }
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(serviceUuid))
            .build()
            
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addManufacturerData(0xFFFF, nameBytes)
            .build()
            
        try {
            advertiser.startAdvertising(settings, data, scanResponse, advertiseCallback)
            isAdvertising = true
        } catch (e: Exception) {
            // Ignored gracefully
        }
    }

    fun stopAdvertising() {
        if (advertiser == null || !isAdvertising) return
        try {
            advertiser.stopAdvertising(advertiseCallback)
            isAdvertising = false
        } catch (e: Exception) {
            // Ignored gracefully
        }
    }

    fun updateInvisibilityCloak() {
        if (!isAdvertising || currentTeamKey.isEmpty()) return
        stopAdvertising()
        startAdvertising(currentTeamKey)
    }
}
