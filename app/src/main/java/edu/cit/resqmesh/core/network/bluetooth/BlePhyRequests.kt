package edu.cit.resqmesh.core.network.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattServer
import android.content.Context
import android.os.Build
import edu.cit.resqmesh.core.logging.AppLogger

/**
 * Asks the controller to move one link to LE Coded PHY. These are link-layer requests, not ATT
 * operations, so they do not occupy the GATT write lane. Both return false when Coded cannot help.
 */
@SuppressLint("MissingPermission", "NewApi") // Callers own the permission; PhyPolicy gates API 26.
object BlePhyRequests {
    fun requestCoded(context: Context, gatt: BluetoothGatt, endpoint: String, reason: String): Boolean {
        val pref = PhyPolicy.codedPreference(Build.VERSION.SDK_INT, PhyPolicy.capabilities(context)) ?: return false
        return runCatching { gatt.setPreferredPhy(pref.txMask, pref.rxMask, pref.option) }
            .onSuccess { AppLogger.d("BLE_PHY", "link=CLIENT endpoint=$endpoint request=CODED_S8 reason=$reason") }
            .isSuccess
    }

    fun requestCoded(
        context: Context,
        server: BluetoothGattServer,
        device: BluetoothDevice,
        reason: String
    ): Boolean {
        val pref = PhyPolicy.codedPreference(Build.VERSION.SDK_INT, PhyPolicy.capabilities(context)) ?: return false
        return runCatching { server.setPreferredPhy(device, pref.txMask, pref.rxMask, pref.option) }
            .onSuccess {
                AppLogger.d("BLE_PHY", "link=SERVER endpoint=${device.address} request=CODED_S8 reason=$reason")
            }
            .isSuccess
    }

    fun logUpdate(role: String, endpoint: String, txPhy: Int, rxPhy: Int, status: Int) {
        AppLogger.d(
            "BLE_PHY",
            "link=$role endpoint=$endpoint tx=${PhyPolicy.phyName(txPhy)} rx=${PhyPolicy.phyName(rxPhy)} status=$status"
        )
    }
}
