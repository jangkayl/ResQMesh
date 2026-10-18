package edu.cit.resqmesh.core.network.ratelimit

import edu.cit.resqmesh.core.utils.collections.BoundedLruMap

/**
 * Per-origin limiter keyed by the ORIGIN node id (never the relaying neighbour), so a relay carrying
 * traffic for many origins does not share buckets with them. Memory is bounded by LRU eviction.
 */
class OriginRateLimiter(
    private val policy: RateLimitPolicy = RateLimitPolicy(),
    private val clock: () -> Long = System::currentTimeMillis
) {
    enum class Decision { Allowed, Dropped, CoolingDown }

    private val buckets = BoundedLruMap<String, MutableMap<TrafficClass, TokenBucket>>(policy.maxTrackedOrigins)
    private val penalties = PenaltyBox(
        policy.violationsBeforeCooldown, policy.violationWindowMs, policy.cooldownMs, clock, policy.maxTrackedOrigins
    )

    fun check(originNodeId: String, trafficClass: TrafficClass): Decision {
        val spec = policy.bucketSpec(trafficClass)
        return when {
            spec == null -> Decision.Allowed
            penalties.isCoolingDown(originNodeId) -> Decision.CoolingDown
            tryConsume(originNodeId, trafficClass, spec) -> Decision.Allowed
            penalties.recordViolation(originNodeId) -> Decision.CoolingDown
            else -> Decision.Dropped
        }
    }

    private fun tryConsume(origin: String, trafficClass: TrafficClass, spec: Pair<Double, Double>): Boolean =
        synchronized(buckets) {
            buckets.getOrPut(origin) { mutableMapOf() }
                .getOrPut(trafficClass) { TokenBucket(spec.first, spec.second, clock) }
                .tryConsume()
        }

    fun forget(originNodeId: String) {
        synchronized(buckets) { buckets.remove(originNodeId) }
        penalties.forget(originNodeId)
    }
}
