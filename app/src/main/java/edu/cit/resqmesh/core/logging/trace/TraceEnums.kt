package edu.cit.resqmesh.core.logging.trace

enum class TraceStream { DATA, CONTROL, ERRORS, LOGS, VOICE }

enum class TraceStage { QUEUED, STARTED, COMPLETED, FAILED, RECEIVED, DROPPED }

enum class TraceDir { OUT, IN }

enum class TraceScope { PUBLIC, PRIVATE, UNKNOWN }

enum class TraceDropReason { NONE, UNKNOWN }

enum class TraceLevel { DEBUG, INFO, WARN, ERROR }

/** Peer-supplied type strings map onto this closed set so they are never stored or formatted. */
enum class TraceType {
    MESSAGE, CONVERSATION, SYSTEM, PING, PONG, SEEN, DELIVERED, LIVE_AUDIO, PIECE, ACK,
    SOS_EVENT, SOS_SYNC_REQ, SOS_SYNC_RES, BLOCK_REQUEST, BLOCK_ACK, OTHER;

    /** Control-plane traffic goes to the CONTROL ring so heartbeats cannot evict message traffic. */
    val isControl: Boolean
        get() = when (this) {
            SYSTEM, PING, PONG, SEEN, DELIVERED, PIECE, ACK, BLOCK_REQUEST, BLOCK_ACK -> true
            else -> false
        }

    companion object {
        @Suppress("CyclomaticComplexMethod") // flat string-to-enum map
        fun of(raw: String?): TraceType = when (raw) {
            "MESSAGE" -> MESSAGE
            "CONVERSATION" -> CONVERSATION
            "SYSTEM" -> SYSTEM
            "PING" -> PING
            "PONG" -> PONG
            "SEEN" -> SEEN
            "DELIVERED" -> DELIVERED
            "LIVE_AUDIO" -> LIVE_AUDIO
            "PIECE" -> PIECE
            "ACK" -> ACK
            "SOS_EVENT" -> SOS_EVENT
            "SOS_SYNC_REQ" -> SOS_SYNC_REQ
            "SOS_SYNC_RES" -> SOS_SYNC_RES
            "BLOCK_REQUEST" -> BLOCK_REQUEST
            "BLOCK_ACK" -> BLOCK_ACK
            else -> OTHER
        }
    }
}
