package com.example.testresqmesh.core.network

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class BlockControlKind {
    REQUEST,
    ACK
}

/**
 * The encrypted inner meaning of a block-control payload. Outer routing fields remain hints only;
 * receivers compare this envelope with them before mutating block state.
 */
data class BlockControlEnvelope(
    val kind: BlockControlKind,
    val operationId: String,
    val initiatorName: String,
    val targetName: String,
    val replyPublicKey: String = ""
) {
    fun encode(): String = listOf(
        kind.name,
        operationId,
        initiatorName,
        targetName,
        replyPublicKey
    ).joinToString("|") { value ->
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }

    companion object {
        fun decode(value: String): BlockControlEnvelope? = runCatching {
            val parts = value.split('|')
            require(parts.size == 5)
            val decoded = parts.map { part ->
                URLDecoder.decode(part, StandardCharsets.UTF_8.name())
            }
            BlockControlEnvelope(
                kind = BlockControlKind.valueOf(decoded[0]),
                operationId = decoded[1],
                initiatorName = decoded[2],
                targetName = decoded[3],
                replyPublicKey = decoded[4]
            )
        }.getOrNull()?.takeIf {
            it.operationId.isNotBlank() && it.initiatorName.isNotBlank() && it.targetName.isNotBlank()
        }
    }
}
