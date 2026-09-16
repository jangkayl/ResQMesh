package com.example.testresqmesh.core.network.bluetooth.state

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean

/** Keep all chunks of one framed payload together; a probe may only overtake whole transfers. */
class GattTransfer(val frame: ByteArray, val heartbeatId: String? = null)

/** Return the unframed payload so a queued GATT transfer can be promoted to L2CAP safely. */
fun GattTransfer.payloadBytes(): ByteArray =
    if (frame.size >= Int.SIZE_BYTES) frame.copyOfRange(Int.SIZE_BYTES, frame.size) else ByteArray(0)

/** One callback-owned GATT transfer on one physical link generation. */
class GattTransferFlight(
    val transfer: GattTransfer,
    val link: BleLink,
    val queue: ConcurrentLinkedDeque<GattTransfer>,
    val writing: AtomicBoolean,
    val gatt: BluetoothGatt?,
    val serverDevice: BluetoothDevice?
) {
    var offset = 0
    var chunkLength = 0
    var retryCount = 0
    var operationId = 0L
}
