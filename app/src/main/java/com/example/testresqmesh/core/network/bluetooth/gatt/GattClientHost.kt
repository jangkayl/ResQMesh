package com.example.testresqmesh.core.network.bluetooth.gatt

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothSocket
import com.example.testresqmesh.core.model.ScanEvent
import com.example.testresqmesh.core.network.bluetooth.BleConnectStartResult
import com.example.testresqmesh.core.network.bluetooth.state.BleLinkRole

/** Client setup capabilities. No server resources, routing or outbox access. */
interface GattClientHost : GattHost {
    val onDeviceScanned: ((ScanEvent) -> Unit)?
    val CONNECT_TIMEOUT_MS: Long
    val HANDSHAKE_WATCHDOG_MS: Long
    val DISCOVERY_REQUEST_RETRY_MS: Long
    val MAX_DISCOVERY_REQUEST_ATTEMPTS: Int

    fun getRemoteDevice(macAddress: String): BluetoothDevice
    fun trackPendingL2capSocket(socket: BluetoothSocket, generation: Long): Boolean
    fun finishPendingL2capSocket(socket: BluetoothSocket): Unit
    fun isRadioHandshakeActive(): Boolean
    fun connectToPersistentGatt(macAddress: String, peerName: String): BleConnectStartResult
    fun tryAcquireConnectLock(macAddress: String): Boolean
    fun releaseConnectLock(macAddress: String?, reason: String, force: Boolean = false): Unit
    fun completeGattChunk(endpoint: String, role: BleLinkRole, status: Int, gatt: BluetoothGatt? = null, device: BluetoothDevice? = null): Unit
    fun forceGattDisconnect(macAddress: String, gatt: BluetoothGatt?): Unit
}
