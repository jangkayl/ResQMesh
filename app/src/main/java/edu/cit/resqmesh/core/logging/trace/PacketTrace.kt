package edu.cit.resqmesh.core.logging.trace

/**
 * Metadata-only packet event. It never holds text, ciphertext, keys, audio, coordinates or names;
 * IDs are shortened by [shortId]. Null route fields mean "not known at this emit point".
 */
data class PacketTrace(
    val seq: Long,
    val epochMs: Long,
    val stage: TraceStage,
    val dir: TraceDir,
    val scope: TraceScope,
    val type: TraceType,
    val packetKey: String,
    val sourceId: String?,
    val destId: String?,
    val nextHop: String?,
    val hop: Int?,
    val ttl: Int?,
    val pathLen: Int?,
    val bytes: Int,
    val transport: String?,
    val dropReason: TraceDropReason = TraceDropReason.NONE,
    /** Index of this phone (inbound) or the next hop (outbound) in a private directed route; null when unknown. */
    val routeStep: Int? = null,
    /** Piece progress of a transfer (acknowledged or held of total pieces); null for other events. */
    val progress: Int? = null,
    val total: Int? = null
)

/** Log line kept raw; timestamp formatting and MAC redaction happen when a snapshot is read. */
data class LogTrace(
    val seq: Long,
    val epochMs: Long,
    val level: TraceLevel,
    val categoryOrdinal: Int,
    val event: String,
    val message: String,
    val tag: String,
    val peerName: String?,
    val endpoint: String?,
    val linkGeneration: Long?,
    val role: String?,
    val isVerbose: Boolean
)

fun shortId(id: String?): String? = id?.takeIf { it.isNotBlank() }?.take(SHORT_ID_LENGTH)

private const val SHORT_ID_LENGTH = 8
