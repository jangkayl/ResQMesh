package com.example.testresqmesh.core.network.bluetooth.gatt.client

import android.bluetooth.*
import com.example.testresqmesh.core.network.bluetooth.gatt.GattClientHost

/** Live host capabilities and one captured attempt; never owns another resource. */
internal interface GattClientAttemptContext : GattClientHost {
    val attempt: GattClientAttempt
    val manager get() = attempt.manager
    val macAddress get() = attempt.macAddress
    val peerName get() = attempt.peerName
    val epoch get() = attempt.epoch
    val link get() = attempt.link
    val payloadSetup get() = attempt.payloadSetup
    val stablePeerId get() = attempt.stablePeerId
    val useExplicitLeTransport get() = attempt.useExplicitLeTransport
    val radioOwner get() = attempt.radioOwner
    val timeoutHandler get() = attempt.timeoutHandler
    val connectTimeoutRunnable get() = attempt.connectTimeoutRunnable
    fun owns(gatt: BluetoothGatt, event: String) = attempt.owns(gatt, event)
    fun requestServicesOnce(gatt: BluetoothGatt) = attempt.requestServicesOnce(gatt)
    fun finishConnectPhase(reason: String) = attempt.finishConnectPhase(reason)
}
