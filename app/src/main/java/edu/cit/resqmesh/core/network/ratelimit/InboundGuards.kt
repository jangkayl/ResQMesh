package edu.cit.resqmesh.core.network.ratelimit

import edu.cit.resqmesh.core.logging.AppLogger
import edu.cit.resqmesh.core.logging.TerminalLogCategory
import edu.cit.resqmesh.core.logging.TerminalLogLevel

/** Receive-side traffic limits shared by the transports (link shaping) and the dispatcher (origin limits). */
class InboundGuards(
    policy: RateLimitPolicy = RateLimitPolicy(),
    clock: () -> Long = System::currentTimeMillis
) {
    val origins = InboundOriginGate(OriginRateLimiter(policy, clock), clock)
    private val shaper = LinkTrafficShaper(policy, clock)
    private val shapeLog = RateLimitedLog(SHAPE_LOG_INTERVAL_MS, clock)

    /** Charges a raw GATT chunk; false means drop it (and reset that link's reassembler). */
    fun admitChunk(linkKey: String, bytes: Int): Boolean {
        val admitted = shaper.admitChunk(linkKey, bytes)
        if (!admitted) logShape(linkKey, "drop")
        return admitted
    }

    /** Charges a raw stream read and returns how long the reader should pause; nothing is dropped. */
    fun streamDelayMs(linkKey: String, bytes: Int): Long {
        val delay = shaper.chargeStream(linkKey, bytes).coerceAtMost(MAX_STREAM_DELAY_MS)
        if (delay > 0) logShape(linkKey, "delay")
        return delay
    }

    private fun logShape(linkKey: String, action: String) {
        if (!shapeLog.shouldLog(linkKey)) return
        AppLogger.event(
            category = TerminalLogCategory.TRANSPORT,
            event = "LINK_SHAPE",
            message = "LINK_SHAPE action=$action link=$linkKey",
            level = TerminalLogLevel.WARN,
            tag = "BLE_MESH",
            endpoint = linkKey.substringBefore('/')
        )
    }

    private companion object {
        const val SHAPE_LOG_INTERVAL_MS = 5_000L
        const val MAX_STREAM_DELAY_MS = 1_000L
    }
}
