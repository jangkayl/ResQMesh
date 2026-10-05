package com.example.testresqmesh.core.network.bluetooth.gatt.client

import android.bluetooth.*
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.network.bluetooth.state.BleLinkRole
import com.example.testresqmesh.core.network.bluetooth.state.BleLinkState
import com.example.testresqmesh.core.network.bluetooth.state.MeshFrameCodec
import com.example.testresqmesh.core.utils.AppLogger
import com.example.testresqmesh.core.network.bluetooth.gatt.GattClientHost

internal class GattClientCallback(
    override val attempt: GattClientAttempt
) : BluetoothGattCallback(), GattClientHost by attempt, GattClientAttemptContext {
    private val connectionEvents = GattClientConnectionEvents(attempt)
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        connectionEvents.onConnectionStateChange(gatt, status, newState)
    }

    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        handler.post {
            if (!isTransportGenerationCurrent(epoch)) return@post
            fun handleCallback() {
                if (!owns(gatt, "MTU $status")) return
                val mac = gatt.device.address
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    AppLogger.d("BLE_MESH", "MTU Expanded to $mtu.")
                    store.connectionMtu[mac] = mtu - 3
                    link.mtu = mtu - 3
                } else {
                    AppLogger.d("BLE_MESH", "MTU Expansion failed. Samsung Fallback to 23 bytes.")
                    store.connectionMtu[mac] = 20
                    link.mtu = 20
                }
                requestServicesOnce(gatt)
                payloadSetup.onMtu()

            }
            handleCallback()
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        handler.post {
            if (!isTransportGenerationCurrent(epoch)) return@post
            fun handleCallback() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                ) {
                    AppLogger.d("BLE_MESH", "Ignoring CLIENT service callback for $peerName: BLUETOOTH_CONNECT was revoked")
                    finishConnectPhase("Bluetooth permission revoked")
                    store.links.transition(link, BleLinkState.DISCONNECTING)
                    store.links.forget(link)
                    return
                }
                if (!owns(gatt, "services $status")) return
                link.currentOperation = null
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (!store.links.transition(link, BleLinkState.CONFIGURING)) return
                    AppLogger.d("BLE_MESH", "Link ${link.generation} CLIENT $macAddress: CONFIGURING serviceStatus=$status")
                    AppLogger.updateLink(macAddress, peerName, "CLIENT", link.generation, "CONFIGURING")
                    AppLogger.d("BLE_MESH", "GATT Services discovered for ${macAddress}. Ready to transmit.")

                    val service = gatt.getService(SERVICE_UUID)
                    val txChar = service?.getCharacteristic(TX_CHARACTERISTIC_UUID)
                    var descriptorWritePending = false
                    if (txChar != null) {
                        try {
                            gatt.setCharacteristicNotification(txChar, true)
                        } catch (e: SecurityException) {
                            AppLogger.d("BLE_MESH", "CLIENT notification setup skipped: BLUETOOTH_CONNECT was revoked")
                            finishConnectPhase("Bluetooth permission revoked")
                            store.links.transition(link, BleLinkState.DISCONNECTING)
                            store.links.forget(link)
                            return
                        }
                        val descriptor = txChar.getDescriptor(CCC_DESCRIPTOR_UUID)
                        if (descriptor != null) {
                            descriptorWritePending = true
                            link.currentOperation = "WRITE_CCCD"
                            try {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                                } else {
                                    descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                                    gatt.writeDescriptor(descriptor)
                                }
                            } catch (e: SecurityException) {
                                AppLogger.d("BLE_MESH", "CLIENT descriptor setup skipped: BLUETOOTH_CONNECT was revoked")
                                finishConnectPhase("Bluetooth permission revoked")
                                store.links.transition(link, BleLinkState.DISCONNECTING)
                                store.links.forget(link)
                                return
                            }
                        }
                    }

                    if (!descriptorWritePending) {
                        AppLogger.d("BLE_MESH", "Failed to setup TX Char/Descriptor (GATT Cache issue). Clearing Cache & Disconnecting.")
                        try {
                            val localMethod = gatt.javaClass.getMethod("refresh")
                            localMethod.invoke(gatt)
                        } catch (e: Exception) {}
                        finishConnectPhase("tx characteristic missing")
                        forceGattDisconnect(macAddress, gatt)
                    }
                } else {
                    AppLogger.d("BLE_MESH", "GATT services discovery failed for ${macAddress}. Status: ${status}. Forcing UI disconnect.")
                    finishConnectPhase("service discovery failed")
                    forceGattDisconnect(macAddress, gatt)
                }

            }
            handleCallback()
        }
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        handler.post {
            if (!isTransportGenerationCurrent(epoch)) return@post
            fun handleCallback() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                ) {
                    AppLogger.d("BLE_MESH", "Ignoring CLIENT descriptor callback for $peerName: BLUETOOTH_CONNECT was revoked")
                    finishConnectPhase("Bluetooth permission revoked")
                    store.links.transition(link, BleLinkState.DISCONNECTING)
                    store.links.forget(link)
                    return
                }
                if (!owns(gatt, "descriptor $status")) return
                link.currentOperation = null
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (!store.links.transition(link, BleLinkState.READY)) return
                    AppLogger.d("BLE_MESH", "Link ${link.generation} CLIENT $macAddress: READY descriptorStatus=$status")
                    AppLogger.updateLink(macAddress, peerName, "CLIENT", link.generation, "READY")
                    AppLogger.d("BLE_MESH", "GATT descriptor written successfully for ${macAddress}.")
                    handler.post {
                        if (!isTransportGenerationCurrent(epoch)) return@post
                        if (store.links.isCurrent(link) && link.state == BleLinkState.READY) {
                            onDeviceConnected?.invoke(ConnectedDevice(
                                endpointId = macAddress,
                                name = store.connectedEndpointNames[macAddress] ?: peerName,
                                isClassicConnected = true,
                                nodeId = link.peerNodeId.orEmpty(),
                                isPayloadReady = true
                            ))
                        }
                    }

                    finishConnectPhase("descriptor written")
                    payloadSetup.start()
                    sendSystemPulse(forceFull = true)
                    processNextPayload(macAddress)
                } else {
                    AppLogger.d("BLE_MESH", "GATT descriptor write failed for ${macAddress}. Status: ${status}. Forcing UI disconnect.")
                    finishConnectPhase("descriptor write failed")
                    forceGattDisconnect(macAddress, gatt)
                }

            }
            handleCallback()
        }
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        val capturedValue = characteristic.value?.copyOf()
        handler.post {
            if (!isTransportGenerationCurrent(epoch)) return@post
            fun handleCallback() {
                if (!owns(gatt, "characteristic read $status")) return
                if (characteristic.uuid == L2CAP_PSM_CHARACTERISTIC_UUID) {
                    val port = if (status == BluetoothGatt.GATT_SUCCESS && capturedValue?.size == 4)
                        java.nio.ByteBuffer.wrap(capturedValue).int else null
                    payloadSetup.onPort(port)
                }

            }
            handleCallback()
        }
    }

    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        val capturedValue = characteristic.value?.copyOf()
        handler.post {
            if (!isTransportGenerationCurrent(epoch)) return@post
            fun handleCallback() {
                if (!owns(gatt, "notification")) return
                val value = capturedValue ?: return
                val now = System.currentTimeMillis()
                if (now - link.lastGattChunkAt > 5000 && link.receiveBuffer.isNotEmpty()) {
                    AppLogger.d("BLE_MESH", "Client Buffer timeout! Clearing corrupted chunk buffer for $macAddress")
                    link.receiveBuffer = byteArrayOf()
                }
                link.lastGattChunkAt = now
                store.connectionInteractionTimes[macAddress] = now
                link.lastInteractionAt = now

                when (val result = MeshFrameCodec.append(link.receiveBuffer, value)) {
                    is MeshFrameCodec.AppendResult.Accepted -> {
                        result.payloads.forEach { processBinaryPayload(macAddress, it) }
                        link.receiveBuffer = result.remainder
                    }
                    is MeshFrameCodec.AppendResult.Rejected -> {
                        AppLogger.d("BLE_MESH", "Rejected malformed CLIENT frame from $macAddress: ${result.reason}")
                        link.receiveBuffer = byteArrayOf()
                    }
                }

            }
            handleCallback()
        }
    }

    override fun onCharacteristicWrite(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, status: Int) {
        handler.post {
            if (!isTransportGenerationCurrent(epoch)) return@post
            fun handleCallback() {
                if (!owns(gatt, "characteristic write $status")) return
                completeGattChunk(macAddress, BleLinkRole.CLIENT, status, gatt)

            }
            handleCallback()
        }
    }
}
