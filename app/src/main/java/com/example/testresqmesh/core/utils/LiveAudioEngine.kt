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

    private var archiveStream: java.io.ByteArrayOutputStream? = null
    private val voiceCodec = VoiceCodec()

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
        archiveStream = java.io.ByteArrayOutputStream()
        voiceCodec.startEncoder()

        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            val audioBuffer = ByteArray(bufferSize)
            while (isRecording && isActive) {
                val bytesRead = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                if (bytesRead > 0) {
                    val pcmChunk = audioBuffer.copyOf(bytesRead)
                    
                    // Archive it locally for the guaranteed Voice Note fallback
                    archiveStream?.write(pcmChunk)

                    // COMPRESSION: Use Hardware Opus/AAC
                    val compressedChunk = voiceCodec.encodeChunk(pcmChunk)
                    if (compressedChunk != null) {
                        broadcastChunk(compressedChunk)
                    }
                }
            }
        }
    }

    fun stopRecording(): ByteArray? {
        isRecording = false
        recordingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        voiceCodec.stop()

        val pcmData = archiveStream?.toByteArray()
        archiveStream?.close()
        archiveStream = null

        if (pcmData == null || pcmData.isEmpty()) return null

        // Generate WAV File from raw PCM
        val channels = 1
        val byteRate = 16 * sampleRate * channels / 8
        val totalDataLen = pcmData.size + 36
        val totalAudioLen = pcmData.size

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte(); header[5] = ((totalDataLen shr 8) and 0xff).toByte(); header[6] = ((totalDataLen shr 16) and 0xff).toByte(); header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0 // 16 for PCM
        header[20] = 1; header[21] = 0 // PCM format
        header[22] = channels.toByte(); header[23] = 0
        header[24] = (sampleRate and 0xff).toByte(); header[25] = ((sampleRate shr 8) and 0xff).toByte(); header[26] = ((sampleRate shr 16) and 0xff).toByte(); header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte(); header[29] = ((byteRate shr 8) and 0xff).toByte(); header[30] = ((byteRate shr 16) and 0xff).toByte(); header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (1 * 16 / 8).toByte(); header[33] = 0 // block align
        header[34] = 16; header[35] = 0 // bits per sample
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte(); header[41] = ((totalAudioLen shr 8) and 0xff).toByte(); header[42] = ((totalAudioLen shr 16) and 0xff).toByte(); header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        val wavFile = ByteArray(header.size + pcmData.size)
        System.arraycopy(header, 0, wavFile, 0, header.size)
        System.arraycopy(pcmData, 0, wavFile, header.size, pcmData.size)

        return wavFile
    }

    var volumeGain: Float = 1.0f
    
    private val _currentSpeaker = MutableStateFlow<String?>(null)
    val currentSpeaker: StateFlow<String?> = _currentSpeaker.asStateFlow()

    fun startPlayback(incomingLiveAudioChunk: SharedFlow<Pair<String, ByteArray>>) {
        isPlaying = true
        voiceCodec.startDecoder()
        
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
                        val pcmChunk = voiceCodec.decodeChunk(compressedChunk)
                        if (pcmChunk != null) {
                            // Apply software gain
                            if (volumeGain != 1.0f) {
                                for (i in pcmChunk.indices step 2) {
                                    val low = pcmChunk[i].toInt() and 0xFF
                                    val high = pcmChunk[i + 1].toInt() shl 8
                                    var sample = (low or high).toShort().toInt()
                                    sample = (sample * volumeGain).toInt()
                                    if (sample > Short.MAX_VALUE) sample = Short.MAX_VALUE.toInt()
                                    if (sample < Short.MIN_VALUE) sample = Short.MIN_VALUE.toInt()
                                    pcmChunk[i] = (sample and 0xFF).toByte()
                                    pcmChunk[i + 1] = ((sample shr 8) and 0xFF).toByte()
                                }
                            }
                            jitterBuffer.offer(Pair(sender, pcmChunk))
                        }
                        
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
        voiceCodec.stop()
    }
}
