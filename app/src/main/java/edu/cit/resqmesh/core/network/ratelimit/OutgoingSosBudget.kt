package edu.cit.resqmesh.core.network.ratelimit

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.ceil
import kotlin.math.floor

/** What the SOS button shows: whole raises left, bucket size, and ms until the next one (0 when full). */
data class SosBudgetState(val remaining: Int, val capacity: Int, val nextRefillInMs: Long)

/**
 * Sender-side pacing for raising a NEW SOS: five back to back, then one every 12 s. It follows the receiver's
 * per-origin SOS_NEW limit (see [RateLimitPolicy], burst 6) so a quick re-raise is paced visibly instead of
 * being dropped by remote phones. Ending, refining and reopening the active SOS never use it.
 * In memory only: a process restart starts it full, which the receiver margin absorbs.
 */
class OutgoingSosBudget(
    policy: RateLimitPolicy = RateLimitPolicy(),
    clock: () -> Long = System::currentTimeMillis
) {
    private val capacity = policy.sosSendBurst
    private val refillPerMs = policy.sosSendPerMin / MS_PER_MIN
    private val bucket = TokenBucket(capacity, refillPerMs, clock)
    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<SosBudgetState> = _state.asStateFlow()

    /** Takes one token; false when empty. The epsilon absorbs float rounding of the refill. */
    fun tryConsume(): Boolean = bucket.tryConsume(1.0 - EPSILON).also { refresh() }

    /** Returns a token taken for a raise that failed before it left the device. */
    fun refund() {
        bucket.refund(1.0)
        refresh()
    }

    /** Recomputes [state] from the clock; call once a second while the budget is not full. */
    fun refresh() {
        _state.value = snapshot()
    }

    private fun snapshot(): SosBudgetState {
        val tokens = bucket.available()
        val whole = floor(tokens + EPSILON).toInt().coerceAtMost(capacity.toInt())
        val full = whole >= capacity.toInt()
        val untilNext = ceil((whole + 1 - tokens) / refillPerMs - EPSILON).toLong().coerceAtLeast(1L)
        val next = if (full || refillPerMs <= 0.0) 0L else untilNext
        return SosBudgetState(whole, capacity.toInt(), next)
    }

    private companion object {
        const val MS_PER_MIN = 60_000.0
        const val EPSILON = 1e-6
    }
}

/** Whole seconds until the next token, rounded up (at least 1); meaningful only while the budget is not full. */
fun SosBudgetState.waitSeconds(): Int =
    ((nextRefillInMs + MS_PER_SECOND - 1) / MS_PER_SECOND).toInt().coerceAtLeast(1)

private const val MS_PER_SECOND = 1000L
