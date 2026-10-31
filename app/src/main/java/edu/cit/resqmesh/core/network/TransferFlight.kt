package edu.cit.resqmesh.core.network

/** Sender-side progress of one retained outbound transfer. Owned by the transfer worker. */
internal class TransferFlight(
    val record: TransferRecord,
    val bytes: ByteArray,
    var queuedAt: Long = 0,
    var queried: Boolean = false
) {
    val acknowledged: MutableSet<Int> = mutableSetOf()
    val pending: MutableMap<Int, Long> = mutableMapOf()
    val attempts: MutableMap<Int, Int> = mutableMapOf()
    val pendingWireIds: MutableMap<Int, String> = mutableMapOf()
    var cursor: Int = 0
    var startedAt: Long = 0
    var waitingStatus: Boolean = false
    var queryUntil: Long = 0
    var queryNonce: String = ""
    var queryWireId: String = ""

    val key get() = "${record.peer}:${record.id}"
    val pieceCount get() = TransferJournal.pieceCount(record.size)

    /** Forgets in-flight wire state so the peer's custody status is asked for again. */
    fun resetProbe() {
        pending.clear(); pendingWireIds.clear(); queried = false; waitingStatus = false
        queryNonce = ""; queryWireId = ""
    }

    fun clearProgress() {
        acknowledged.clear(); pending.clear(); pendingWireIds.clear()
    }

    /** Durable relays only expire; other flights also give up once they stall. */
    fun isExpired(at: Long, queuedStallMs: Long, startedStallMs: Long) = record.expiresAt <= at ||
        (!record.durableRelay && startedAt == 0L && at - queuedAt >= queuedStallMs) ||
        (!record.durableRelay && startedAt > 0 && at - startedAt >= startedStallMs)

    /** The next unsent piece, or the last piece again to draw a completing ACK once all are acknowledged. */
    fun nextPieceIndex(): Int? {
        val count = pieceCount
        return (0 until count).map { (cursor + it) % count }
            .firstOrNull { it !in acknowledged && it !in pending }
            ?: (count - 1).takeIf { acknowledged.size == count && pending.isEmpty() }
    }

    fun pieceBytes(index: Int): ByteArray = bytes.copyOfRange(index * TransferJournal.PIECE_BYTES,
        minOf((index + 1) * TransferJournal.PIECE_BYTES, record.size))

    fun retryDelay(index: Int): Long =
        (ReliableMeshTransfers.ACK_WAIT_MS shl ((attempts[index] ?: 1) - 1).coerceIn(0, MAX_BACKOFF_DOUBLINGS)) +
            ((record.id.hashCode().toLong() + index).and(RETRY_JITTER_MASK))

    private companion object {
        const val MAX_BACKOFF_DOUBLINGS = 3
        const val RETRY_JITTER_MASK = 255L
    }
}
