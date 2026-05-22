package com.example.testresqmesh.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import android.util.Log
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

    fun playVoiceMail(base64Audio: String) {
        try {
            mediaPlayer?.release() // Stop previous playback if running
            val decodedBytes = Base64.decode(base64Audio, Base64.NO_WRAP)
            val tempPlayFile = File(context.cacheDir, "temp_audio_play.amr")
            tempPlayFile.writeBytes(decodedBytes)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempPlayFile.absolutePath)
                prepare()
                start()
                setOnCompletionListener { it.release() }
            }
        } catch (e: Exception) {
            Log.e("MediaHelper", "Playback failed", e)
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
}