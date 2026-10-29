package edu.cit.resqmesh.core.network.ratelimit

/** Traffic classes limited per origin node. [OTHER] is never limited. */
enum class TrafficClass { CHAT, SOS_NEW, SOS_UPDATE, SYNC_REQUEST, PRESENCE, OTHER }

private const val LINK_BYTES_PER_SEC_DEFAULT = 262_144.0 // 256 KiB

/** Rates are tokens per minute; bursts are bucket capacities. Defaults follow docs/plans/security-hardening.md. */
data class RateLimitPolicy(
    val chatPerMin: Double = 20.0,
    // Receiver burst is 5 above the sender budget (chatSendBurst) so BLE queue bunching never trips it.
    val chatBurst: Double = 25.0,
    // Sender-side pacing for outgoing chat (OutgoingChatBudget): the same 20/min refill, burst 20.
    val chatSendPerMin: Double = 20.0,
    val chatSendBurst: Double = 20.0,
    // Receiver burst is the sender's 5 raises (OutgoingSosBudget) plus 1 margin for BLE queue bunching.
    val sosNewPerMin: Double = 5.0,
    val sosNewBurst: Double = 6.0,
    // Sender-side pacing for raising a new SOS: 5 back to back, then one every 12 s.
    val sosSendPerMin: Double = 5.0,
    val sosSendBurst: Double = 5.0,
    // Not fixed by the plan. Each raise costs up to 2 updates: its refine, plus its revision 1 when that arrives
    // after the refine (then the sosId is known, so rev 1 is charged as an update). 5 raises/min can therefore
    // charge 10 updates/min; burst 12 adds a 2-event margin. Ends of a known SOS are exempt (SosRepository.ingest).
    val sosUpdatePerMin: Double = 10.0,
    val sosUpdateBurst: Double = 12.0,
    val syncRequestPerMin: Double = 6.0,
    val syncRequestBurst: Double = 3.0,
    val presencePerMin: Double = 30.0,
    val presenceBurst: Double = 10.0,
    val violationsBeforeCooldown: Int = 3,
    val violationWindowMs: Long = 60_000L,
    val cooldownMs: Long = 60_000L,
    val linkFramesPerSec: Double = 64.0,
    val linkBytesPerSec: Double = LINK_BYTES_PER_SEC_DEFAULT,
    val maxTrackedOrigins: Int = 512
) {
    /** (capacity, refill per ms) for [trafficClass], or null when unlimited. */
    internal fun bucketSpec(trafficClass: TrafficClass): Pair<Double, Double>? = when (trafficClass) {
        TrafficClass.CHAT -> chatBurst to chatPerMin / MS_PER_MIN
        TrafficClass.SOS_NEW -> sosNewBurst to sosNewPerMin / MS_PER_MIN
        TrafficClass.SOS_UPDATE -> sosUpdateBurst to sosUpdatePerMin / MS_PER_MIN
        TrafficClass.SYNC_REQUEST -> syncRequestBurst to syncRequestPerMin / MS_PER_MIN
        TrafficClass.PRESENCE -> presenceBurst to presencePerMin / MS_PER_MIN
        TrafficClass.OTHER -> null
    }

    private companion object {
        const val MS_PER_MIN = 60_000.0
    }
}
