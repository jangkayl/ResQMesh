package edu.cit.resqmesh.feature.comms.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import edu.cit.resqmesh.R
import edu.cit.resqmesh.core.audio.ImageQuality
import edu.cit.resqmesh.core.audio.MediaHelper
import edu.cit.resqmesh.feature.comms.ui.components.ChatInput
import edu.cit.resqmesh.feature.comms.ui.components.VoiceNoteLimitEffect
import edu.cit.resqmesh.feature.comms.viewmodel.CommunicationViewModel

/** The public composer for an active SOS: images stay at the lowest tier, and the alert carries location. */
@Suppress("FunctionNaming")
@Composable
internal fun SosConversationComposer(
    vm: CommunicationViewModel,
    media: MediaHelper,
    sosId: String,
    input: String,
    onInputChange: (String) -> Unit
) {
    val sendBudget by vm.chatBudgetState.collectAsState()
    val imageBudget by vm.imageBudgetState.collectAsState()
    var pendingImage by remember(sosId) { mutableStateOf<String?>(null) }
    var pendingAudio by remember(sosId) { mutableStateOf<String?>(null) }
    var isRecording by remember(sosId) { mutableStateOf(false) }
    val voiceNoteText = stringResource(R.string.private_chat_voice_note)
    val photoText = stringResource(R.string.private_chat_photo)

    VoiceNoteLimitEffect(media, isRecording) {
        isRecording = false
        media.stopRecording()?.let { pendingAudio = it }
    }

    ChatInput(
        inputText = input,
        onTextChange = onInputChange,
        pendingImage = pendingImage,
        onImageSelected = { image: String, _: ImageQuality -> pendingImage = image },
        onClearImage = { pendingImage = null },
        pendingAudio = pendingAudio,
        onClearAudio = { pendingAudio = null },
        isRecording = isRecording,
        onToggleRecord = {
            if (isRecording) {
                isRecording = false
                media.stopRecording()?.let { pendingAudio = it }
            } else {
                isRecording = media.startRecording()
            }
        },
        onSend = {
            val text = when {
                input.isNotBlank() -> input.trim()
                pendingAudio != null -> voiceNoteText
                pendingImage != null -> photoText
                else -> ""
            }
            // False means nothing was sent: keep the text and attachments.
            if (text.isNotBlank() && vm.sendSosReply(sosId, text, pendingAudio, pendingImage)) {
                onInputChange("")
                pendingImage = null
                pendingAudio = null
            }
        },
        onSendLocation = {},
        mediaHelper = media,
        sendBudget = sendBudget,
        imageBudget = imageBudget,
        showLocation = false,
        showImageQuality = false
    )
}
