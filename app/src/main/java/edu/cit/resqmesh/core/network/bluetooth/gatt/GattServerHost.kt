package edu.cit.resqmesh.core.network.bluetooth.gatt

import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import edu.cit.resqmesh.core.network.bluetooth.state.ServerIndicationLedger
import java.util.UUID

/** Server registration and listener capabilities; values proxy the existing facade. */
interface GattServerHost : GattHost {
    val bluetoothAdapter: BluetoothAdapter?
    val myDeviceName: String
    val myNodeId: String
    val bluetoothManager: BluetoothManager
    var gattServer: BluetoothGattServer?
    var l2capServerSocket: BluetoothServerSocket?
    var myL2capPsm: Int
    var l2capAcceptThread: Thread?

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
