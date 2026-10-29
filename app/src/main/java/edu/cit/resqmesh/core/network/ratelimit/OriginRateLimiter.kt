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
    /** [CooldownStarted] is returned once, by the violation that starts a cooldown; later blocks are [CoolingDown]. */
    enum class Decision { Allowed, Dropped, CoolingDown, CooldownStarted }

    private val buckets = BoundedLruMap<String, MutableMap<TrafficClass, TokenBucket>>(policy.maxTrackedOrigins)
    private val penalties = PenaltyBox(
        policy.violationsBeforeCooldown, policy.violationWindowMs, policy.cooldownMs, clock, policy.maxTrackedOrigins
    )

    fun check(originNodeId: String, trafficClass: TrafficClass): Decision {
        val spec = policy.bucketSpec(trafficClass)
        if (spec == null) return Decision.Allowed
        // SOS and incident creation classes are only ever limited by their own buckets: a cooldown never
        // blocks them, and a dropped live event is recovered by catch-up sync once the bucket refills.
        val bucketOnly = trafficClass in BUCKET_ONLY
        return when {
            !bucketOnly && penalties.isCoolingDown(originNodeId, trafficClass) -> Decision.CoolingDown
            tryConsume(originNodeId, trafficClass, spec) -> Decision.Allowed
            trafficClass == TrafficClass.INCIDENT_NEW || trafficClass == TrafficClass.OFFER_CHANGE -> Decision.Dropped
            penalties.recordViolation(originNodeId, trafficClass) -> Decision.CooldownStarted
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

    private companion object {
        val BUCKET_ONLY = setOf(TrafficClass.SOS_NEW, TrafficClass.SOS_UPDATE,
            TrafficClass.INCIDENT_NEW, TrafficClass.OFFER_CHANGE)
    }
}
