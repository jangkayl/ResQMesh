package edu.cit.resqmesh.core.network.ratelimit

import edu.cit.resqmesh.core.utils.collections.BoundedLruMap

/**
 * Per-link frames/s and bytes/s shaping. Only admits or rejects; it never applies a cooldown.
 * A frame larger than the one-second byte budget can never be admitted.
 */
class LinkTrafficShaper(
    private val policy: RateLimitPolicy = RateLimitPolicy(),
    private val clock: () -> Long = System::currentTimeMillis,
    maxLinks: Int = 128
) {
    private class Buckets(val frames: TokenBucket, val bytes: TokenBucket)

    private val links = BoundedLruMap<String, Buckets>(maxLinks)

    fun admit(linkKey: String, bytes: Int): Boolean = synchronized(links) {
        val state = links.getOrPut(linkKey) {
            Buckets(
                TokenBucket(policy.linkFramesPerSec, policy.linkFramesPerSec / MS_PER_SEC, clock),
                TokenBucket(policy.linkBytesPerSec, policy.linkBytesPerSec / MS_PER_SEC, clock)
            )
        }
        // Check both before consuming so a rejected frame does not drain the other bucket.
        if (state.frames.available() < 1.0 || state.bytes.available() < bytes) return false
        state.frames.tryConsume()
        state.bytes.tryConsume(bytes.toDouble())
    }

    fun forget(linkKey: String) {
        synchronized(links) { links.remove(linkKey) }
    }

    private companion object {
        const val MS_PER_SEC = 1000.0
    }
}
