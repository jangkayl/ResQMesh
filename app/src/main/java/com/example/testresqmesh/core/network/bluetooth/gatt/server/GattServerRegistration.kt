package com.example.testresqmesh.core.network.bluetooth.gatt.server

import android.bluetooth.*
import com.example.testresqmesh.core.network.bluetooth.gatt.GattServerHost

internal class GattServerRegistration(
    private val manager: GattServerHost,
    val serverSetupTimeoutMs: Long,
    private val openServer: (BluetoothGattServerCallback) -> BluetoothGattServer?,
    private val addService: (BluetoothGattService) -> Boolean
) : GattServerHost by manager {
    val host get() = manager
    val epoch = transportGeneration
    val serverEpoch = beginServerRegistration()
    fun ownsServer() = isTransportGenerationCurrent(epoch) && ownsServerRegistration(serverEpoch)

    fun start(serverCallback: GattServerCallback) {
        gattServer = openServer(serverCallback)

        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val rxChar = BluetoothGattCharacteristic(
            RX_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(rxChar)

        val txChar = BluetoothGattCharacteristic(
            TX_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_INDICATE,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        val cccDescriptor = BluetoothGattDescriptor(CCC_DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_WRITE)
        txChar.addDescriptor(cccDescriptor)
        service.addCharacteristic(txChar)

        val psmChar = BluetoothGattCharacteristic(
            L2CAP_PSM_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        service.addCharacteristic(psmChar)

        if (!addService(service)) {
            failTransport(epoch)
            return
        }
        if (l2capServerSocket == null) startL2capServer()
    }
}
