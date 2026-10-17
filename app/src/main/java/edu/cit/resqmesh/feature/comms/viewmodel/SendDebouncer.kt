package edu.cit.resqmesh.feature.comms.viewmodel

/**
 * Drops a repeated send for the same [key] inside [windowMs] so double taps do not send twice.
 * Keys are per send kind and conversation, so a text send never suppresses a media or voice send.
 * A key is only consumed by [tryAcquire]/[mark], i.e. after the caller's validation succeeded.
 */
class SendDebouncer(private val windowMs: Long, private val clock: () -> Long) {
    private val lastSendAt = HashMap<String, Long>()

    /** True while [key] was marked less than the window ago; does not consume the gate. */
    fun isBlocked(key: String): Boolean {
        val last = lastSendAt[key] ?: return false
        return clock() - last < windowMs
    }

    fun mark(key: String) {
        lastSendAt[key] = clock()
    }

    /** Marks [key] and returns true when open; returns false (without marking) when blocked. */
    fun tryAcquire(key: String): Boolean {
        if (isBlocked(key)) return false
        mark(key)
        return true
    }
}
