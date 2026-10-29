package edu.cit.resqmesh.core.network.ratelimit

import edu.cit.resqmesh.core.logging.AppLogger
import edu.cit.resqmesh.core.utils.collections.BoundedLruMap
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs at most one expensive handler per key at a time. While a run is in flight up to [maxQueued]
 * further requests wait for their turn (a coalesced rerun); anything beyond that is dropped and the
 * peer's own retry or the periodic anti-entropy pass recovers it.
 */
class SingleFlightGate(
    private val name: String,
    private val maxQueued: Int = 1,
    maxKeys: Int = 128,
    clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = { AppLogger.d("SINGLE_FLIGHT", it) }
) {
    private class Slot {
        val mutex = Mutex()
        val queued = AtomicInteger()
    }

    private val slots = BoundedLruMap<String, Slot>(maxKeys)
    private val dropLog = RateLimitedLog(DROP_LOG_INTERVAL_MS, clock)

    /** Returns true when [block] ran (possibly after waiting) and false when the request was dropped. */
    suspend fun run(key: String, block: suspend () -> Unit): Boolean {
        val slot = slots.getOrPut(key) { Slot() }
        if (!slot.mutex.tryLock()) {
            if (slot.queued.incrementAndGet() > maxQueued) {
                slot.queued.decrementAndGet()
                if (dropLog.shouldLog("$name/$key")) {
                    log("SYNC_COALESCED handler=$name key=${key.take(MAX_KEY_LOG)} action=drop")
                }
                return false
            }
            try {
                slot.mutex.lock()
            } finally {
                slot.queued.decrementAndGet()
            }
        }
        try {
            block()
        } finally {
            slot.mutex.unlock()
        }
        return true
    }

    private companion object {
        const val DROP_LOG_INTERVAL_MS = 5_000L
        const val MAX_KEY_LOG = 40
    }
}
