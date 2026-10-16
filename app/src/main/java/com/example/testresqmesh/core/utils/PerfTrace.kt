package com.example.testresqmesh.core.utils

import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.example.testresqmesh.BuildConfig

/**
 * Debug-only main-thread timing for the BLE inbound offload Phase 0 measurement
 * (docs/plans/ble-main-looper-offload.md). Writes straight to Logcat, never to the in-app
 * terminal, so measuring does not add main-thread work. Markers never carry payload content.
 */
object PerfTrace {
    const val TAG = "RESQ_PERF"
    private const val MAIN_SLOW_MS = 32L
    private const val INBOUND_SLOW_MS = 4L

    val enabled: Boolean = BuildConfig.DEBUG

    fun nowNanos(): Long = if (enabled) System.nanoTime() else 0L

    fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000

    /** Logs every main-looper dispatch that blocks for at least [MAIN_SLOW_MS]. */
    fun installMainLooperWatchdog() {
        if (!enabled) return
        // The printer only runs on the main thread, so plain fields are safe here.
        var startedAt = 0L
        var target = ""
        Looper.getMainLooper().setMessageLogging { line ->
            if (line.startsWith(">>>>> Dispatching")) {
                startedAt = SystemClock.uptimeMillis()
                target = line
            } else if (line.startsWith("<<<<< Finished") && startedAt != 0L) {
                val ms = SystemClock.uptimeMillis() - startedAt
                startedAt = 0L
                if (ms >= MAIN_SLOW_MS) Log.d(TAG, "MAIN_SLOW ms=$ms target=${target.substringAfter("} ")}")
            }
        }
    }

    fun inbound(type: String, bytes: Int, decodeMs: Long, handleMs: Long) {
        if (enabled && decodeMs + handleMs >= INBOUND_SLOW_MS) {
            Log.d(TAG, "INBOUND_PREP type=$type bytes=$bytes decodeMs=$decodeMs handleMs=$handleMs")
        }
    }

    fun decrypt(ms: Long, ok: Boolean) {
        if (enabled) Log.d(TAG, "INBOUND_DECRYPT ms=$ms ok=$ok")
    }

    fun media(kind: String, bytes: Int, ms: Long) {
        if (enabled) Log.d(TAG, "INBOUND_MEDIA kind=$kind bytes=$bytes ms=$ms")
    }
}
