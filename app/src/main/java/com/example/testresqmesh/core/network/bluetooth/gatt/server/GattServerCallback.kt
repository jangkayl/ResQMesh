package com.example.testresqmesh.core.network.bluetooth.gatt.server

import android.bluetooth.*
import com.example.testresqmesh.core.network.bluetooth.gatt.GattServerHost

internal class GattServerCallback(
    override val registration: GattServerRegistration
) : BluetoothGattServerCallback(), GattServerHost by registration, GattServerRegistrationContext {
    private val links = GattServerLinkEvents(registration)
    private val attributes = GattServerAttributeRequests(registration) { device, requestId, characteristic, preparedWrite, responseNeeded, offset, value ->
        super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
    }
    override fun onServiceAdded(status: Int, service: BluetoothGattService) {
        handler.post {
            if (!ownsServer()) return@post
            if (status == BluetoothGatt.GATT_SUCCESS) onGattServerReady(epoch) else failTransport(epoch)
        }
    }
    override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
        links.onConnectionStateChange(device, status, newState)
    }
    override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
        attributes.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value)
    }

    override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
        attributes.onMtuChanged(device, mtu)
    }

    override fun onNotificationSent(device: BluetoothDevice, status: Int) {
        attributes.onNotificationSent(device, status)
    }

    override fun onCharacteristicReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        characteristic: BluetoothGattCharacteristic
    ) {
        attributes.onCharacteristicReadRequest(device, requestId, offset, characteristic)
    }

    override fun onCharacteristicWriteRequest(
        device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
        preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?
    ) {
        attributes.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
    }
}
