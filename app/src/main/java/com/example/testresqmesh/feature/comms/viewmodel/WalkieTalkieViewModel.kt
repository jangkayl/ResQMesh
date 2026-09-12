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
    private val repository: MeshRepository,
    private val mediaHelper: MediaHelper,
    private val liveAudioEngine: LiveAudioEngine
) : ViewModel() {

    private val _isWalkieTalkieMode = MutableStateFlow(false)
    val isWalkieTalkieMode = _isWalkieTalkieMode.asStateFlow()

    val currentChannelId: StateFlow<String> = repository.currentChannelId
    
    val currentSpeaker: StateFlow<String?> = liveAudioEngine.currentSpeaker
    
    fun setChannel(channelId: String) {
        repository.setChannel(channelId)
    }

    fun setVolumeGain(gain: Float) {
        liveAudioEngine.volumeGain = gain
    }

    init {
        viewModelScope.launch {
            repository.incomingVoiceMessage.collect { message ->
                if (_isWalkieTalkieMode.value) {
                    message.audioBase64?.let { base64 ->
                        mediaHelper.playVoiceMail(base64)
                        repository.broadcastSeenReceipt(message.id, message.isPrivate, message.senderName)
                    }
                }
            }
        }
    }

    fun startLiveAudio() {
        liveAudioEngine.startRecording()
    }

    fun stopLiveAudio() {
        liveAudioEngine.stopRecording()
    }

    fun toggleWalkieTalkieMode() {
        _isWalkieTalkieMode.value = !_isWalkieTalkieMode.value
        if (_isWalkieTalkieMode.value) {
            liveAudioEngine.startPlayback(repository.incomingLiveAudioChunk)
        } else {
            liveAudioEngine.stopPlayback()
        }
    }
}
