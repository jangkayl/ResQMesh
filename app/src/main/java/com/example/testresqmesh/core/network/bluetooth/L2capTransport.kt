package com.example.testresqmesh.core.network.bluetooth

import android.bluetooth.BluetoothSocket
import android.os.Handler
import com.example.testresqmesh.core.network.bluetooth.state.BleStateStore
import com.example.testresqmesh.core.network.bluetooth.state.MeshFrameCodec
import com.example.testresqmesh.core.utils.AppLogger
import java.io.DataInputStream
import java.io.DataOutputStream

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
    fun attach(endpoint: String, socket: BluetoothSocket) {
        store.activeL2capSockets.put(endpoint, socket)?.let { previous ->
            if (previous !== socket) close(previous)
        }
        AppLogger.updateLinkTransport(endpoint, "GATT+L2CAP")
        handler.post { onPromoteGattWork(endpoint) }
        onConnected()
        Thread {
            try {
                val input = DataInputStream(socket.inputStream)
                while (store.isNodeActive.get() && socket.isConnected &&
                    store.activeL2capSockets[endpoint] === socket && hasLiveGattRole(endpoint)) {
                    val length = input.readInt()
                    if (!MeshFrameCodec.isValidPayloadLength(length)) {
                        AppLogger.d("BLE_MESH", "Rejected invalid L2CAP payload length $length from $endpoint")
                        break
                    }
                    val payload = ByteArray(length)
                    input.readFully(payload)
                    store.connectionInteractionTimes[endpoint] = System.currentTimeMillis()
                    AppLogger.d("BLE_MESH", "L2CAP Received $length bytes from $endpoint")
                    if (store.activeL2capSockets[endpoint] === socket && hasLiveGattRole(endpoint)) {
                        onPayload(endpoint, payload)
                    }
                }
            } catch (error: Exception) {
                AppLogger.d("BLE_MESH", "L2CAP stream disconnected for $endpoint: ${error.message}")
            } finally {
                if (store.activeL2capSockets.remove(endpoint, socket)) {
                    close(socket)
                    handler.post { onSocketLost(endpoint) }
                } else {
                    close(socket)
                }
            }
        }.start()
    }

    /** Returns true only when the payload was accepted for the current L2CAP socket. */
    fun send(endpoint: String, payload: ByteArray): Boolean {
        val socket = store.activeL2capSockets[endpoint]
        if (socket == null || !socket.isConnected || !hasLiveGattRole(endpoint)) return false
        Thread {
            try {
                synchronized(socket) {
                    val output = DataOutputStream(socket.outputStream)
                    output.writeInt(payload.size)
                    output.write(payload)
                    output.flush()
                }
                AppLogger.d("BLE_MESH", "L2CAP Sent ${payload.size} bytes directly to $endpoint")
            } catch (error: Exception) {
                AppLogger.d("BLE_MESH", "L2CAP write failed to $endpoint: ${error.message}")
                if (store.activeL2capSockets.remove(endpoint, socket)) {
                    close(socket)
                    handler.post {
                        onWriteFailure(endpoint, payload)
                        onSocketLost(endpoint)
                    }
                }
            }
        }.start()
        return true
    }

    private fun close(socket: BluetoothSocket) {
        try { socket.close() } catch (_: Exception) {}
    }
}
