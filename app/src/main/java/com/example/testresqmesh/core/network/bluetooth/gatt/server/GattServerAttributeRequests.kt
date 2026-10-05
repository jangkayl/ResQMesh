package com.example.testresqmesh.core.network.bluetooth.gatt.server

import android.bluetooth.*
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.network.bluetooth.state.BleLinkRole
import com.example.testresqmesh.core.network.bluetooth.state.BleLinkState
import com.example.testresqmesh.core.network.bluetooth.state.MeshFrameCodec
import com.example.testresqmesh.core.utils.AppLogger
import com.example.testresqmesh.core.network.bluetooth.gatt.GattServerHost

internal class GattServerAttributeRequests(
    override val registration: GattServerRegistration,
    private val defaultWrite: (BluetoothDevice, Int, BluetoothGattCharacteristic, Boolean, Boolean, Int, ByteArray?) -> Unit
) : GattServerHost by registration, GattServerRegistrationContext {
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
                    store.connectionMtu[device.address] = mtu - 3
                    store.links.current(device.address, BleLinkRole.SERVER)?.takeIf { it.serverDevice?.address == device.address }?.mtu = mtu - 3

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
                    if (responseNeeded) {
                        try {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                        } catch (e: SecurityException) {
                            AppLogger.d("BLE_MESH", "SERVER write response skipped: BLUETOOTH_CONNECT was revoked")
                            return
                        }
                    }
                    value?.let {
                        val macAddress = device.address
                        val now = System.currentTimeMillis()
                        if (now - receivingLink.lastGattChunkAt > 5000 && receivingLink.receiveBuffer.isNotEmpty()) {
                            AppLogger.d("BLE_MESH", "Server Buffer timeout! Clearing corrupted chunk buffer for $macAddress")
                            receivingLink.receiveBuffer = byteArrayOf()
                        }
                        receivingLink.lastGattChunkAt = now
                        store.connectionInteractionTimes[macAddress] = now
                        store.links.current(macAddress, BleLinkRole.SERVER)?.takeIf { it.serverDevice?.address == device.address }?.lastInteractionAt = now

                        when (val result = MeshFrameCodec.append(receivingLink.receiveBuffer, it)) {
                            is MeshFrameCodec.AppendResult.Accepted -> {
                                result.payloads.forEach { payload -> processBinaryPayload(macAddress, payload) }
                                receivingLink.receiveBuffer = result.remainder
                            }
                            is MeshFrameCodec.AppendResult.Rejected -> {
                                AppLogger.d("BLE_MESH", "Rejected malformed SERVER frame from $macAddress: ${result.reason}")
                                receivingLink.receiveBuffer = byteArrayOf()
                            }
                        }
                    }

                }
                handleCallback()
            }
        }
}
