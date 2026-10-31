package edu.cit.resqmesh.feature.comms.ui.components

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import edu.cit.resqmesh.core.audio.MediaHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

private const val ELAPSED_TICK_MS = 250L

/** Milliseconds since [active] became true, or 0 while it is false. */
@Composable
fun rememberVoiceNoteElapsedMs(active: Boolean): State<Long> = produceState(0L, active) {
    value = 0L
    if (active) {
        val startedAt = SystemClock.elapsedRealtime()
        while (true) {
            value = SystemClock.elapsedRealtime() - startedAt
            delay(ELAPSED_TICK_MS)
        }
    }
}

/** Tap-to-record composers stop at the limit; the caller keeps the note pending for the user to send. */
@Suppress("FunctionNaming") // Composable effects use PascalCase
@Composable
fun VoiceNoteLimitEffect(mediaHelper: MediaHelper, isRecording: Boolean, onLimitReached: () -> Unit) {
    val latestOnLimitReached by rememberUpdatedState(onLimitReached)
    LaunchedEffect(mediaHelper, isRecording) {
        if (isRecording) {
            mediaHelper.recordingLimitReached.first { it }
            latestOnLimitReached()
        }
    }
}
