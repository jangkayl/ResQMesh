package com.example.testresqmesh.core.network.bluetooth

import android.bluetooth.BluetoothSocket
import android.os.Handler
import com.example.testresqmesh.core.network.TransportDispatchResult
import com.example.testresqmesh.core.network.bluetooth.state.BleLinkRole
import com.example.testresqmesh.core.network.bluetooth.state.BoundedPayloadWriter
import com.example.testresqmesh.core.network.bluetooth.state.BleStateStore
import com.example.testresqmesh.core.network.bluetooth.state.MeshFrameCodec
import com.example.testresqmesh.core.network.bluetooth.state.OutboundQueuePolicy
import com.example.testresqmesh.core.utils.AppLogger
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.concurrent.ConcurrentHashMap

/** Owns one endpoint's L2CAP socket lifecycle without deciding GATT admission or payload policy. */
class L2capTransport(
    private val store: BleStateStore,
    private val handler: Handler,
    private val hasLiveGattRole: (String) -> Boolean,
    private val onPayload: (String, ByteArray) -> Unit,
    private val onPromoteGattWork: (String) -> Unit,
    private val onSocketLost: (String) -> Unit,
    private val onWriteFailure: (String, ByteArray) -> Unit,
    private val onConnected: () -> Unit
) {
    private val writers = ConcurrentHashMap<String, BoundedPayloadWriter>()

    @Synchronized fun attach(endpoint: String, socket: BluetoothSocket) {
        val existing = store.activeL2capSockets[endpoint]
        if (existing === socket) return
        val inherited = writers[endpoint]?.close().orEmpty()
        disconnect(endpoint)
        store.activeL2capSockets[endpoint] = socket
        val gattOwners = BleLinkRole.entries.mapNotNull { store.links.current(endpoint, it) }
        fun isOwned() = store.isNodeActive.get() && store.activeL2capSockets[endpoint] === socket &&
            hasLiveGattRole(endpoint) && store.links.ownsEndpoint(endpoint, gattOwners)
        fun lost(abandoned: List<ByteArray>) {
            synchronized(this@L2capTransport) {
                if (!store.activeL2capSockets.remove(endpoint, socket)) return
                writers.remove(endpoint)?.close()
                store.l2capOutboundProgressTimes.remove(endpoint)
                close(socket)
            }
            handler.post {
                if (!store.links.ownsEndpoint(endpoint, gattOwners)) return@post
                abandoned.forEach { onWriteFailure(endpoint, it) }
                onSocketLost(endpoint)
            }
        }
        val writer = BoundedPayloadWriter(
            isOwned = ::isOwned,
            write = { payload ->
                val output = DataOutputStream(socket.outputStream)
                output.writeInt(payload.size)
                var offset = 0
                while (offset < payload.size) {
                    check(isOwned())
                    val count = minOf(IO_CHUNK_BYTES, payload.size - offset)
                    output.write(payload, offset, count)
                    offset += count
                    if (isOwned()) store.l2capOutboundProgressTimes[endpoint] = System.currentTimeMillis()
                }
                output.flush()
                AppLogger.d("BLE_MESH", "L2CAP Sent ${payload.size} bytes directly to $endpoint")
            },
            onFailure = { abandoned ->
                AppLogger.d("BLE_MESH", "L2CAP writer failed on $endpoint; ${abandoned.size} transfers need GATT fallback")
                lost(abandoned)
            },
            onCapacityAvailable = { handler.post { if (isOwned()) onPromoteGattWork(endpoint) } }
        )
        writers[endpoint] = writer
        // Socket replacement retains accepted complete frames. A partly written old frame is
        // resent in full; existing message-ID dedupe handles an ambiguous successful old write.
        inherited.forEach { payload ->
            if (!writer.offer(payload, priority = true)) {
                handler.post { if (isOwned()) onWriteFailure(endpoint, payload) }
            }
        }
        AppLogger.updateLinkTransport(endpoint, "GATT+L2CAP")
        handler.post { onPromoteGattWork(endpoint) }
        onConnected()
        Thread {
            try {
                val input = DataInputStream(socket.inputStream)
                while (isOwned()) {
                    val length = input.readInt()
                    if (!MeshFrameCodec.isValidPayloadLength(length)) {
                        AppLogger.d("BLE_MESH", "Rejected invalid L2CAP payload length $length from $endpoint")
                        break
                    }
                    val payload = ByteArray(length)
                    var offset = 0
                    while (offset < length) {
                        val count = input.read(payload, offset, minOf(IO_CHUNK_BYTES, length - offset))
                        if (count < 0) throw java.io.EOFException()
                        if (!isOwned()) return@Thread
                        offset += count
                        store.connectionInteractionTimes[endpoint] = System.currentTimeMillis()
                    }
                    AppLogger.d("BLE_MESH", "L2CAP Received $length bytes from $endpoint")
                    if (isOwned()) {
                        onPayload(endpoint, payload)
                    }
                }
            } catch (error: Exception) {
                AppLogger.d("BLE_MESH", "L2CAP stream disconnected for $endpoint: ${error.message}")
            } finally {
                lost(writer.close())
                close(socket)
            }
        }.start()
    }

    /** Queue-full is distinct from socket-unavailable: pressure must not open a parallel GATT lane. */
    @Synchronized fun send(endpoint: String, payload: ByteArray, priority: Boolean = false): TransportDispatchResult {
        if (!MeshFrameCodec.isValidPayloadLength(payload.size) ||
            !OutboundQueuePolicy.fitsSingleTransfer(payload.size + Int.SIZE_BYTES, priority)) return TransportDispatchResult.REJECTED_INVALID_FRAME
        val socket = store.activeL2capSockets[endpoint]
        if (socket == null || !socket.isConnected || !hasLiveGattRole(endpoint)) return TransportDispatchResult.REJECTED_NOT_READY
        val writer = writers[endpoint] ?: return TransportDispatchResult.REJECTED_NOT_READY
        return if (writer.offer(payload, priority)) TransportDispatchResult.ACCEPTED else {
            AppLogger.d("BLE_MESH", "L2CAP queue full for $endpoint; rejected ${payload.size} bytes")
            TransportDispatchResult.REJECTED_QUEUE_FULL
        }
    }

    @Synchronized fun disconnect(endpoint: String) {
        writers.remove(endpoint)?.close()
        store.activeL2capSockets.remove(endpoint)?.let(::close)
        store.l2capOutboundProgressTimes.remove(endpoint)
    }

    fun stop() { (writers.keys + store.activeL2capSockets.keys).toSet().forEach(::disconnect) }

    private fun close(socket: BluetoothSocket) {
        try { socket.close() } catch (_: Exception) {}
    }

    private companion object { const val IO_CHUNK_BYTES = 16 * 1024 }
}
