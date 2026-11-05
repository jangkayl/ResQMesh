package edu.cit.resqmesh.core.network

/**
 * Values of [MeshPayload.type]. These strings travel on the wire and must never change;
 * `MeshPayload.type` stays a `String` so older phones keep decoding them.
 */
object PayloadTypes {
    const val MESSAGE = "MESSAGE"
    const val CONVERSATION = "CONVERSATION"
    const val SYSTEM = "SYSTEM"
    const val PING = "PING"
    const val PONG = "PONG"
    const val SEEN = "SEEN"
    const val DELIVERED = "DELIVERED"
    const val GOODBYE = "GOODBYE"
    const val BLOCK = "BLOCK"
    const val UNBLOCK = "UNBLOCK"
    const val LIVE_AUDIO = "LIVE_AUDIO"
    const val DOMAIN_EVENT = "DOMAIN_EVENT"
    const val EVENT_SYNC_REQ = "EVENT_SYNC_REQ"
    const val EVENT_SYNC_RESP = "EVENT_SYNC_RESP"
    const val SOS_EVENT = "SOS_EVENT"
    const val SOS_SYNC_REQ = "SOS_SYNC_REQ"
    const val SOS_SYNC_RES = "SOS_SYNC_RES"
    const val CHAT_SYNC_REQ = "CHAT_SYNC_REQ"
    const val CHAT_SYNC_RES = "CHAT_SYNC_RES"
}
