package com.example.testresqmesh.core.network.bluetooth.gatt.server

import android.bluetooth.*
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.testresqmesh.core.utils.AppLogger
import com.example.testresqmesh.core.network.bluetooth.gatt.GattServerHost

internal class GattL2capAcceptor(
    private val context: Context,
    private val manager: GattServerHost
) {
    fun startL2capServer() {
        if (!com.example.testresqmesh.BuildConfig.BLE_L2CAP_ENABLED) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            AppLogger.d("BLE_MESH", "L2CAP server not started: BLUETOOTH_CONNECT is not granted")
            return
        }
        with(manager) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                val epoch = transportGeneration
                val listener = bluetoothAdapter?.listenUsingInsecureL2capChannel() ?: return
                l2capServerSocket = listener
                myL2capPsm = listener.psm
                AppLogger.d("BLE_MESH", "L2CAP Server started on PSM: $myL2capPsm")

                l2capAcceptThread = Thread {
                    while (isTransportGenerationCurrent(epoch)) {
                        try {
                            val socket = listener.accept()
                            if (socket != null) {
                                AppLogger.d("BLE_MESH", "L2CAP Connection Accepted from ${socket.remoteDevice.address}")
                                handler.post {
                                    if (isTransportGenerationCurrent(epoch) && l2capServerSocket === listener) {
                                        handleL2capConnection(socket.remoteDevice.address, socket)
                                    } else try { socket.close() } catch (_: Exception) {}
                                }
                            }
                        } catch (e: Exception) {
                            if (store.isNodeActive.get()) {
                                AppLogger.d("BLE_MESH", "L2CAP Accept Thread error: ${e.message}")
                            }
                            break
                        }
                    }
                }
                l2capAcceptThread?.start()
            } catch (e: Exception) {
                AppLogger.d("BLE_MESH", "Failed to start L2CAP server: ${e.message}")
            }
        } else {
            AppLogger.d("BLE_MESH", "L2CAP CoC not supported on this Android version. Falling back to GATT exclusively.")
            myL2capPsm = 0
        }

        }
    }
}
