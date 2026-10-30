package edu.cit.resqmesh.core.network.bluetooth

import android.bluetooth.BluetoothSocket
import android.os.Handler
import edu.cit.resqmesh.core.network.TransportDispatchResult
import edu.cit.resqmesh.core.network.OutboundFrameEvent
import edu.cit.resqmesh.core.network.bluetooth.state.BleLinkRole
import edu.cit.resqmesh.core.network.bluetooth.state.BleLink
import edu.cit.resqmesh.core.network.bluetooth.state.BoundedPayloadWriter
import edu.cit.resqmesh.core.network.bluetooth.state.BleStateStore
import edu.cit.resqmesh.core.network.bluetooth.state.MeshFrameCodec
import edu.cit.resqmesh.core.network.bluetooth.state.OutboundQueuePolicy
import edu.cit.resqmesh.core.logging.AppLogger
import java.io.DataOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Owns one endpoint's L2CAP socket lifecycle without deciding GATT admission or payload policy. */
class L2capTransport(
    private val store: BleStateStore,
    private val handler: Handler,
    private val hasLiveGattRole: (String) -> Boolean,
    private val onPayload: (String, ByteArray, String) -> Unit,
    private val onPromoteGattWork: (String) -> Unit,
    private val onSocketLost: (String) -> Unit,
    private val onWriteFailure: (String, ByteArray) -> Unit,
    private val onConnected: () -> Unit,
    private val onFrame: (String, ByteArray, OutboundFrameEvent.Stage) -> Unit = { _, _, _ -> },
    private val priorityForPayload: (ByteArray) -> Boolean = { false }
) {
    private val writers = ConcurrentHashMap<String, BoundedPayloadWriter>()
    private val inboundFrames = ConcurrentHashMap<String, AtomicInteger>()
    private val socketOwners = ConcurrentHashMap<String, List<BleLink>>()
    fun isUsable(endpoint: String): Boolean = store.activeL2capSockets[endpoint]?.isConnected == true &&
        store.links.ownsSocket(endpoint, socketOwners[endpoint].orEmpty())
    /** Read-only: true while a payload writer exists for this endpoint's L2CAP socket. */
    fun hasWriter(endpoint: String): Boolean = writers.containsKey(endpoint)
    fun isIdle(endpoint: String): Boolean = writers[endpoint]?.isIdle() != false &&
        (inboundFrames[endpoint]?.get() ?: 0) == 0

    @Synchronized fun attach(endpoint: String, socket: BluetoothSocket) {
        val existing = store.activeL2capSockets[endpoint]
        if (existing === socket) return
        val inherited = writers[endpoint]?.close().orEmpty()
        disconnect(endpoint)
        store.activeL2capSockets[endpoint] = socket
        val incoming = AtomicInteger()
        inboundFrames[endpoint] = incoming
        val gattOwners = BleLinkRole.entries.mapNotNull { store.links.current(endpoint, it) }
        socketOwners[endpoint] = gattOwners
        fun isOwned() = store.isNodeActive.get() && store.activeL2capSockets[endpoint] === socket &&
            hasLiveGattRole(endpoint) && store.links.ownsSocket(endpoint, gattOwners)
        fun lost(abandoned: List<ByteArray>) {
            synchronized(this@L2capTransport) {
                if (!store.activeL2capSockets.remove(endpoint, socket)) return
                writers.remove(endpoint)?.close()
                store.l2capOutboundProgressTimes.remove(endpoint)
                inboundFrames.remove(endpoint, incoming)
                socketOwners.remove(endpoint, gattOwners)
                close(socket)
            }
            handler.post {
                if (!store.links.ownsSocket(endpoint, gattOwners)) {
                    abandoned.forEach { onFrame(endpoint, it, OutboundFrameEvent.Stage.FAILED) }
                    return@post
                }
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
                    handler.post { if (isOwned()) store.l2capOutboundProgressTimes[endpoint] = System.currentTimeMillis() }
                }
                output.flush()
                AppLogger.d("BLE_MESH", "L2CAP Sent ${payload.size} bytes directly to $endpoint")
            },
            onFailure = { abandoned ->
                AppLogger.d("BLE_MESH", "L2CAP writer failed on $endpoint; ${abandoned.size} transfers need GATT fallback")
                lost(abandoned)
            },
            onCapacityAvailable = { handler.post { if (isOwned()) onPromoteGattWork(endpoint) } },
            onStarted = { bytes -> handler.post { if (isOwned()) onFrame(endpoint, bytes, OutboundFrameEvent.Stage.STARTED) } },
            onCompleted = { bytes -> handler.post { if (isOwned()) onFrame(endpoint, bytes, OutboundFrameEvent.Stage.COMPLETED) } },
            onRetired = { abandoned -> handler.post { abandoned.forEach { onFrame(endpoint, it, OutboundFrameEvent.Stage.FAILED) } } }
        )
        writers[endpoint] = writer
        // Socket replacement retains accepted complete frames. A partly written old frame is
        // resent in full; existing message-ID dedupe handles an ambiguous successful old write.
        inherited.forEach { payload ->
            if (!writer.offer(payload, priority = priorityForPayload(payload))) {
                handler.post { if (isOwned()) onWriteFailure(endpoint, payload) }
            }
        }
        handler.post { if (isOwned()) onPromoteGattWork(endpoint) }
        onConnected()
        val gate = L2capInboundGate()
        val watchdog = L2capReadWatchdog()
        val readerDone = java.util.concurrent.atomic.AtomicBoolean(false)
        val stallCheck = object : Runnable {
            override fun run() {
                if (readerDone.get() || !isOwned()) return
                if (watchdog.isStalled(System.currentTimeMillis())) {
                    AppLogger.d(
                        "BLE_MESH",
                        "L2CAP_READ_TIMEOUT endpoint=$endpoint: no progress for ${L2capReadWatchdog.READ_TIMEOUT_MS} ms"
                    )
                    close(socket)
                    return
                }
                handler.postDelayed(this, L2capReadWatchdog.CHECK_INTERVAL_MS)
            }
        }
        handler.postDelayed(stallCheck, L2capReadWatchdog.CHECK_INTERVAL_MS)
        Thread {
            try {
                val reader = L2capFrameReader(
                    socket.inputStream,
                    onFrameStarted = { watchdog.frameStarted(System.currentTimeMillis()) },
                    onChunk = { count ->
                        val delay = store.guards.streamDelayMs("$endpoint/l2cap", count)
                        if (delay > 0) Thread.sleep(delay)
                    },
                    onProgress = {
                        val now = System.currentTimeMillis()
                        watchdog.progress(now)
                        handler.post { if (isOwned()) store.connectionInteractionTimes[endpoint] = now }
                    }
                )
                while (isOwned()) {
                    val header = reader.readHeader()
                    if (header is L2capFrameReader.Header.Invalid) {
                        AppLogger.d("BLE_MESH", "Rejected invalid L2CAP payload length ${header.value} from $endpoint")
                        return@Thread
                    }
                    val length = (header as L2capFrameReader.Header.Length).value
                    // Back-pressure: wait for handler space before reading the body, so no frame is dropped.
                    // Not mid-frame while waiting, so the read watchdog stays quiet.
                    watchdog.frameEnded()
                    val admission = gate.awaitAcquire(length, L2capInboundGate.STALL_TIMEOUT_MS, ::isOwned)
                    if (admission == L2capInboundGate.Admission.TIMED_OUT) {
                        AppLogger.d(
                            "BLE_MESH",
                            "L2CAP_QUEUE_STALL endpoint=$endpoint: queue full ${L2capInboundGate.STALL_TIMEOUT_MS} ms"
                        )
                        return@Thread
                    }
                    if (admission != L2capInboundGate.Admission.ADMITTED) return@Thread
                    watchdog.frameStarted(System.currentTimeMillis())
                    incoming.incrementAndGet()
                    val payload = try {
                        reader.readPayload(length, ::isOwned)
                    } catch (error: Exception) {
                        incoming.decrementAndGet()
                        gate.release(length)
                        throw error
                    }
                    watchdog.frameEnded()
                    if (payload == null || !isOwned()) {
                        incoming.decrementAndGet()
                        gate.release(length)
                        return@Thread
                    }
                    AppLogger.d("BLE_MESH", "L2CAP Received $length bytes from $endpoint")
                    handler.post {
                        try { if (isOwned()) onPayload(endpoint, payload, "L2CAP") }
                        finally {
                            incoming.decrementAndGet()
                            gate.release(length)
                        }
                    }
                }
            } catch (error: Exception) {
                AppLogger.d("BLE_MESH", "L2CAP stream disconnected for $endpoint: ${error.javaClass.simpleName}")
            } finally {
                gate.close()
                readerDone.set(true)
                handler.removeCallbacks(stallCheck)
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
        if (socket == null || !isUsable(endpoint) || !hasLiveGattRole(endpoint)) return TransportDispatchResult.REJECTED_NOT_READY
        val writer = writers[endpoint] ?: return TransportDispatchResult.REJECTED_NOT_READY
        return if (writer.offer(payload, priority)) TransportDispatchResult.ACCEPTED else {
            AppLogger.d("BLE_MESH", "L2CAP queue full for $endpoint; rejected ${payload.size} bytes")
            TransportDispatchResult.REJECTED_QUEUE_FULL
        }
    }

    @Synchronized fun disconnect(endpoint: String) {
        writers.remove(endpoint)?.close()?.forEach { bytes ->
            handler.post { onFrame(endpoint, bytes, OutboundFrameEvent.Stage.FAILED) }
        }
        store.activeL2capSockets.remove(endpoint)?.let(::close)
        store.l2capOutboundProgressTimes.remove(endpoint)
        inboundFrames.remove(endpoint)
        socketOwners.remove(endpoint)
    }

    fun stop() { (writers.keys + store.activeL2capSockets.keys).toSet().forEach(::disconnect) }

    private fun close(socket: BluetoothSocket) {
        try { socket.close() } catch (_: Exception) {}
    }

    private companion object { const val IO_CHUNK_BYTES = L2capFrameReader.IO_CHUNK_BYTES }
}
