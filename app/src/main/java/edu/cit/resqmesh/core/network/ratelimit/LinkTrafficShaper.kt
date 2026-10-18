package edu.cit.resqmesh.core.network.ratelimit

import edu.cit.resqmesh.core.utils.collections.BoundedLruMap

/**
 * Per-link traffic shaping. Only admits, rejects or delays; it never applies a cooldown.
 *
 * [admit] charges whole frames (frames/s and bytes/s) and rejects a frame larger than the one-second
 * byte budget. Raw transports shape chunks instead: [admitChunk] and [chargeStream] charge only the
 * byte budget and let it go into debt, so a chunk of any size (and a 2 MiB frame read in pieces) is
 * never refused outright; the debt just has to be repaid before the next chunk or read.
 */
class LinkTrafficShaper(
    private val policy: RateLimitPolicy = RateLimitPolicy(),
    private val clock: () -> Long = System::currentTimeMillis,
    maxLinks: Int = 128
) {
    private class Buckets(val frames: TokenBucket, val bytes: TokenBucket)

    private val links = BoundedLruMap<String, Buckets>(maxLinks)

    private fun state(linkKey: String): Buckets = links.getOrPut(linkKey) {
        Buckets(
            TokenBucket(policy.linkFramesPerSec, policy.linkFramesPerSec / MS_PER_SEC, clock),
            TokenBucket(policy.linkBytesPerSec, policy.linkBytesPerSec / MS_PER_SEC, clock)
        )
    }

    fun admit(linkKey: String, bytes: Int): Boolean = synchronized(links) {
        val state = state(linkKey)
        // Check both before consuming so a rejected frame does not drain the other bucket.
        if (state.frames.available() < 1.0 || state.bytes.available() < bytes) return false
        state.frames.tryConsume()
        state.bytes.tryConsume(bytes.toDouble())
    }

    /** Charges one decoded frame to the frames/s budget only (bytes are charged per chunk or read). */
    fun admitFrame(linkKey: String): Boolean = synchronized(links) { state(linkKey).frames.tryConsume() }

    /** Charges a raw chunk to the byte budget. False while the link is in debt (chunk not charged). */
    fun admitChunk(linkKey: String, bytes: Int): Boolean = synchronized(links) {
        val budget = state(linkKey).bytes
        if (budget.available() <= 0.0) return false
        budget.consumeWithDebt(bytes.toDouble())
        true
    }

    /** Charges a raw stream read and returns the milliseconds the reader should wait; never rejects. */
    fun chargeStream(linkKey: String, bytes: Int): Long = synchronized(links) {
        val budget = state(linkKey).bytes
        budget.consumeWithDebt(bytes.toDouble())
        budget.debtMs()
    }

    fun forget(linkKey: String) {
        synchronized(links) { links.remove(linkKey) }
    }

    private companion object {
        const val MS_PER_SEC = 1000.0
    }
}
