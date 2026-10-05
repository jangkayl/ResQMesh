package com.example.testresqmesh.core.network.bluetooth.gatt

import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Handler
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.network.bluetooth.state.BleStateStore
import java.util.UUID

/** Shared live resources and operations; the facade remains their sole owner. */
interface GattHost {
    val context: Context
    val store: BleStateStore
    val handler: Handler
    val onDeviceConnected: ((ConnectedDevice) -> Unit)?
    val onDeviceDisconnected: ((String) -> Unit)?
    val SERVICE_UUID: UUID
    val TX_CHARACTERISTIC_UUID: UUID
    val L2CAP_PSM_CHARACTERISTIC_UUID: UUID
    val CCC_DESCRIPTOR_UUID: UUID
    val MAX_TOTAL_CONNECTIONS: Int
    val transportGeneration: Long

    fun isTransportGenerationCurrent(generation: Long): Boolean
    fun sendSystemPulse(forceFull: Boolean = false): Unit
    fun scheduleAdvertisingUpdate(delayMs: Long = 1000L): Unit
    fun beginRadioHandshake(owner: String): Unit
    fun finishRadioHandshake(owner: String, reason: String): Unit
    fun cleanupEndpointIfUnowned(endpointId: String): Unit
    fun findLinkEndpointByIdentity(peerName: String): String?
    fun distinctLinkCount(): Int
    fun processNextPayload(macAddress: String): Unit
    fun handleL2capConnection(macAddress: String, socket: BluetoothSocket): Unit
    fun processBinaryPayload(endpointId: String, payloadBytes: ByteArray): Unit
    fun isDeviceBlocked(deviceName: String): Boolean
}
