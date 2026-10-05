package com.example.testresqmesh.core.network.bluetooth.gatt

import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import com.example.testresqmesh.core.network.bluetooth.state.ServerIndicationLedger
import java.util.UUID

/** Server registration and listener capabilities; values proxy the existing facade. */
interface GattServerHost : GattHost {
    val bluetoothAdapter: BluetoothAdapter?
    val myDeviceName: String
    val myNodeId: String
    val bluetoothManager: BluetoothManager
    val RX_CHARACTERISTIC_UUID: UUID
    var gattServer: BluetoothGattServer?
    var l2capServerSocket: BluetoothServerSocket?
    var myL2capPsm: Int
    var l2capAcceptThread: Thread?
    val NAME_HANDSHAKE_TIMEOUT_MS: Long
    val DUPLICATE_LINK_GRACE_MS: Long

    fun beginServerRegistration(): Long
    fun ownsServerRegistration(generation: Long): Boolean
    fun onGattServerReady(generation: Long): Unit
    fun failTransport(generation: Long): Unit
    fun linkEstablishedAt(endpointId: String): Long
    fun completeServerIndication(ticket: ServerIndicationLedger.Ticket, status: Int): Unit
    fun startGattServer(): Unit
    fun startL2capServer(): Unit
    fun disconnectFromEndpoint(endpointId: String): Unit
}
