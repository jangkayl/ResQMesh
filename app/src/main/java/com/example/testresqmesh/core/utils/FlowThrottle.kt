package com.example.testresqmesh.core.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.transform

/** Refresh period for UI lists fed by per-scan-result peer updates. */
const val SCAN_UI_THROTTLE_MS = 400L

/**
 * UI-only rate limiter: the first value passes immediately, later values are held for [periodMs]
 * and only the latest one is delivered, so the final value of a burst is never dropped.
 * Do not use on routing, READY or delivery flows.
 */
fun <T> Flow<T>.throttleLatest(periodMs: Long): Flow<T> =
    conflate().transform { value ->
        emit(value)
        delay(periodMs)
    }
