package edu.cit.resqmesh.core.network.bluetooth.gatt.server

import android.bluetooth.BluetoothGatt

/** Framework-free decisions for inbound GATT server writes so they can be unit tested on the JVM. */
internal object GattWritePolicy {
    const val MAX_PREPARED_BYTES = 4 * 1024
    const val MAX_PREPARED_DEVICES = 8
    const val MIN_PAYLOAD_BYTES = 20
    const val MAX_PAYLOAD_BYTES = 512
    private const val ATT_OVERHEAD_BYTES = 3

    /** Usable bytes per ATT write for a negotiated MTU, clamped against bogus peer values. */
    fun payloadSizeForMtu(mtu: Int): Int = (mtu - ATT_OVERHEAD_BYTES).coerceIn(MIN_PAYLOAD_BYTES, MAX_PAYLOAD_BYTES)

    /** Status for a write request before any buffering; GATT_SUCCESS means "accept". */
    fun checkWrite(targetsMeshData: Boolean, preparedWrite: Boolean, offset: Int): Int = when {
        !targetsMeshData -> BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
        !preparedWrite && offset != 0 -> BluetoothGatt.GATT_INVALID_OFFSET
        else -> BluetoothGatt.GATT_SUCCESS
    }

    /** Per-device prepared-write queues. Not thread-safe; used from the BLE handler thread only. */
    class PreparedWrites(
        private val maxBytes: Int = MAX_PREPARED_BYTES,
        private val maxDevices: Int = MAX_PREPARED_DEVICES
    ) {
        private val buffers = LinkedHashMap<String, ByteArray>()

        fun add(device: String, offset: Int, value: ByteArray?): Int {
            val chunk = value ?: ByteArray(0)
            val current = buffers[device]
            val currentSize = current?.size ?: 0
            val status = when {
                offset != currentSize -> BluetoothGatt.GATT_INVALID_OFFSET
                current == null && buffers.size >= maxDevices -> BluetoothGatt.GATT_FAILURE
                currentSize.toLong() + chunk.size > maxBytes -> BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH
                else -> BluetoothGatt.GATT_SUCCESS
            }
            if (status == BluetoothGatt.GATT_SUCCESS) buffers[device] = (current ?: ByteArray(0)) + chunk
            return status
        }

        /** Removes and returns the assembled value, or null when nothing was queued. */
        fun take(device: String): ByteArray? = buffers.remove(device)

        fun clear(device: String) {
            buffers.remove(device)
        }

        fun clearAll() = buffers.clear()
    }
}
