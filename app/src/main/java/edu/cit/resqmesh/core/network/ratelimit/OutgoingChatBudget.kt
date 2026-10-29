package edu.cit.resqmesh.core.network.ratelimit

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.ceil
import kotlin.math.floor

/** What the composer shows: whole tokens left, bucket size, and ms until the next token (0 when full). */
data class ChatBudgetState(val remaining: Int, val capacity: Int, val nextRefillInMs: Long)

/**
 * Sender-side chat pacing shared by public and private chat. It follows the receiver's per-origin CHAT
 * limit (see [RateLimitPolicy]) so quick sends are paced and visible instead of silently dropped remotely.
 */
class OutgoingChatBudget(
    policy: RateLimitPolicy = RateLimitPolicy(),
    clock: () -> Long = System::currentTimeMillis
) {
    private val capacity = policy.chatSendBurst
    private val refillPerMs = policy.chatSendPerMin / MS_PER_MIN
    private val bucket = TokenBucket(capacity, refillPerMs, clock)
    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<ChatBudgetState> = _state.asStateFlow()

    /** Takes one token; false when empty. The epsilon absorbs float rounding of the refill. */
    fun tryConsume(): Boolean = bucket.tryConsume(1.0 - EPSILON).also { refresh() }

    /** Returns a token taken for a send that was refused before leaving the device. */
    fun refund() {
        bucket.refund(1.0)
        refresh()
    }

    /** Recomputes [state] from the clock; call once a second while the budget is not full. */
    fun refresh() {
        _state.value = snapshot()
    }

    private fun snapshot(): ChatBudgetState {
        val tokens = bucket.available()
        val whole = floor(tokens + EPSILON).toInt().coerceAtMost(capacity.toInt())
        val full = whole >= capacity.toInt()
        val untilNext = ceil((whole + 1 - tokens) / refillPerMs - EPSILON).toLong().coerceAtLeast(1L)
        val next = if (full || refillPerMs <= 0.0) 0L else untilNext
        return ChatBudgetState(whole, capacity.toInt(), next)
    }

    private companion object {
        const val MS_PER_MIN = 60_000.0
        const val EPSILON = 1e-6
    }
}
