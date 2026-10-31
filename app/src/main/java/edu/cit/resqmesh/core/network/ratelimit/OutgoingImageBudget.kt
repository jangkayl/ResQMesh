package edu.cit.resqmesh.core.network.ratelimit

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.ceil
import kotlin.math.floor

/** What the composer shows: whole credits left, bucket size, and the time to the next credit (0 when full). */
data class ImageBudgetState(val remaining: Int, val capacity: Int, val nextRefillInMs: Long, val msPerCredit: Long) {
    /** Milliseconds until [credits] are available; 0 when they already are. */
    fun waitMsFor(credits: Int): Long =
        if (remaining >= credits) 0L else nextRefillInMs + (credits - remaining - 1) * msPerCredit
}

/**
 * Sender-side image pacing, on top of the chat budget each send also spends. Credits follow image size, so
 * worst-case airtime is the same at every quality level. In memory only: a process restart starts it full.
 */
class OutgoingImageBudget(
    policy: RateLimitPolicy = RateLimitPolicy(),
    clock: () -> Long = System::currentTimeMillis
) {
    private val capacity = policy.imageSendBurst
    private val refillPerMs = policy.imageSendPerMin / MS_PER_MIN
    private val msPerCredit = (MS_PER_MIN / policy.imageSendPerMin).toLong()
    private val bucket = TokenBucket(capacity, refillPerMs, clock)
    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<ImageBudgetState> = _state.asStateFlow()

    fun canAfford(credits: Int): Boolean = credits <= 0 || bucket.available() >= credits - EPSILON

    /** Takes [credits]; false when too few are left. The epsilon absorbs float rounding of the refill. */
    fun tryConsume(credits: Int): Boolean =
        (credits <= 0 || bucket.tryConsume(credits - EPSILON)).also { refresh() }

    /** Returns credits taken for a send that was refused before leaving the device. */
    fun refund(credits: Int) {
        if (credits > 0) bucket.refund(credits.toDouble())
        refresh()
    }

    /** Recomputes [state] from the clock; call once a second while the budget is not full. */
    fun refresh() {
        _state.value = snapshot()
    }

    private fun snapshot(): ImageBudgetState {
        val tokens = bucket.available()
        val whole = floor(tokens + EPSILON).toInt().coerceAtMost(capacity.toInt())
        val full = whole >= capacity.toInt()
        val untilNext = ceil((whole + 1 - tokens) / refillPerMs - EPSILON).toLong().coerceAtLeast(1L)
        return ImageBudgetState(whole, capacity.toInt(), if (full) 0L else untilNext, msPerCredit)
    }

    private companion object {
        const val MS_PER_MIN = 60_000.0
        const val EPSILON = 1e-6
    }
}
