package edu.cit.resqmesh.core.network.bluetooth.gatt.server

import android.bluetooth.*
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import edu.cit.resqmesh.core.model.ConnectedDevice
import edu.cit.resqmesh.core.model.NodeIdentity
import edu.cit.resqmesh.core.network.bluetooth.state.BleLink
import edu.cit.resqmesh.core.network.bluetooth.state.BleLinkRole
import edu.cit.resqmesh.core.network.bluetooth.state.BleLinkState
import edu.cit.resqmesh.core.network.bluetooth.state.MeshFrameCodec
import edu.cit.resqmesh.core.logging.AppLogger
import edu.cit.resqmesh.core.network.bluetooth.gatt.GattServerHost

internal class GattServerAttributeRequests(
    override val registration: GattServerRegistration,
    private val defaultWrite: (BluetoothDevice, Int, BluetoothGattCharacteristic, Boolean, Boolean, Int, ByteArray?) -> Unit
) : GattServerHost by registration, GattServerRegistrationContext {
    private val preparedWrites = GattWritePolicy.PreparedWrites()

    fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
            handler.post {
                if (!ownsServer()) return@post
                fun handleCallback() {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                    ) {
                        AppLogger.d("BLE_MESH", "Ignoring SERVER descriptor callback: BLUETOOTH_CONNECT was revoked")
                        return
                    }
                    if (responseNeeded) {
                        try {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                        } catch (e: SecurityException) {
                            AppLogger.d("BLE_MESH", "SERVER descriptor response skipped: BLUETOOTH_CONNECT was revoked")
                            return
                        }
                    }
                    val link = store.links.current(device.address, BleLinkRole.SERVER)
                    if (link?.serverDevice?.address == device.address && descriptor.uuid == CCC_DESCRIPTOR_UUID &&
                        value?.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE) == true &&
                        store.links.transition(link, BleLinkState.READY)) {
                        finishRadioHandshake("server:${link.endpoint}:${link.generation}", "server ready")
                        AppLogger.d("BLE_MESH", "Link ${link.generation} SERVER ${device.address}: READY CCCD indications enabled")
                        AppLogger.updateLink(
                            device.address,
                            store.connectedEndpointNames[device.address],
                            "SERVER",
                            link.generation,
                            "READY"
                        )
                        handler.post {
                            if (!ownsServer()) return@post
                            if (store.links.isCurrent(link) && link.state == BleLinkState.READY) {
                                val name = store.connectedEndpointNames[device.address] ?: NodeIdentity.UNKNOWN_NAME
                                onDeviceConnected?.invoke(ConnectedDevice(
                                    endpointId = device.address,
                                    name = name,
                                    isClassicConnected = true,
                                    isProvisional = NodeIdentity.isPlaceholder(name),
                                    nodeId = link.peerNodeId.orEmpty(),
                                    isPayloadReady = true
                                ))
                            }
                        }
                        processNextPayload(device.address)
                        if (NodeIdentity.isPlaceholder(store.connectedEndpointNames[device.address])) {
                            handler.postDelayed({
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    AppLogger.d("BLE_MESH", "SERVER identity deadline ended: BLUETOOTH_CONNECT was revoked")
                                    return@postDelayed
                                }
                                if (store.links.isCurrent(link) && link.state == BleLinkState.READY &&
                                    NodeIdentity.isPlaceholder(store.connectedEndpointNames[device.address])) {
                                    AppLogger.d("BLE_MESH", "Server: READY link received no identity; disconnecting ${device.address}.")
                                    try {
                                        gattServer?.cancelConnection(device)
                                    } catch (e: SecurityException) {
                                        AppLogger.d("BLE_MESH", "SERVER identity timeout disconnect skipped: BLUETOOTH_CONNECT was revoked")
                                    }
                                }
                            }, NAME_HANDSHAKE_TIMEOUT_MS)
                        }
                    }

                }
                handleCallback()
            }
        }

    fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            handler.post {
                if (!ownsServer()) return@post
                fun handleCallback() {
                    AppLogger.d("BLE_MESH", "Server: MTU Expanded to $mtu for ${device.address}.")
                    val payloadSize = GattWritePolicy.payloadSizeForMtu(mtu)
                    store.connectionMtu[device.address] = payloadSize
                    store.links.current(device.address, BleLinkRole.SERVER)
                        ?.takeIf { it.serverDevice?.address == device.address }?.mtu = payloadSize

                }
                handleCallback()
            }
        }

    fun onNotificationSent(device: BluetoothDevice, status: Int) {
            if (!ownsServer()) return
            val ticket = store.serverIndications.take(device.address, serverEpoch) ?: return
            completeServerIndication(ticket, status)
        }

    fun onCharacteristicReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        characteristic: BluetoothGattCharacteristic
    ) {
            handler.post {
                if (!ownsServer()) return@post
                fun handleCallback() {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                    ) {
                        AppLogger.d("BLE_MESH", "Ignoring SERVER read request: BLUETOOTH_CONNECT was revoked")
                        return
                    }
                    if (characteristic.uuid == L2CAP_PSM_CHARACTERISTIC_UUID) {
                        val psmBytes = java.nio.ByteBuffer.allocate(4).putInt(myL2capPsm).array()
                        try {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, psmBytes)
                        } catch (e: SecurityException) {
                            AppLogger.d("BLE_MESH", "SERVER PSM response skipped: BLUETOOTH_CONNECT was revoked")
                        }
                    } else {
                        try {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
                        } catch (e: SecurityException) {
                            AppLogger.d("BLE_MESH", "SERVER read rejection skipped: BLUETOOTH_CONNECT was revoked")
                        }
                    }

                }
                handleCallback()
            }
        }

    fun onCharacteristicWriteRequest(
        device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
        preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?
    ) {
            val receivingLink = store.links.current(device.address, BleLinkRole.SERVER) ?: return
            handler.post {
                if (!ownsServer() || !store.links.isCurrent(receivingLink)) return@post
                fun handleCallback() {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                    ) {
                        AppLogger.d("BLE_MESH", "Ignoring SERVER write request: BLUETOOTH_CONNECT was revoked")
                        return
                    }
                    defaultWrite(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
                    val status = writeStatus(device, characteristic, preparedWrite, offset, value)
                    // Only prepared writes echo the chunk (ATT lets the client verify it); plain writes carry no value.
                    val echo = if (preparedWrite && status == BluetoothGatt.GATT_SUCCESS) value else null
                    val responded = !responseNeeded || respond(device, requestId, status, offset, echo)
                    if (responded && status == BluetoothGatt.GATT_SUCCESS && !preparedWrite) {
                        value?.let { deliverWrite(device, receivingLink, it) }
                    }
                }
                handleCallback()
            }
        }

    private fun writeStatus(
        device: BluetoothDevice, characteristic: BluetoothGattCharacteristic,
        preparedWrite: Boolean, offset: Int, value: ByteArray?
    ): Int {
        var status = GattWritePolicy.checkWrite(characteristic.uuid == RX_CHARACTERISTIC_UUID, preparedWrite, offset)
        if (status == BluetoothGatt.GATT_SUCCESS && preparedWrite) {
            status = preparedWrites.add(device.address, offset, value)
        }
        if (status != BluetoothGatt.GATT_SUCCESS) {
            if (preparedWrite) preparedWrites.clear(device.address)
            AppLogger.d(
                "BLE_MESH",
                "Rejected SERVER write from ${device.address}: status=$status uuid=${characteristic.uuid} " +
                    "offset=$offset prepared=$preparedWrite"
            )
        }
        return status
    }

    private fun respond(device: BluetoothDevice, requestId: Int, status: Int, offset: Int, value: ByteArray?): Boolean =
        try {
            gattServer?.sendResponse(device, requestId, status, offset, value)
            true
        } catch (e: SecurityException) {
            AppLogger.d("BLE_MESH", "SERVER write response skipped: BLUETOOTH_CONNECT was revoked")
            false
        }

    fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
            handler.post {
                if (!ownsServer()) return@post
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                ) {
                    AppLogger.d("BLE_MESH", "Ignoring SERVER execute write: BLUETOOTH_CONNECT was revoked")
                    return@post
                }
                val assembled = preparedWrites.take(device.address)
                val link = store.links.current(device.address, BleLinkRole.SERVER)
                val deliverable = assembled?.takeIf { execute && link != null && it.isNotEmpty() }
                val failed = execute && deliverable == null
                val status = if (failed) BluetoothGatt.GATT_FAILURE else BluetoothGatt.GATT_SUCCESS
                if (respond(device, requestId, status, 0, null) && deliverable != null) {
                    link?.let { deliverWrite(device, it, deliverable) }
                }
            }
        }

    /** Drops queued prepared writes; call when a device disconnects. */
    fun onDeviceDisconnected(address: String) {
        handler.post { preparedWrites.clear(address) }
    }

    private fun deliverWrite(device: BluetoothDevice, receivingLink: BleLink, value: ByteArray) {
        val macAddress = device.address
        if (!store.guards.admitChunk("$macAddress/server", value.size)) {
            receivingLink.receiveBuffer.clear()
            return
        }
        val now = System.currentTimeMillis()
        if (now - receivingLink.lastGattChunkAt > 5000 && !receivingLink.receiveBuffer.isEmpty()) {
            AppLogger.d("BLE_MESH", "Server Buffer timeout! Clearing corrupted chunk buffer for $macAddress")
            receivingLink.receiveBuffer.clear()
        }
        receivingLink.lastGattChunkAt = now
        store.connectionInteractionTimes[macAddress] = now
        store.links.current(macAddress, BleLinkRole.SERVER)?.takeIf { it.serverDevice?.address == device.address }?.lastInteractionAt = now

        when (val result = receivingLink.receiveBuffer.append(value)) {
            is MeshFrameCodec.AppendResult.Accepted -> {
                result.payloads.forEach { payload -> processBinaryPayload(macAddress, payload) }
            }
            is MeshFrameCodec.AppendResult.Rejected -> {
                AppLogger.d("BLE_MESH", "Rejected malformed SERVER frame from $macAddress: ${result.reason}")
            }
        }
    }
}
