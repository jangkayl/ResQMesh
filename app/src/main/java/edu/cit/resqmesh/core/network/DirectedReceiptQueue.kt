package edu.cit.resqmesh.core.network

/** One bounded, coalescing retry lane. First attempts and READY wakes have no batching delay. */
internal class DirectedReceiptQueue(
    private val send: (MeshPayload) -> TransportDispatchResult,
    private val schedule: (Long, () -> Unit) -> Unit,
    private val now: () -> Long,
    private val log: (String) -> Unit = {}
) {
    private data class Entry(val payload: MeshPayload, val expires: Long)
    private val pending = linkedMapOf<String, Entry>()
    private var timer = false
    private var epoch = 0L
    val size: Int get() = pending.size

    fun offer(payload: MeshPayload) {
        val key = "${payload.type}:${payload.targetMessageId}:${payload.reader}:${payload.directedRouteNodeIds}"
        if (!pending.containsKey(key) && pending.size >= MAX_PENDING_RECEIPTS) {
            log("PRIVATE_RECEIPT_RETRY capacity reached; duplicate replay remains available")
            return
        }
        pending[key] = Entry(payload, pending[key]?.expires ?: (now() + RECEIPT_RETENTION_MS))
        attempt(key)
        arm()
    }
    fun wake() { pending.keys.toList().forEach(::attempt); arm() }
    private fun attempt(key: String) {
        val entry = pending[key] ?: return
        if (now() >= entry.expires) { pending.remove(key); return }
        val result = send(entry.payload)
        log("PRIVATE_RECEIPT message=${entry.payload.targetMessageId} result=$result")
        if (result.accepted || result == TransportDispatchResult.REJECTED_INVALID_FRAME) pending.remove(key)
    }
    private fun arm() {
        if (timer || pending.isEmpty()) return
        timer = true
        val captured = epoch
        schedule(RETRY_INTERVAL_MS) {
            if (captured != epoch) return@schedule
            timer = false
            wake()
        }
    }
    fun clear() { pending.clear(); timer = false; epoch++ }
}

private const val MAX_PENDING_RECEIPTS = 128
private const val RECEIPT_RETENTION_MS = 24 * 60 * 60 * 1000L
private const val RETRY_INTERVAL_MS = 5_000L
