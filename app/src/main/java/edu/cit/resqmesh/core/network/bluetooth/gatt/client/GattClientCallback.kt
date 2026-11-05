package edu.cit.resqmesh.core.network.bluetooth.gatt.client

import edu.cit.resqmesh.core.network.bluetooth.BleConfig
import android.bluetooth.*
import edu.cit.resqmesh.core.model.ConnectedDevice
import edu.cit.resqmesh.core.network.bluetooth.state.BleLinkRole
import edu.cit.resqmesh.core.network.bluetooth.state.BleLinkState
import edu.cit.resqmesh.core.network.bluetooth.state.MeshFrameCodec
import edu.cit.resqmesh.core.logging.AppLogger
import edu.cit.resqmesh.core.network.bluetooth.gatt.GattClientHost
import edu.cit.resqmesh.core.network.bluetooth.gatt.server.GattWritePolicy

/** A partially received frame older than this is discarded as corrupt. */
private const val CLIENT_CHUNK_BUFFER_TIMEOUT_MS = 5000

/** The L2CAP PSM characteristic carries one big-endian Int. */
private const val PSM_VALUE_BYTES = 4

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
            handleMtuChanged(gatt, mtu, status)
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        handler.post {
            if (!isTransportGenerationCurrent(epoch)) return@post
            handleServicesDiscovered(gatt, status)
        }
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        handler.post {
            if (!isTransportGenerationCurrent(epoch)) return@post
            handleDescriptorWrite(gatt, status)
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
            if (!owns(gatt, "characteristic read $status")) return@post
            if (characteristic.uuid == BleConfig.L2CAP_PSM_CHARACTERISTIC_UUID) {
                val port = if (status == BluetoothGatt.GATT_SUCCESS && capturedValue?.size == PSM_VALUE_BYTES)
                    java.nio.ByteBuffer.wrap(capturedValue).int else null
                payloadSetup.onPort(port)
            }
        }
    }

    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        val capturedValue = characteristic.value?.copyOf()
        handler.post {
            if (!isTransportGenerationCurrent(epoch)) return@post
            handleNotification(gatt, capturedValue)
        }
    }

    override fun onCharacteristicWrite(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, status: Int) {
        handler.post {
            if (!isTransportGenerationCurrent(epoch)) return@post
            if (!owns(gatt, "characteristic write $status")) return@post
            completeGattChunk(macAddress, BleLinkRole.CLIENT, status, gatt)
        }
    }

    /** Enables TX notifications locally; false after tearing the link down on a revoked permission. */
    internal fun subscribeToTx(gatt: BluetoothGatt, txChar: BluetoothGattCharacteristic): Boolean = try {
        gatt.setCharacteristicNotification(txChar, true)
        true
    } catch (e: SecurityException) {
        abortForRevokedPermission("CLIENT notification setup skipped: BLUETOOTH_CONNECT was revoked")
        false
    }

    /** Starts the CCCD indication write; false after tearing the link down on a revoked permission. */
    internal fun writeCccd(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor): Boolean {
        link.currentOperation = "WRITE_CCCD"
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
            } else {
                descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }
            true
        } catch (e: SecurityException) {
            abortForRevokedPermission("CLIENT descriptor setup skipped: BLUETOOTH_CONNECT was revoked")
            false
        }
    }

    /** A missing TX characteristic or descriptor usually means a stale GATT cache on the phone. */
    internal fun clearCacheAndDisconnect(gatt: BluetoothGatt) {
        AppLogger.d("BLE_MESH", "Failed to setup TX Char/Descriptor (GATT Cache issue). Clearing Cache & Disconnecting.")
        try {
            val localMethod = gatt.javaClass.getMethod("refresh")
            localMethod.invoke(gatt)
        } catch (e: Exception) { AppLogger.d("BLE_MESH", "gatt refresh ignored: ${e.javaClass.simpleName}") }
        finishConnectPhase("tx characteristic missing")
        forceGattDisconnect(macAddress, gatt)
    }
}

private fun GattClientAttemptContext.handleMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
    if (!owns(gatt, "MTU $status")) return
    val mac = gatt.device.address
    if (status == BluetoothGatt.GATT_SUCCESS) {
        AppLogger.d("BLE_MESH", "MTU Expanded to $mtu.")
        val payloadSize = GattWritePolicy.payloadSizeForMtu(mtu)
        store.connectionMtu[mac] = payloadSize
        link.mtu = payloadSize
    } else {
        AppLogger.d("BLE_MESH", "MTU Expansion failed. Samsung Fallback to 23 bytes.")
        store.connectionMtu[mac] = DEFAULT_ATT_PAYLOAD_BYTES
        link.mtu = DEFAULT_ATT_PAYLOAD_BYTES
    }
    requestServicesOnce(gatt)
    payloadSetup.onMtu()
}

private fun GattClientCallback.handleServicesDiscovered(gatt: BluetoothGatt, status: Int) {
    when {
        isConnectPermissionRevoked() -> abortForRevokedPermission(
            "Ignoring CLIENT service callback for $peerName: BLUETOOTH_CONNECT was revoked"
        )
        !owns(gatt, "services $status") -> Unit
        else -> {
            link.currentOperation = null
            if (status == BluetoothGatt.GATT_SUCCESS) {
                configureDiscoveredLink(gatt, status)
            } else {
                AppLogger.d("BLE_MESH",
                    "GATT services discovery failed for ${macAddress}. Status: ${status}. Forcing UI disconnect.")
                finishConnectPhase("service discovery failed")
                forceGattDisconnect(macAddress, gatt)
            }
        }
    }
}

private fun GattClientCallback.configureDiscoveredLink(gatt: BluetoothGatt, status: Int) {
    if (!store.links.transition(link, BleLinkState.CONFIGURING)) return
    AppLogger.d("BLE_MESH", "Link ${link.generation} CLIENT $macAddress: CONFIGURING serviceStatus=$status")
    AppLogger.d("BLE_MESH", "GATT Services discovered for ${macAddress}. Ready to transmit.")
    when (enableTxIndications(gatt)) {
        IndicationSetup.WRITE_PENDING, IndicationSetup.ABORTED -> Unit
        IndicationSetup.UNAVAILABLE -> clearCacheAndDisconnect(gatt)
    }
}

private enum class IndicationSetup { WRITE_PENDING, UNAVAILABLE, ABORTED }

/** Subscribes to TX and starts the CCCD write. ABORTED means the link was already torn down. */
private fun GattClientCallback.enableTxIndications(gatt: BluetoothGatt): IndicationSetup {
    val txChar = gatt.getService(BleConfig.SERVICE_UUID)?.getCharacteristic(BleConfig.TX_CHARACTERISTIC_UUID)
    return when {
        txChar == null -> IndicationSetup.UNAVAILABLE
        !subscribeToTx(gatt, txChar) -> IndicationSetup.ABORTED
        else -> txChar.getDescriptor(BleConfig.CCC_DESCRIPTOR_UUID)?.let { descriptor ->
            if (writeCccd(gatt, descriptor)) IndicationSetup.WRITE_PENDING else IndicationSetup.ABORTED
        } ?: IndicationSetup.UNAVAILABLE
    }
}

private fun GattClientAttemptContext.handleDescriptorWrite(gatt: BluetoothGatt, status: Int) {
    when {
        isConnectPermissionRevoked() -> abortForRevokedPermission(
            "Ignoring CLIENT descriptor callback for $peerName: BLUETOOTH_CONNECT was revoked"
        )
        !owns(gatt, "descriptor $status") -> Unit
        else -> {
            link.currentOperation = null
            if (status == BluetoothGatt.GATT_SUCCESS) {
                markClientReady()
            } else {
                AppLogger.d("BLE_MESH",
                    "GATT descriptor write failed for ${macAddress}. Status: ${status}. Forcing UI disconnect.")
                finishConnectPhase("descriptor write failed")
                forceGattDisconnect(macAddress, gatt)
            }
        }
    }
}

/** CCCD indications are on: the link is READY for payloads. */
private fun GattClientAttemptContext.markClientReady() {
    if (!store.links.transition(link, BleLinkState.READY)) return
    AppLogger.d("BLE_MESH",
        "Link ${link.generation} CLIENT $macAddress: READY descriptorStatus=${BluetoothGatt.GATT_SUCCESS}")
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
}

private fun GattClientAttemptContext.handleNotification(gatt: BluetoothGatt, capturedValue: ByteArray?) {
    if (!owns(gatt, "notification")) return
    val value = capturedValue ?: return
    if (store.guards.admitChunk("$macAddress/client", value.size)) {
        appendNotificationChunk(value)
    } else {
        link.receiveBuffer.clear()
    }
}

private fun GattClientAttemptContext.appendNotificationChunk(value: ByteArray) {
    val now = System.currentTimeMillis()
    if (now - link.lastGattChunkAt > CLIENT_CHUNK_BUFFER_TIMEOUT_MS && !link.receiveBuffer.isEmpty()) {
        AppLogger.d("BLE_MESH", "Client Buffer timeout! Clearing corrupted chunk buffer for $macAddress")
        link.receiveBuffer.clear()
    }
    link.lastGattChunkAt = now
    store.connectionInteractionTimes[macAddress] = now
    link.lastInteractionAt = now

    when (val result = link.receiveBuffer.append(value)) {
        is MeshFrameCodec.AppendResult.Accepted -> {
            result.payloads.forEach { processBinaryPayload(macAddress, it) }
        }
        is MeshFrameCodec.AppendResult.Rejected -> {
            AppLogger.d("BLE_MESH", "Rejected malformed CLIENT frame from $macAddress: ${result.reason}")
        }
    }
}
