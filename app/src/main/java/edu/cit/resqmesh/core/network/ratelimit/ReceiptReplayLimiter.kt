package edu.cit.resqmesh.core.network.ratelimit

import edu.cit.resqmesh.core.logging.AppLogger
import edu.cit.resqmesh.core.utils.collections.BoundedLruMap

/**
 * Caps how often a receipt is re-sent for a duplicate inbound message. The first receipt for a
 * message is never limited; a flood of duplicates from one origin cannot make us emit a receipt each time.
 */
class ReceiptReplayLimiter(
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
    private val clock: () -> Long = System::currentTimeMillis,
    maxKeys: Int = MAX_KEYS,
    private val log: (String) -> Unit = { AppLogger.d("RECEIPT", it) }
) {
    private val lastSent = BoundedLruMap<String, Long>(maxKeys)

    /** Records and allows the receipt, or returns false when one for this (origin, message) went out recently. */
    fun allow(origin: String, messageId: String): Boolean = synchronized(lastSent) {
        val key = "$origin|$messageId"
        val now = clock()
        val previous = lastSent[key]
        if (previous != null && now - previous < intervalMs) {
            log("RECEIPT_THROTTLED message=$messageId")
            return false
        }
        lastSent.put(key, now)
        true
    }

    companion object {
        // Below the 5 s outbox retry so a genuine retry after a lost receipt is still answered.
        const val DEFAULT_INTERVAL_MS = 4_000L
        const val MAX_KEYS = 1024
    }
}
