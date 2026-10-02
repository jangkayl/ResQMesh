package com.example.testresqmesh.feature.comms.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.testresqmesh.core.utils.MediaHelper
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.data.repository.MeshRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

import com.example.testresqmesh.core.utils.LiveAudioEngine

class WalkieTalkieViewModel(
    private val useCases: com.example.testresqmesh.core.domain.usecase.MeshUseCases,
    private val mediaHelper: MediaHelper,
    private val liveAudioEngine: LiveAudioEngine
) : ViewModel() {

    private val _isWalkieTalkieMode = MutableStateFlow(false)
    val isWalkieTalkieMode = _isWalkieTalkieMode.asStateFlow()

    val currentChannelId: StateFlow<String> = useCases.observeCurrentChannelId()
    
    val currentSpeaker: StateFlow<String?> = combine(
        mediaHelper.currentRadioSpeaker,
        liveAudioEngine.currentSpeaker,
        useCases.observePeerNames()
    ) { radioSender, liveSender, names ->
        val sender = radioSender ?: liveSender
        sender?.let {
            NodeIdentity.displayNameOf(NodeIdentity.currentName(it, names)).ifBlank { NodeIdentity.displayNameOf(it) }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    
    fun setChannel(channelId: String) {
        // Tuning changes never replay previously received notes.
        mediaHelper.setRadioMonitoring(false)
        useCases.setChannel(channelId)
        mediaHelper.setRadioMonitoring(_isWalkieTalkieMode.value)
    }

    private val _volumeGain = MutableStateFlow(1.0f)
    val volumeGain = _volumeGain.asStateFlow()

    fun setVolumeGain(gain: Float) {
        _volumeGain.value = gain
        liveAudioEngine.volumeGain = gain
    }

    init {
        viewModelScope.launch {
            useCases.observeIncomingVoiceMessage().collect { message ->
                if (com.example.testresqmesh.core.model.ConversationPolicy.autoplay(message, currentChannelId.value, _isWalkieTalkieMode.value)) message.audioBase64?.let { base64 ->
                    mediaHelper.enqueueRadioVoice(message.id, base64, message.senderName)
                    if (_isWalkieTalkieMode.value) {
                        useCases.broadcastSeenReceipt(message.id, message.isPrivate, message.senderName)
                    }
                }
            }
        }
    }

    fun startLiveAudio() {
        liveAudioEngine.startRecording()
    }

    fun stopLiveAudio(): String? {
        val wavBytes = liveAudioEngine.stopRecording()
        return if (wavBytes != null) {
            android.util.Base64.encodeToString(wavBytes, android.util.Base64.NO_WRAP)
        } else null
    }

    fun toggleWalkieTalkieMode() {
        _isWalkieTalkieMode.value = !_isWalkieTalkieMode.value
        mediaHelper.setRadioMonitoring(_isWalkieTalkieMode.value)
        if (_isWalkieTalkieMode.value) {
            liveAudioEngine.startPlayback(useCases.observeIncomingLiveAudioChunk())
        } else {
            liveAudioEngine.stopPlayback()
        }
    }

    override fun onCleared() {
        mediaHelper.setRadioMonitoring(false)
        super.onCleared()
    }
}
