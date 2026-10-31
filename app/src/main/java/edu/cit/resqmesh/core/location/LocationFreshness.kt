package edu.cit.resqmesh.core.location

/** A fix this recent and precise needs no new GPS work. */
const val GOOD_FIX_MAX_ACCURACY_M = 50f

/** True when a fix is no older than [maxAgeMs] and, if [maxAccuracyM] is set, at least that precise. */
fun isFixUsable(
    capturedAt: Long,
    accuracyM: Float,
    now: Long,
    maxAgeMs: Long,
    maxAccuracyM: Float = Float.MAX_VALUE
): Boolean = capturedAt > 0 && now - capturedAt <= maxAgeMs && accuracyM <= maxAccuracyM
