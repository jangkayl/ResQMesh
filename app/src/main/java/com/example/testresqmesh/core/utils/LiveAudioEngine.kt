package com.example.testresqmesh.core.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LiveAudioEngine(
    private val context: Context,
    private val broadcastChunk: (ByteArray) -> Unit
) {
    private var isRecording = false
    private var isPlaying = false

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private var recordingJob: Job? = null
    private var playbackJob: Job? = null

    fun startRecording() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        audioRecord?.startRecording()
        isRecording = true

        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            val audioBuffer = ByteArray(bufferSize)
            while (isRecording && isActive) {
                val bytesRead = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                if (bytesRead > 0) {
                    val pcmChunk = audioBuffer.copyOf(bytesRead)
                    // COMPRESSION: Reduce size by 50%
                    val compressedChunk = G711Ulaw.compress(pcmChunk)
                    broadcastChunk(compressedChunk)
                }
            }
        }
    }

    fun stopRecording() {
        isRecording = false
        recordingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    var volumeGain: Float = 1.0f
    
    private val _currentSpeaker = MutableStateFlow<String?>(null)
    val currentSpeaker: StateFlow<String?> = _currentSpeaker.asStateFlow()

    fun startPlayback(incomingLiveAudioChunk: SharedFlow<Pair<String, ByteArray>>) {
        isPlaying = true
        
        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, audioFormat)
        
        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            audioFormat,
            minBufferSize,
            AudioTrack.MODE_STREAM
        )

        playbackJob = CoroutineScope(Dispatchers.IO).launch {
            val jitterBuffer = java.util.concurrent.ConcurrentLinkedQueue<Pair<String, ByteArray>>()
            var isBuffering = true
            
            // Collect chunks from network
            launch {
                incomingLiveAudioChunk.collect { (sender, compressedChunk) ->
                    if (isPlaying) {
                        val pcmChunk = G711Ulaw.decompress(compressedChunk, volumeGain)
                        jitterBuffer.offer(Pair(sender, pcmChunk))
                        
                        // Wait until we have 20 chunks (~600ms) before starting to play to completely eliminate choppiness
                        if (isBuffering && jitterBuffer.size >= 20) {
                            isBuffering = false
                            audioTrack?.play()
                        }
                    }
                }
            }
            
            // Play chunks from jitter buffer
            while (isPlaying && isActive) {
                if (!isBuffering) {
                    val pair = jitterBuffer.poll()
                    if (pair != null) {
                        _currentSpeaker.value = pair.first
                        audioTrack?.write(pair.second, 0, pair.second.size)
                    } else {
                        // Buffer underrun, pause playback and buffer again
                        isBuffering = true
                        _currentSpeaker.value = null
                        audioTrack?.pause()
                    }
                } else {
                    _currentSpeaker.value = null
                    delay(10)
                }
            }
        }
    }

    fun stopPlayback() {
        isPlaying = false
        playbackJob?.cancel()
        _currentSpeaker.value = null
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }
}
