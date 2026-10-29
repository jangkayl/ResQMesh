package edu.cit.resqmesh.core.network.ratelimit

import edu.cit.resqmesh.core.utils.collections.BoundedLruMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.ceil

/** Waits the incident screens show: ms until the next report, and per incident ms until the next offer change. */
data class IncidentBudgetState(val reportWaitMs: Long = 0L, val offerWaitMs: Map<String, Long> = emptyMap()) {
    fun offerWaitMs(incidentId: String): Long = offerWaitMs[incidentId] ?: 0L
}

/**
 * Sender-side pacing for incident creation: three reports back to back, then one every 20 s, and five offer
 * changes per minute on each incident. It follows the receivers' live limits ([RateLimitPolicy] INCIDENT_NEW
 * and OFFER_CHANGE, each one token larger) so a busy reporter is paced visibly instead of being throttled
 * remotely. Withdrawals and lead decisions never use it. In memory only: a restart starts it full.
 */
class OutgoingIncidentBudget(
    private val policy: RateLimitPolicy = RateLimitPolicy(),
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val reportRefillPerMs = policy.incidentSendPerMin / MS_PER_MIN
    private val offerRefillPerMs = policy.offerSendPerMin / MS_PER_MIN
    private val reports = TokenBucket(policy.incidentSendBurst, reportRefillPerMs, clock)
    private val offers = BoundedLruMap<String, TokenBucket>(MAX_TRACKED_INCIDENTS)
    private val _state = MutableStateFlow(IncidentBudgetState())
    val state: StateFlow<IncidentBudgetState> = _state.asStateFlow()

    fun tryConsumeReport(): Boolean = reports.tryConsume(1.0 - EPSILON).also { refresh() }
    fun refundReport() { reports.refund(1.0); refresh() }

    fun tryConsumeOffer(incidentId: String): Boolean =
        offerBucket(incidentId).tryConsume(1.0 - EPSILON).also { refresh() }
    fun refundOffer(incidentId: String) { offerBucket(incidentId).refund(1.0); refresh() }

    /** True while any wait is pending; the view model ticks [refresh] once a second until it is false. */
    fun isWaiting(): Boolean = state.value.reportWaitMs > 0 || state.value.offerWaitMs.isNotEmpty()

    fun refresh() {
        val offerWaits = offers.keys().mapNotNull { id ->
            offers[id]?.let { waitMs(it, offerRefillPerMs) }?.takeIf { it > 0 }?.let { id to it }
        }.toMap()
        _state.value = IncidentBudgetState(waitMs(reports, reportRefillPerMs), offerWaits)
    }

    private fun offerBucket(incidentId: String): TokenBucket =
        offers.getOrPut(incidentId) { TokenBucket(policy.offerSendBurst, offerRefillPerMs, clock) }

    private fun waitMs(bucket: TokenBucket, refillPerMs: Double): Long {
        val tokens = bucket.available()
        if (tokens >= 1.0 - EPSILON || refillPerMs <= 0.0) return 0L
        return ceil((1.0 - tokens) / refillPerMs - EPSILON).toLong().coerceAtLeast(1L)
    }

    private companion object {
        const val MS_PER_MIN = 60_000.0
        const val EPSILON = 1e-6
        const val MAX_TRACKED_INCIDENTS = 128
    }
}

/** Whole seconds to show for a wait, rounded up; 0 when nothing is pending. */
fun waitSeconds(waitMs: Long): Int = if (waitMs <= 0) 0 else ((waitMs + MS_PER_SECOND - 1) / MS_PER_SECOND).toInt()

private const val MS_PER_SECOND = 1000L
