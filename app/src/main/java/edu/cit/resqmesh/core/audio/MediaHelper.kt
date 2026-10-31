package edu.cit.resqmesh.core.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import edu.cit.resqmesh.core.logging.AppLogger
import edu.cit.resqmesh.core.logging.trace.TraceDir
import edu.cit.resqmesh.core.logging.trace.TraceDropReason
import edu.cit.resqmesh.core.logging.trace.TraceScope
import edu.cit.resqmesh.core.logging.trace.TraceStage
import edu.cit.resqmesh.core.network.TransferTraceEmitter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream
import java.io.File

private const val CAPTURE_DIR = "captures"

class MediaHelper(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var mediaPlayer: MediaPlayer? = null
    private val captureAuthority = "${context.packageName}.fileprovider"

    private val _recordingLimitReached = MutableStateFlow(false)
    val recordingLimitReached: StateFlow<Boolean> = _recordingLimitReached

    private fun traceVoice(dir: TraceDir, reason: TraceDropReason) =
        TransferTraceEmitter.voice(dir, TraceStage.FAILED, TraceScope.UNKNOWN, "", reason, "media")

    // --- AUDIO LOGIC ---
    fun startRecording(): Boolean {
        _recordingLimitReached.value = false
        return try {
            audioFile = File(context.cacheDir, "temp_audio_record.amr")
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.AMR_NB)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(audioFile!!.absolutePath)
                setMaxDuration(MAX_VOICE_NOTE_MS)
                setOnInfoListener { recorder, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) finishAtLimit(recorder)
                }
                prepare()
                start()
            }
            true
        } catch (e: Exception) {
            AppLogger.d("MediaHelper", "Failed to start recording: ${e.javaClass.simpleName}")
            traceVoice(TraceDir.OUT, TraceDropReason.RECORD_FAILED)
            false
        }
    }

    /** Releases the recorder now, so stopRecording only reads the file and cannot lose the note on a second stop. */
    private fun finishAtLimit(recorder: MediaRecorder) {
        if (recorder !== mediaRecorder) return
        runCatching { recorder.stop() }.onFailure {
            AppLogger.d("MediaHelper", "Recorder stop at limit failed: ${it.javaClass.simpleName}")
        }
        recorder.release()
        mediaRecorder = null
        _recordingLimitReached.value = true
    }

    fun stopRecording(): String? {
        _recordingLimitReached.value = false
        return try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null

            val audioBytes = audioFile?.readBytes()
            if (audioBytes == null) traceVoice(TraceDir.OUT, TraceDropReason.RECORD_FAILED)
            if (audioBytes != null) Base64.encodeToString(audioBytes, Base64.NO_WRAP) else null
        } catch (e: Exception) {
            AppLogger.d("MediaHelper", "Failed to stop recording: ${e.javaClass.simpleName}")
            traceVoice(TraceDir.OUT, TraceDropReason.RECORD_FAILED)
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
        if (isSirenPlaying || !radioMonitoring || manualAudio != null || mediaPlayer != null) return
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
                // Observe only: returning false keeps the default behaviour (the completion listener still runs).
                setOnErrorListener { _, _, _ ->
                    traceVoice(TraceDir.IN, TraceDropReason.PLAYBACK_FAILED)
                    false
                }
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
            AppLogger.d("MediaHelper", "Playback failed: ${e.javaClass.simpleName}")
            traceVoice(TraceDir.IN, TraceDropReason.PLAYBACK_FAILED)
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
    /** Blocking; call off the main thread. Returns null when the image cannot be read. */
    fun compressImageUriToBase64(uri: Uri, level: ImageQuality = ImageQuality.STANDARD): String? {
        return try {
            val bitmap = decodeUpright(uri, level) ?: return null
            val (width, height) = ImageEncodingPolicy.scaledSize(bitmap.width, bitmap.height, level)
            val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
            if (scaled !== bitmap) bitmap.recycle()
            val encoded = ImageEncodingPolicy.encodeWithinTarget(level) { quality -> encodeWebp(scaled, quality) }
            scaled.recycle()
            encoded.takeIf { it.isNotEmpty() }?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        } catch (e: Exception) {
            AppLogger.d("MediaHelper", "Image compression failed: ${e.javaClass.simpleName}")
            null
        }
    }

    /** Creates a private temp file for the camera app, clearing older captures except [keep]. */
    fun createCaptureUri(keep: Uri?): Uri {
        val dir = File(context.cacheDir, CAPTURE_DIR).apply { mkdirs() }
        dir.listFiles()?.filter { it.name != keep?.lastPathSegment }?.forEach { it.delete() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg").apply { createNewFile() }
        return FileProvider.getUriForFile(context, captureAuthority, file)
    }

    /** Deletes a temp capture; other URIs, such as gallery picks, are left alone. */
    fun deleteCapture(uri: Uri?) {
        if (uri?.authority != captureAuthority) return
        uri.lastPathSegment?.let { File(File(context.cacheDir, CAPTURE_DIR), File(it).name).delete() }
    }

    private fun decodeUpright(uri: Uri, level: ImageQuality): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply {
            inSampleSize = ImageEncodingPolicy.sampleSize(bounds.outWidth, bounds.outHeight, level)
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        return decoded?.let { applyExifOrientation(it, uri) }
    }

    private fun applyExifOrientation(decoded: Bitmap, uri: Uri): Bitmap {
        val exif = context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) } ?: return decoded
        val matrix = Matrix().apply {
            if (exif.isFlipped) postScale(-1f, 1f)
            postRotate(exif.rotationDegrees.toFloat())
        }
        val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (upright !== decoded) decoded.recycle()
        return upright
    }

    private fun encodeWebp(bitmap: Bitmap, quality: Int): ByteArray {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(webpFormat(), quality, outputStream)
        return outputStream.toByteArray()
    }

    @Suppress("DEPRECATION") // WEBP is the only lossy WebP format below API 30.
    private fun webpFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY
        else Bitmap.CompressFormat.WEBP

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
        if (radioQueue.current != null && manualAudio == null) {
            radioResumePositionMs = mediaPlayer?.currentPosition ?: radioResumePositionMs
            releasePlayer()
        }

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
        playNextRadioNote()
    }
}
