package com.example.testresqmesh.core.network.bluetooth.session

import android.bluetooth.*

internal class BleTransportResources {
    var gattServer: BluetoothGattServer? = null
    var l2capServerSocket: android.bluetooth.BluetoothServerSocket? = null
    var myL2capPsm: Int = 0
    var l2capAcceptThread: Thread? = null
    @Volatile var serverRegistrationGeneration = 0L
}
