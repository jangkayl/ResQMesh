package edu.cit.resqmesh.core.location

import android.location.Location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Live map demand: a person walking moves a few metres between updates. */
const val MAP_LIVE_INTERVAL_MS = 5_000L
const val MAP_LIVE_MIN_DISTANCE_M = 5f

private val NO_LOCATION = MutableStateFlow<Location?>(null).asStateFlow()

interface LocationClient {
    val locationStatus: StateFlow<LocationStatus>
    val lastFix: StateFlow<LocationFix?>

    /** The newest fix from any source; collecting it demands no extra GPS work. */
    val location: StateFlow<Location?> get() = NO_LOCATION

    fun startTracking(interval: Long)
    fun stopTracking()
    fun getLastKnownLocation(): Location?

    /** Concurrent callers share one request and all receive its result; null means no fix within the time cap. */
    fun requestPinpointLocation(onResult: (Location?) -> Unit)

    /** Holds high-accuracy updates until closed. The fastest open session wins. */
    fun acquireLive(intervalMs: Long, minDistanceMeters: Float): AutoCloseable = AutoCloseable {}

    /** Drops the cached fix, used when the user turns auto-detect off. */
    fun clearCachedLocation() {}
}
