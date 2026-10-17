package com.example.testresqmesh.core.domain.usecase.messaging

import com.example.testresqmesh.core.domain.repository.MeshNodeRepository

sealed interface PrivateSendResult {
    data object Sent : PrivateSendResult
    /** The recipient key changed and must be verified before private sends resume. */
    data object NeedsKeyVerification : PrivateSendResult
    /** No ready route or no current trusted recipient key. */
    data object Failed : PrivateSendResult
}

/**
 * Sends a private message. The repository fails closed without a trusted usable key; a refused send
 * is classified afterwards so a pending key change prompts verification instead of a generic failure.
 */
class SendPrivateChatMessageUseCase(private val repository: MeshNodeRepository) {
    operator fun invoke(
        targetName: String,
        text: String,
        imageBase64: String?,
        audioBase64: String?
    ): PrivateSendResult {
        if (repository.sendPrivateMessage(targetName, text, imageBase64, audioBase64)) return PrivateSendResult.Sent
        return if (repository.hasPendingPublicKeyChange(targetName)) PrivateSendResult.NeedsKeyVerification
        else PrivateSendResult.Failed
    }
}
