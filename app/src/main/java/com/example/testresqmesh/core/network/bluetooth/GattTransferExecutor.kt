package com.example.testresqmesh.core.network.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattServer
import android.os.Handler
import android.os.Looper
import com.example.testresqmesh.core.network.bluetooth.state.BleLink
import com.example.testresqmesh.core.network.bluetooth.state.BleLinkRole
import com.example.testresqmesh.core.network.bluetooth.state.GattTransferCoordinator
import com.example.testresqmesh.core.network.bluetooth.state.GattTransferFlight
import com.example.testresqmesh.core.network.bluetooth.state.BleStateStore
import com.example.testresqmesh.core.network.bluetooth.state.payloadBytes
import com.example.testresqmesh.core.utils.AppLogger
import java.util.UUID

/** Executes Android GATT writes/indications while [GattTransferCoordinator] owns flight state. */
class GattTransferExecutor(
    private val store: BleStateStore,
    private val coordinator: GattTransferCoordinator,
    private val handler: Handler,
    private val serviceUuid: UUID,
    private val receiveCharacteristicUuid: UUID,
    private val transmitCharacteristicUuid: UUID,
    private val gattServer: () -> BluetoothGattServer?,
    private val readyLink: (String) -> BleLink?,
    private val hasUsableL2cap: (String) -> Boolean,
    private val promoteToL2cap: (String) -> Unit,
    private val resendPayload: (String, ByteArray) -> Unit,
    private val onHeartbeatSent: (String, BleLink, String) -> Unit,
    private val onFlightRemoved: (String) -> Unit,
    private val isCurrentLink: (BleLink) -> Boolean,
    private val disconnectClient: (String, BluetoothGatt?) -> Unit,
    private val disconnectServer: (BluetoothDevice) -> Unit,
    private val chunkTimeoutMs: Long
) {
    fun processNext(endpoint: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { processNext(endpoint) }
            return
        }
        if (hasUsableL2cap(endpoint)) {
            promoteToL2cap(endpoint)
            return
        }
        val link = readyLink(endpoint) ?: return
        val flight = coordinator.claimNext(
            endpoint,
            link,
            if (link.role == BleLinkRole.CLIENT) store.activeConnections[endpoint] else null,
            if (link.role == BleLinkRole.SERVER) store.activeServerConnections[endpoint] else null
        ) ?: return
        sendChunk(flight)
    }

    fun complete(
        endpoint: String,
        role: BleLinkRole,
        status: Int,
        gatt: BluetoothGatt? = null,
        device: BluetoothDevice? = null
    ) {
        handler.post {
            val flight = store.gattFlights[endpoint] ?: return@post
            if (!coordinator.callbackMatches(flight, role, gatt, device)) return@post
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail(flight, "GATT completion failed status=$status")
                return@post
            }
            when (coordinator.completeChunk(flight)) {
                GattTransferCoordinator.Completion.MORE -> sendChunk(flight)
                GattTransferCoordinator.Completion.DONE -> {
                    flight.transfer.heartbeatId?.let { onHeartbeatSent(endpoint, flight.link, it) }
                    processNext(endpoint)
                }
                GattTransferCoordinator.Completion.STALE -> Unit
            }
        }
    }

    private fun sendChunk(flight: GattTransferFlight) {
        val endpoint = flight.link.endpoint
        if (!coordinator.owns(flight)) return
        val remaining = flight.transfer.frame.size - flight.offset
        if (remaining <= 0) return
        val mtu = (store.connectionMtu[endpoint] ?: 20).coerceAtLeast(1)
        val chunk = flight.transfer.frame.copyOfRange(flight.offset, flight.offset + minOf(mtu, remaining))
        val operationId = coordinator.beginChunk(flight, chunk.size)
        val initiated = try {
            if (flight.link.role == BleLinkRole.CLIENT) {
                val gatt = flight.gatt
                val characteristic = gatt?.getService(serviceUuid)?.getCharacteristic(receiveCharacteristicUuid)
                if (gatt == null || characteristic == null || store.activeConnections[endpoint] !== gatt) false
                else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(
                        characteristic,
                        chunk,
                        android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    ) == android.bluetooth.BluetoothStatusCodes.SUCCESS
                } else {
                    characteristic.writeType = android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    characteristic.value = chunk
                    gatt.writeCharacteristic(characteristic)
                }
            } else {
                val device = flight.serverDevice
                val characteristic = gattServer()?.getService(serviceUuid)?.getCharacteristic(transmitCharacteristicUuid)
                if (device == null || characteristic == null || store.activeServerConnections[endpoint] !== device) false
                else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    gattServer()?.notifyCharacteristicChanged(device, characteristic, true, chunk) ==
                        android.bluetooth.BluetoothStatusCodes.SUCCESS
                } else {
                    characteristic.value = chunk
                    gattServer()?.notifyCharacteristicChanged(device, characteristic, true) == true
                }
            }
        } catch (e: SecurityException) {
            AppLogger.d("BLE_MESH", "Link ${flight.link.generation} $endpoint: GATT initiation skipped because BLUETOOTH_CONNECT was revoked")
            fail(flight, "Bluetooth permission revoked before GATT initiation")
            return
        }
        if (!initiated) {
            if (coordinator.recordInitiationRejected(flight, MAX_INITIATION_ATTEMPTS)) {
                fail(flight, "GATT initiation rejected $MAX_INITIATION_ATTEMPTS times")
            } else {
                handler.postDelayed({
                    if (store.gattFlights[endpoint] === flight && flight.operationId == operationId) sendChunk(flight)
                }, RETRY_DELAY_MS)
            }
            return
        }
        handler.postDelayed({
            if (store.gattFlights[endpoint] === flight && flight.operationId == operationId) {
                fail(flight, "GATT completion callback timed out")
            }
        }, chunkTimeoutMs)
    }

    private fun fail(flight: GattTransferFlight, reason: String) {
        val endpoint = flight.link.endpoint
        if (!coordinator.remove(flight)) return
        onFlightRemoved(endpoint)
        if (hasUsableL2cap(endpoint)) {
            AppLogger.d(
                "BLE_MESH",
                "Link ${flight.link.generation} $endpoint: $reason; preserving healthy L2CAP and promoting payload"
            )
            resendPayload(endpoint, flight.transfer.payloadBytes())
            promoteToL2cap(endpoint)
            return
        }
        AppLogger.d("BLE_MESH", "Link ${flight.link.generation} $endpoint: $reason; retiring link")
        if (!isCurrentLink(flight.link)) {
            processNext(endpoint)
            return
        }
        if (flight.link.role == BleLinkRole.CLIENT) disconnectClient(endpoint, flight.gatt)
        else flight.serverDevice?.let(disconnectServer)
    }

    private companion object {
        const val RETRY_DELAY_MS = 200L
        const val MAX_INITIATION_ATTEMPTS = 5
    }
}
