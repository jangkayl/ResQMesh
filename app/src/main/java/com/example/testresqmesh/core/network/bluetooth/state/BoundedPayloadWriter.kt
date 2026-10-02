package com.example.testresqmesh.core.network.bluetooth.state

private fun startSocketWriter(action: () -> Unit) { Thread(action, "ResQMesh-L2CAP-writer").start() }
private fun ignoreCapacityAvailable() {}

/** One worker owns a socket's output. Queued control may overtake whole ordinary frames. */
class BoundedPayloadWriter(
    private val isOwned: () -> Boolean,
    private val write: (ByteArray) -> Unit,
    private val onFailure: (List<ByteArray>) -> Unit,
    startWorker: (() -> Unit) -> Unit = ::startSocketWriter,
    private val onCapacityAvailable: () -> Unit = ::ignoreCapacityAvailable
) {
    private val monitor = Object()
    private val ordinary = ArrayDeque<ByteArray>()
    private val control = ArrayDeque<ByteArray>()
    private var retainedCount = 0
    private var retainedBytes = 0L
    private var closed = false
    private var active: ByteArray? = null
    fun isIdle(): Boolean = synchronized(monitor) { !closed && active == null && retainedCount == 0 }

    init { startWorker { runWriter() } }

    fun offer(payload: ByteArray, priority: Boolean = false): Boolean = synchronized(monitor) {
        val frameBytes = payload.size + Int.SIZE_BYTES
        if (payload.isEmpty() || closed || !isOwned() || !OutboundQueuePolicy.canAccept(retainedCount, retainedBytes, frameBytes, priority)) {
            return@synchronized false
        }
        (if (priority) control else ordinary).addLast(payload)
        retainedCount++
        retainedBytes += frameBytes
        monitor.notifyAll()
        true
    }

    fun close(): List<ByteArray> = synchronized(monitor) {
        if (closed) return@synchronized emptyList()
        closed = true
        val abandoned = listOfNotNull(active) + control + ordinary
        active = null
        ordinary.clear()
        control.clear()
        monitor.notifyAll()
        abandoned
    }

    private fun runWriter() {
        while (true) {
            val payload = synchronized(monitor) {
                while (!closed && control.isEmpty() && ordinary.isEmpty()) monitor.wait()
                if (closed) return
                (if (control.isNotEmpty()) control else ordinary).removeFirst().also { active = it }
            }
            if (!isOwned()) { close(); return }
            try {
                write(payload)
                synchronized(monitor) { active = null; retainedCount--; retainedBytes -= payload.size + Int.SIZE_BYTES }
                onCapacityAvailable()
            } catch (_: Exception) {
                val abandoned = synchronized(monitor) {
                    if (closed) return
                    closed = true
                    active = null
                    (listOf(payload) + control + ordinary).also {
                        control.clear()
                        ordinary.clear()
                    }
                }
                onFailure(abandoned)
                return
            }
        }
    }
}
