package com.example.testresqmesh.core.network.bluetooth.gatt

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Handler
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.ScanEvent
import com.example.testresqmesh.core.network.NativeBleManager
import com.example.testresqmesh.core.network.bluetooth.BleConnectStartResult
import com.example.testresqmesh.core.network.bluetooth.state.BleLinkRole
import com.example.testresqmesh.core.network.bluetooth.state.BleStateStore
import com.example.testresqmesh.core.network.bluetooth.state.ServerIndicationLedger
import java.util.UUID

/** Compatibility adapter: no copied state, extra scheduling, or resource lifetime. */
internal class NativeGattHost(private val manager: NativeBleManager) : GattClientHost, GattServerHost {
    override val context: Context get() = manager.context
    override val store: BleStateStore get() = manager.store
    override val handler: Handler get() = manager.handler
    override val onDeviceConnected: ((ConnectedDevice) -> Unit)? get() = manager.onDeviceConnected
    override val onDeviceDisconnected: ((String) -> Unit)? get() = manager.onDeviceDisconnected
    override val bluetoothAdapter: BluetoothAdapter? get() = manager.bluetoothAdapter
    override val SERVICE_UUID: UUID get() = manager.SERVICE_UUID
    override val TX_CHARACTERISTIC_UUID: UUID get() = manager.TX_CHARACTERISTIC_UUID
    override val L2CAP_PSM_CHARACTERISTIC_UUID: UUID get() = manager.L2CAP_PSM_CHARACTERISTIC_UUID
    override val CCC_DESCRIPTOR_UUID: UUID get() = manager.CCC_DESCRIPTOR_UUID
    override val MAX_TOTAL_CONNECTIONS: Int get() = manager.MAX_TOTAL_CONNECTIONS
    override val transportGeneration: Long get() = manager.transportGeneration
    override val onDeviceScanned: ((ScanEvent) -> Unit)? get() = manager.onDeviceScanned
    override val CONNECT_TIMEOUT_MS: Long get() = manager.CONNECT_TIMEOUT_MS
    override val HANDSHAKE_WATCHDOG_MS: Long get() = manager.HANDSHAKE_WATCHDOG_MS
    override val DISCOVERY_REQUEST_RETRY_MS: Long get() = manager.DISCOVERY_REQUEST_RETRY_MS
    override val MAX_DISCOVERY_REQUEST_ATTEMPTS: Int get() = manager.MAX_DISCOVERY_REQUEST_ATTEMPTS
    override val myDeviceName: String get() = manager.myDeviceName
    override val myNodeId: String get() = manager.myNodeId
    override val bluetoothManager: BluetoothManager get() = manager.bluetoothManager
    override val RX_CHARACTERISTIC_UUID: UUID get() = manager.RX_CHARACTERISTIC_UUID
    override var gattServer: BluetoothGattServer?
        get() = manager.gattServer
        set(value) { manager.gattServer = value }
    override var l2capServerSocket: BluetoothServerSocket?
        get() = manager.l2capServerSocket
        set(value) { manager.l2capServerSocket = value }
    override var myL2capPsm: Int
        get() = manager.myL2capPsm
        set(value) { manager.myL2capPsm = value }
    override var l2capAcceptThread: Thread?
        get() = manager.l2capAcceptThread
        set(value) { manager.l2capAcceptThread = value }
    override val NAME_HANDSHAKE_TIMEOUT_MS: Long get() = manager.NAME_HANDSHAKE_TIMEOUT_MS
    override val DUPLICATE_LINK_GRACE_MS: Long get() = manager.DUPLICATE_LINK_GRACE_MS

    override fun getRemoteDevice(macAddress: String): BluetoothDevice = manager.bluetoothAdapter.getRemoteDevice(macAddress)
    override fun isTransportGenerationCurrent(generation: Long): Boolean = manager.isTransportGenerationCurrent(generation)
    override fun sendSystemPulse(forceFull: Boolean): Unit = manager.sendSystemPulse(forceFull)
    override fun scheduleAdvertisingUpdate(delayMs: Long): Unit = manager.scheduleAdvertisingUpdate(delayMs)
    override fun beginRadioHandshake(owner: String): Unit = manager.beginRadioHandshake(owner)
    override fun finishRadioHandshake(owner: String, reason: String): Unit = manager.finishRadioHandshake(owner, reason)
    override fun cleanupEndpointIfUnowned(endpointId: String): Unit = manager.cleanupEndpointIfUnowned(endpointId)
    override fun findLinkEndpointByIdentity(peerName: String): String? = manager.findLinkEndpointByIdentity(peerName)
    override fun distinctLinkCount(): Int = manager.distinctLinkCount()
    override fun processNextPayload(macAddress: String): Unit = manager.processNextPayload(macAddress)
    override fun handleL2capConnection(macAddress: String, socket: BluetoothSocket): Unit = manager.handleL2capConnection(macAddress, socket)
    override fun processBinaryPayload(endpointId: String, payloadBytes: ByteArray): Unit = manager.processBinaryPayload(endpointId, payloadBytes)
    override fun isDeviceBlocked(deviceName: String): Boolean = manager.isDeviceBlocked(deviceName)
    override fun trackPendingL2capSocket(socket: BluetoothSocket, generation: Long): Boolean = manager.trackPendingL2capSocket(socket, generation)
    override fun finishPendingL2capSocket(socket: BluetoothSocket): Unit = manager.finishPendingL2capSocket(socket)
    override fun isRadioHandshakeActive(): Boolean = manager.isRadioHandshakeActive()
    override fun connectToPersistentGatt(macAddress: String, peerName: String): BleConnectStartResult = manager.connectToPersistentGatt(macAddress, peerName)
    override fun tryAcquireConnectLock(macAddress: String): Boolean = manager.tryAcquireConnectLock(macAddress)
    override fun releaseConnectLock(macAddress: String?, reason: String, force: Boolean): Unit = manager.releaseConnectLock(macAddress, reason, force)
    override fun completeGattChunk(endpoint: String, role: BleLinkRole, status: Int, gatt: BluetoothGatt?, device: BluetoothDevice?): Unit = manager.completeGattChunk(endpoint, role, status, gatt, device)
    override fun forceGattDisconnect(macAddress: String, gatt: BluetoothGatt?): Unit = manager.forceGattDisconnect(macAddress, gatt)
    override fun beginServerRegistration(): Long = manager.beginServerRegistration()
    override fun ownsServerRegistration(generation: Long): Boolean = manager.ownsServerRegistration(generation)
    override fun onGattServerReady(generation: Long): Unit = manager.onGattServerReady(generation)
    override fun failTransport(generation: Long): Unit = manager.failTransport(generation)
    override fun linkEstablishedAt(endpointId: String): Long = manager.linkEstablishedAt(endpointId)
    override fun completeServerIndication(ticket: ServerIndicationLedger.Ticket, status: Int): Unit = manager.completeServerIndication(ticket, status)
    override fun startGattServer(): Unit = manager.startGattServer()
    override fun startL2capServer(): Unit = manager.startL2capServer()
    override fun disconnectFromEndpoint(endpointId: String): Unit = manager.disconnectFromEndpoint(endpointId)
}
