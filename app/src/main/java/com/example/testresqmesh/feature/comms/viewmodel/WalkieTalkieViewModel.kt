package com.example.testresqmesh.feature.comms.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.testresqmesh.core.utils.MediaHelper
import com.example.testresqmesh.data.repository.MeshRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    
    val currentSpeaker: StateFlow<String?> = liveAudioEngine.currentSpeaker
    
    fun setChannel(channelId: String) {
        useCases.setChannel(channelId)
    }

    fun setVolumeGain(gain: Float) {
        liveAudioEngine.volumeGain = gain
    }

    init {
        viewModelScope.launch {
            useCases.observeIncomingVoiceMessage().collect { message ->
                if (_isWalkieTalkieMode.value) {
                    message.audioBase64?.let { base64 ->
                        mediaHelper.playVoiceMail(base64)
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
        if (_isWalkieTalkieMode.value) {
            liveAudioEngine.startPlayback(useCases.observeIncomingLiveAudioChunk())
        } else {
            liveAudioEngine.stopPlayback()
        }
    }
}
