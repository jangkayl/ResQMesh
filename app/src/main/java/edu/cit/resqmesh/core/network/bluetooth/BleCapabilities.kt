package edu.cit.resqmesh.core.network.bluetooth

import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build

/** Read-only snapshot of BLE radio features. Null means unknown/unavailable. */
data class BleCapabilities(
    val codedPhy: Boolean? = null,
    val phy2M: Boolean? = null,
    val extendedAdvertising: Boolean? = null,
    val maxAdvertisingDataLength: Int? = null
) {
    companion object {
        fun read(context: Context): BleCapabilities {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return BleCapabilities()
            return try {
                val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                    ?: return BleCapabilities()
                BleCapabilities(
                    codedPhy = adapter.isLeCodedPhySupported,
                    phy2M = adapter.isLe2MPhySupported,
                    extendedAdvertising = adapter.isLeExtendedAdvertisingSupported,
                    maxAdvertisingDataLength = adapter.leMaximumAdvertisingDataLength
                )
            } catch (_: SecurityException) {
                BleCapabilities()
            } catch (_: Exception) {
                BleCapabilities()
            }
        }
    }
}
