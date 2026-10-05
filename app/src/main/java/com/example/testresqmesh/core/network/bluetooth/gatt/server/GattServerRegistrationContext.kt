package com.example.testresqmesh.core.network.bluetooth.gatt.server

import android.bluetooth.*
import com.example.testresqmesh.core.network.bluetooth.gatt.GattServerHost

internal interface GattServerRegistrationContext : GattServerHost {
    val registration: GattServerRegistration
    val manager get() = registration.host
    val epoch get() = registration.epoch
    val serverEpoch get() = registration.serverEpoch
    val serverSetupTimeoutMs get() = registration.serverSetupTimeoutMs
    fun ownsServer() = registration.ownsServer()
}
