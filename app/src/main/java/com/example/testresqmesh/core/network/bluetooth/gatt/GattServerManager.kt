package com.example.testresqmesh.core.network.bluetooth.gatt

import com.example.testresqmesh.core.network.bluetooth.gatt.server.GattServerRegistration
import com.example.testresqmesh.core.network.bluetooth.gatt.server.GattServerCallback
import com.example.testresqmesh.core.network.bluetooth.gatt.server.GattL2capAcceptor
import android.bluetooth.*
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.testresqmesh.core.network.NativeBleManager
import com.example.testresqmesh.core.utils.AppLogger

class GattServerManager(
    val context: Context,
    val manager: GattServerHost
) {
    constructor(context: Context, manager: NativeBleManager) : this(context, NativeGattHost(manager))

    // The elected client owns the normal 15 s setup deadline. This longer server deadline is only a
    // safety backstop for a vanished client whose disconnect callback never arrives.
    private val l2capAcceptor = GattL2capAcceptor(context, manager)
    private val serverSetupTimeoutMs = 20_000L

    fun startGattServer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            AppLogger.d("BLE_MESH", "GATT server not started: BLUETOOTH_CONNECT is not granted")
            return
        }
        with(manager) {
        val registration = GattServerRegistration(manager, serverSetupTimeoutMs,
            openServer = { callback -> bluetoothManager.openGattServer(context, callback) },
            addService = { service -> gattServer?.addService(service) == true })
        val serverCallback = GattServerCallback(registration)
        registration.start(serverCallback)

        }
    }

    fun startL2capServer() = l2capAcceptor.startL2capServer()
}
