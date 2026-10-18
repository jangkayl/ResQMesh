package edu.cit.resqmesh.core.network.ratelimit

/** Thread-safe token bucket that starts full. [clock] returns milliseconds and is injected for tests. */
class TokenBucket(
    private val capacity: Double,
    private val refillPerMs: Double,
    private val clock: () -> Long
) {
    init {
        require(capacity > 0.0 && refillPerMs >= 0.0) { "invalid bucket parameters" }
    }

    private var tokens = capacity
    private var lastRefill = clock()

    @Synchronized
    fun tryConsume(tokens: Double = 1.0): Boolean {
        refill()
        if (this.tokens < tokens) return false
        this.tokens -= tokens
        return true
    }

    /** Tokens currently available, without consuming. */
    @Synchronized
    fun available(): Double {
        refill()
        return tokens
    }

    /** Charges [amount] even when that overdraws the bucket; the balance then refills back past zero. */
    @Synchronized
    fun consumeWithDebt(amount: Double) {
        refill()
        tokens -= amount
    }

    /** Milliseconds until a negative balance is repaid (0 when not in debt). */
    @Synchronized
    fun debtMs(): Long {
        refill()
        return if (tokens >= 0.0 || refillPerMs <= 0.0) 0L else Math.ceil(-tokens / refillPerMs).toLong()
    }

    private fun refill() {
        val now = clock()
        val elapsed = now - lastRefill
        if (elapsed > 0) tokens = minOf(capacity, tokens + elapsed * refillPerMs)
        lastRefill = maxOf(lastRefill, now)
    }
}
