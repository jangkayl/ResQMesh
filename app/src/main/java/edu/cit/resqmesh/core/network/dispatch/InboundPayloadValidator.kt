package edu.cit.resqmesh.core.network.dispatch

import edu.cit.resqmesh.core.network.MeshPayload
import edu.cit.resqmesh.core.utils.BinaryCompressor

/**
 * Structural checks on a decoded [MeshPayload] before dedup, rate limiting or any handler runs.
 * Caps are deliberately generous: they bound memory and CPU for hostile peers without rejecting
 * anything this app sends. [MeshPayload] has no priority field, so there is nothing to clamp there.
 */
class InboundPayloadValidator {
    sealed interface Result {
        /** [payload] has its TTL and hop count clamped; it is the same instance when nothing changed. */
        data class Accepted(val payload: MeshPayload) : Result
        data class Rejected(val reason: String) : Result
    }

    fun validate(payload: MeshPayload): Result {
        rejection(payload)?.let { return Result.Rejected(it) }
        // ttl 0 is the legacy "no TTL" marker that relay handlers treat specially, so it is kept.
        val ttl = payload.ttl.coerceIn(0, MAX_TTL)
        val hops = payload.relayHopCount.coerceIn(0, MAX_TTL)
        return Result.Accepted(
            if (ttl == payload.ttl && hops == payload.relayHopCount) payload
            else payload.copy(ttl = ttl, relayHopCount = hops)
        )
    }

    private fun rejection(p: MeshPayload): String? = when {
        stringsTooLong(p) -> "string_too_long"
        listFields(p).any { it.size > MAX_LIST } -> "list_too_long"
        listFields(p).any { list -> list.any { it.length > MAX_NAME } } -> "list_item_too_long"
        bytesTooLong(p) -> "bytes_too_long"
        else -> null
    }

    private fun stringsTooLong(p: MeshPayload): Boolean =
        idFields(p).any { it.length > MAX_ID } ||
            nameFields(p).any { it.length > MAX_NAME } ||
            listOf(p.type, p.channelId, p.conversationKind, p.identityExchangeId).any { it.length > MAX_TAG } ||
            textTooLong(p)

    private fun textTooLong(p: MeshPayload): Boolean =
        p.publicKey.length > MAX_KEY || p.text.length > MAX_TEXT ||
            (p.encryptedData?.length ?: 0) > MAX_ENCRYPTED || (p.encryptedKey?.length ?: 0) > MAX_KEY

    private fun bytesTooLong(p: MeshPayload): Boolean =
        listOf(p.imageBytes, p.audioBytes, p.liveAudioChunk).any { (it?.size ?: 0) > BinaryCompressor.MAX_MEDIA_BYTES }

    private fun idFields(p: MeshPayload) = listOf(
        p.id, p.targetMessageId, p.senderNodeId, p.targetNodeId, p.sosId
    )

    private fun nameFields(p: MeshPayload) = listOf(p.senderName, p.targetName, p.reader)

    private fun listFields(p: MeshPayload) = listOf(
        p.routePath, p.directedRoute, p.returnRoute, p.connectedNodes, p.connectedNodeIds,
        p.directedRouteNodeIds, p.returnRouteNodeIds
    )

    companion object {
        const val MAX_TTL = 10
        const val MAX_ID = 128
        const val MAX_NAME = 128
        const val MAX_TAG = 64
        const val MAX_KEY = 8_192
        /** Incident sync pages and SOS sync responses ride in [MeshPayload.text]. */
        const val MAX_TEXT = 512 * 1024
        const val MAX_ENCRYPTED = 1024 * 1024
        const val MAX_LIST = 256
    }
}
