package edu.cit.resqmesh.core.network.bluetooth.gatt.client

import android.bluetooth.*
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import edu.cit.resqmesh.core.logging.AppLogger
import edu.cit.resqmesh.core.network.bluetooth.state.BleLinkState
import edu.cit.resqmesh.core.network.bluetooth.gatt.GattClientHost

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
    val connectTimeoutKey get() = attempt.connectTimeoutKey
    fun owns(gatt: BluetoothGatt, event: String) = attempt.owns(gatt, event)
    fun requestServicesOnce(gatt: BluetoothGatt) = attempt.requestServicesOnce(gatt)
    fun finishConnectPhase(reason: String) = attempt.finishConnectPhase(reason)
}

/** Android 12+ needs BLUETOOTH_CONNECT for every GATT call; it can be revoked mid-connection. */
internal fun GattClientAttemptContext.isConnectPermissionRevoked(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
        PackageManager.PERMISSION_GRANTED

/** Logs [message], ends the connect phase and retires this attempt's link. */
internal fun GattClientAttemptContext.abortForRevokedPermission(message: String) {
    AppLogger.d("BLE_MESH", message)
    finishConnectPhase("Bluetooth permission revoked")
    store.links.transition(link, BleLinkState.DISCONNECTING)
    store.links.forget(link)
}
