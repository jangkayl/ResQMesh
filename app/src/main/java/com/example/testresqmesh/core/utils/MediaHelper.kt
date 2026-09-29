package com.example.testresqmesh.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream
import java.io.File

class MediaHelper(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var mediaPlayer: MediaPlayer? = null

    // --- AUDIO LOGIC ---
    fun startRecording(): Boolean {
        return try {
            audioFile = File(context.cacheDir, "temp_audio_record.amr")
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.AMR_NB)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(audioFile!!.absolutePath)
                prepare()
                start()
            }
            true
        } catch (e: Exception) {
            Log.e("MediaHelper", "Failed to start recording", e)
            false
        }
    }

    fun stopRecording(): String? {
        return try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null

            val audioBytes = audioFile?.readBytes()
            if (audioBytes != null) Base64.encodeToString(audioBytes, Base64.NO_WRAP) else null
        } catch (e: Exception) {
            Log.e("MediaHelper", "Failed to stop recording", e)
            null
        }
    }

    private val radioQueue = RadioVoiceQueue()
    private var radioMonitoring = false
    private var radioResumePositionMs = 0
    private var manualAudio: String? = null
    private var playingFile: File? = null

    private val _currentRadioSpeaker = MutableStateFlow<String?>(null)
    val currentRadioSpeaker: StateFlow<String?> = _currentRadioSpeaker

    private val _currentlyPlayingAudio = MutableStateFlow<String?>(null)
    val currentlyPlayingAudio: StateFlow<String?> = _currentlyPlayingAudio

    fun playVoiceMail(base64Audio: String) {
        togglePlayVoiceMail(base64Audio)
    }

    fun togglePlayVoiceMail(base64Audio: String) {
        if (manualAudio == base64Audio) {
            stopVoiceMail()
            return
        }
        if (radioQueue.current != null && manualAudio == null) {
            radioResumePositionMs = mediaPlayer?.currentPosition ?: radioResumePositionMs
        }
        releasePlayer()
        manualAudio = base64Audio
        playAudio(base64Audio, isRadio = false)
    }

    fun stopVoiceMail() {
        if (manualAudio == null) return
        releasePlayer()
        manualAudio = null
        playNextRadioNote()
    }

    fun setRadioMonitoring(enabled: Boolean) {
        if (radioMonitoring == enabled) return
        radioMonitoring = enabled
        radioQueue.setMonitoring(enabled)
        if (!enabled) {
            radioResumePositionMs = 0
            if (manualAudio == null) releasePlayer()
        }
    }

    fun enqueueRadioVoice(id: String, base64Audio: String, sender: String) {
        if (!radioQueue.enqueue(RadioVoiceQueue.Note(id, base64Audio, sender))) return
        playNextRadioNote()
    }

    private fun playNextRadioNote() {
        if (!radioMonitoring || manualAudio != null || mediaPlayer != null) return
        val note = radioQueue.currentOrNext() ?: return
        playAudio(note.audio, isRadio = true)
    }

    private fun releasePlayer() {
        mediaPlayer?.setOnCompletionListener(null)
        try {
            mediaPlayer?.stop()
        } catch (_: IllegalStateException) {
            // A failed prepare may leave the player unable to stop.
        }
        mediaPlayer?.release()
        mediaPlayer = null
        playingFile?.delete()
        playingFile = null
        _currentlyPlayingAudio.value = null
        _currentRadioSpeaker.value = null
    }

    private fun playAudio(base64Audio: String, isRadio: Boolean) {
        try {
            val decodedBytes = Base64.decode(base64Audio, Base64.NO_WRAP)
            val tempPlayFile = File.createTempFile("temp_audio_play_", ".amr", context.cacheDir)
            playingFile = tempPlayFile
            tempPlayFile.writeBytes(decodedBytes)
            val player = MediaPlayer()
            mediaPlayer = player
            player.apply {
                setDataSource(tempPlayFile.absolutePath)
                prepare()
                if (isRadio && radioResumePositionMs > 0) seekTo(radioResumePositionMs)
                setOnCompletionListener {
                    releasePlayer()
                    if (isRadio) {
                        radioQueue.completeCurrent()
                        radioResumePositionMs = 0
                    } else {
                        manualAudio = null
                    }
                    playNextRadioNote()
                }
                start()
            }
            _currentlyPlayingAudio.value = base64Audio
            _currentRadioSpeaker.value = if (isRadio) radioQueue.current?.sender else null
        } catch (e: Exception) {
            Log.e("MediaHelper", "Playback failed", e)
            releasePlayer()
            if (isRadio) {
                radioQueue.completeCurrent()
                radioResumePositionMs = 0
            } else {
                manualAudio = null
            }
            playNextRadioNote()
        }
    }

    // --- IMAGE LOGIC ---
    fun compressBitmapToBase64(bitmap: Bitmap): String {
        val maxImageSize = 150f
        val ratio = Math.min(maxImageSize / bitmap.width, maxImageSize / bitmap.height)
        val width = Math.round(ratio * bitmap.width)
        val height = Math.round(ratio * bitmap.height)

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 20, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    fun decodeBase64ToBitmap(base64String: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64String, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) { null }
    }

    // --- PROCEDURAL EMERGENCY SIREN ---
    // Uses the system ToneGenerator to create a high-fidelity "Hi-Lo" emergency siren without requiring an external mp3 file.
    private var sirenThread: Thread? = null
    private var isSirenPlaying = false

    fun playEmergencySiren() {
        if (isSirenPlaying) return
        isSirenPlaying = true

        sirenThread = Thread {
            val toneGenerator = android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, 100)
            try {
                while (isSirenPlaying) {
                    // "Hi" tone
                    toneGenerator.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 400)
                    Thread.sleep(450)
                    // "Lo" tone
                    toneGenerator.startTone(android.media.ToneGenerator.TONE_SUP_ERROR, 400)
                    Thread.sleep(450)
                }
            } catch (e: InterruptedException) {
                // Thread stopped
            } finally {
                toneGenerator.release()
            }
        }.apply { start() }
    }

    fun stopEmergencySiren() {
        isSirenPlaying = false
        sirenThread?.interrupt()
        sirenThread = null
    }
}
