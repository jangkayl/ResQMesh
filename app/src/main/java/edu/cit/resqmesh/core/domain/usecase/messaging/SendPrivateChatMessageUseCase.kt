package edu.cit.resqmesh.core.domain.usecase.messaging

import edu.cit.resqmesh.core.domain.repository.MeshNodeRepository
import edu.cit.resqmesh.core.security.input.TextField
import edu.cit.resqmesh.core.security.input.TextPolicy
import edu.cit.resqmesh.core.security.input.ValidationResult

sealed interface PrivateSendResult {
    data object Sent : PrivateSendResult
    /** The recipient key changed and must be verified before private sends resume. */
    data object NeedsKeyVerification : PrivateSendResult
    /** No ready route or no current trusted recipient key. */
    data object Failed : PrivateSendResult
    /** The text exceeds the shared message limit; nothing was sent. */
    data class TooLong(val max: Int) : PrivateSendResult
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
        return when (val checked = TextPolicy.validate(text, TextField.MESSAGE)) {
            is ValidationResult.TooLong -> PrivateSendResult.TooLong(checked.max)
            is ValidationResult.Valid -> send(targetName, checked.value, imageBase64, audioBase64)
            ValidationResult.Blank -> send(targetName, "", imageBase64, audioBase64)
        }
    }

    private fun send(targetName: String, text: String, imageBase64: String?, audioBase64: String?): PrivateSendResult =
        when {
            repository.sendPrivateMessage(targetName, text, imageBase64, audioBase64) -> PrivateSendResult.Sent
            repository.hasPendingPublicKeyChange(targetName) -> PrivateSendResult.NeedsKeyVerification
            else -> PrivateSendResult.Failed
        }
}
