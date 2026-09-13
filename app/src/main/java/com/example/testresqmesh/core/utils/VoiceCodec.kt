package com.example.testresqmesh.core.utils

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build

class VoiceCodec {
    private var encoder: MediaCodec? = null
    private var decoder: MediaCodec? = null
    
    private var isEncoderStarted = false
    private var isDecoderStarted = false

    fun startEncoder() {
        try {
            // Use OPUS on Android 10+, fallback to AAC-LC on older devices
            val mime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaFormat.MIMETYPE_AUDIO_OPUS else MediaFormat.MIMETYPE_AUDIO_AAC
            
            val format = MediaFormat.createAudioFormat(mime, 16000, 1)
            format.setInteger(MediaFormat.KEY_BIT_RATE, 16000) // 16 kbps!
            
            if (mime == MediaFormat.MIMETYPE_AUDIO_AAC) {
                format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            }

            encoder = MediaCodec.createEncoderByType(mime)
            encoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder?.start()
            isEncoderStarted = true
        } catch (e: Exception) {
            AppLogger.d("VoiceCodec", "Encoder failed to start: ${e.message}")
        }
    }

    fun encodeChunk(pcmData: ByteArray): ByteArray? {
        if (!isEncoderStarted) return null
        val enc = encoder ?: return null
        
        try {
            val inputIndex = enc.dequeueInputBuffer(10000)
            if (inputIndex >= 0) {
                val inputBuffer = enc.getInputBuffer(inputIndex)
                inputBuffer?.clear()
                inputBuffer?.put(pcmData)
                enc.queueInputBuffer(inputIndex, 0, pcmData.size, System.nanoTime() / 1000, 0)
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var outputIndex = enc.dequeueOutputBuffer(bufferInfo, 10000)
            val stream = java.io.ByteArrayOutputStream()
            
            while (outputIndex >= 0) {
                val outputBuffer = enc.getOutputBuffer(outputIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    val outData = ByteArray(bufferInfo.size)
                    outputBuffer.get(outData)
                    stream.write(outData)
                }
                enc.releaseOutputBuffer(outputIndex, false)
                outputIndex = enc.dequeueOutputBuffer(bufferInfo, 0)
            }
            
            val finalData = stream.toByteArray()
            if (finalData.isNotEmpty()) return finalData
            return null
        } catch (e: Exception) {
            AppLogger.d("VoiceCodec", "Encode error: ${e.message}")
        }
        return null
    }

    fun startDecoder() {
        try {
            val mime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaFormat.MIMETYPE_AUDIO_OPUS else MediaFormat.MIMETYPE_AUDIO_AAC
            val format = MediaFormat.createAudioFormat(mime, 16000, 1)
            decoder = MediaCodec.createDecoderByType(mime)
            decoder?.configure(format, null, null, 0)
            decoder?.start()
            isDecoderStarted = true
        } catch (e: Exception) {
            AppLogger.d("VoiceCodec", "Decoder failed to start: ${e.message}")
        }
    }

    fun decodeChunk(encodedData: ByteArray): ByteArray? {
        if (!isDecoderStarted) return null
        val dec = decoder ?: return null
        
        try {
            val inputIndex = dec.dequeueInputBuffer(10000)
            if (inputIndex >= 0) {
                val inputBuffer = dec.getInputBuffer(inputIndex)
                inputBuffer?.clear()
                inputBuffer?.put(encodedData)
                dec.queueInputBuffer(inputIndex, 0, encodedData.size, System.nanoTime() / 1000, 0)
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var outputIndex = dec.dequeueOutputBuffer(bufferInfo, 10000)
            val stream = java.io.ByteArrayOutputStream()
            
            while (outputIndex >= 0) {
                val outputBuffer = dec.getOutputBuffer(outputIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    val outData = ByteArray(bufferInfo.size)
                    outputBuffer.get(outData)
                    stream.write(outData)
                }
                dec.releaseOutputBuffer(outputIndex, false)
                outputIndex = dec.dequeueOutputBuffer(bufferInfo, 0)
            }
            
            val finalData = stream.toByteArray()
            if (finalData.isNotEmpty()) return finalData
            return null
        } catch (e: Exception) {
            AppLogger.d("VoiceCodec", "Decode error: ${e.message}")
        }
        return null
    }

    fun stop() {
        try {
            if (isEncoderStarted) {
                encoder?.stop()
                encoder?.release()
                isEncoderStarted = false
            }
            if (isDecoderStarted) {
                decoder?.stop()
                decoder?.release()
                isDecoderStarted = false
            }
        } catch (e: Exception) {}
    }
}
