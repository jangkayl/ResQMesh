package edu.cit.resqmesh.core.network.bluetooth.gatt.server

import android.bluetooth.BluetoothGatt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GattWritePolicyTest {
    @Test fun mtuIsClampedToSaneRange() {
        assertEquals(20, GattWritePolicy.payloadSizeForMtu(-5))
        assertEquals(20, GattWritePolicy.payloadSizeForMtu(23))
        assertEquals(244, GattWritePolicy.payloadSizeForMtu(247))
        assertEquals(512, GattWritePolicy.payloadSizeForMtu(Int.MAX_VALUE))
    }

    @Test fun onlyMeshDataWritesAtOffsetZeroAreAccepted() {
        assertEquals(BluetoothGatt.GATT_SUCCESS, GattWritePolicy.checkWrite(true, false, 0))
        assertEquals(BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, GattWritePolicy.checkWrite(false, false, 0))
        assertEquals(BluetoothGatt.GATT_INVALID_OFFSET, GattWritePolicy.checkWrite(true, false, 5))
        assertEquals(BluetoothGatt.GATT_SUCCESS, GattWritePolicy.checkWrite(true, true, 5))
    }

    @Test fun preparedWritesAssembleInOrderAndExecuteOnce() {
        val writes = GattWritePolicy.PreparedWrites()
        assertEquals(BluetoothGatt.GATT_SUCCESS, writes.add("a", 0, byteArrayOf(1, 2)))
        assertEquals(BluetoothGatt.GATT_SUCCESS, writes.add("a", 2, byteArrayOf(3)))
        assertArrayEquals(byteArrayOf(1, 2, 3), writes.take("a"))
        assertNull(writes.take("a"))
    }

    @Test fun preparedWriteOffsetMustMatchBufferedLength() {
        val writes = GattWritePolicy.PreparedWrites()
        assertEquals(BluetoothGatt.GATT_INVALID_OFFSET, writes.add("a", 1, byteArrayOf(1)))
        writes.add("a", 0, byteArrayOf(1))
        assertEquals(BluetoothGatt.GATT_INVALID_OFFSET, writes.add("a", 0, byteArrayOf(2)))
    }

    @Test fun preparedWritesAreBoundedPerDeviceAndByDeviceCount() {
        val writes = GattWritePolicy.PreparedWrites(maxBytes = 4, maxDevices = 2)
        assertEquals(BluetoothGatt.GATT_SUCCESS, writes.add("a", 0, ByteArray(4)))
        assertEquals(BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH, writes.add("a", 4, ByteArray(1)))
        assertEquals(BluetoothGatt.GATT_SUCCESS, writes.add("b", 0, ByteArray(1)))
        assertEquals(BluetoothGatt.GATT_FAILURE, writes.add("c", 0, ByteArray(1)))
        writes.clear("a")
        assertEquals(BluetoothGatt.GATT_SUCCESS, writes.add("c", 0, ByteArray(1)))
    }
}
