package edu.cit.resqmesh.data.location

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import edu.cit.resqmesh.core.location.LocationClient
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import edu.cit.resqmesh.core.location.GOOD_FIX_MAX_ACCURACY_M
import edu.cit.resqmesh.core.location.LiveLocationDemand
import edu.cit.resqmesh.core.location.LiveLocationSpec
import edu.cit.resqmesh.core.location.LocationFix
import edu.cit.resqmesh.core.location.LocationStatus
import edu.cit.resqmesh.core.location.SharedResultRequest
import edu.cit.resqmesh.core.location.isFixUsable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DefaultLocationClient(
    private val context: Context
) : LocationClient {

    private val client: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context.applicationContext)
    private val handler = Handler(Looper.getMainLooper())
    private var cachedLocation: Location? = null
        set(value) {
            field = value
            _location.value = value
            _lastFix.value = value?.let {
                LocationFix(it.accuracy, it.time.takeIf { t -> t > 0 } ?: System.currentTimeMillis())
            }
        }
    private val _location = MutableStateFlow<Location?>(null)
    override val location: StateFlow<Location?> = _location.asStateFlow()
    private val _lastFix = MutableStateFlow<LocationFix?>(null)
    override val lastFix: StateFlow<LocationFix?> = _lastFix.asStateFlow()
    private val pinpoint = SharedResultRequest<Location?>()
    private val liveDemand = LiveLocationDemand { applyUpdates() }
    private var trackingIntervalMs: Long? = null
    private var updatesActive = false
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                cachedLocation = location
                _locationStatus.value = LocationStatus.READY
            }
        }
    }

    // Offline phones have no Wi-Fi/cell positioning to lean on, so a short GPS fix keeps later ones quick.
    private val warmFix = object : Runnable {
        override fun run() {
            if (trackingIntervalMs == null) return
            if (needsWarmFix()) requestPinpointLocation { }
            handler.postDelayed(this, WARM_FIX_INTERVAL_MS)
        }
    }

    private val _locationStatus = MutableStateFlow(LocationStatus.IDLE)
    override val locationStatus: StateFlow<LocationStatus> = _locationStatus.asStateFlow()

    @SuppressLint("MissingPermission")
    override fun startTracking(interval: Long) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            _locationStatus.value = LocationStatus.ERROR_DENIED
            return
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager != null && !LocationManagerCompat.isLocationEnabled(locationManager)) {
            _locationStatus.value = LocationStatus.ERROR_DISABLED
            return
        }

        _locationStatus.value = LocationStatus.ACQUIRING
        trackingIntervalMs = interval

        // Check if we already have a reasonably fresh cached location
        try {
            client.lastLocation.addOnSuccessListener { loc ->
                // Tracking may have stopped (auto-detect off) before this arrives.
                if (loc != null && trackingIntervalMs != null) {
                    cachedLocation = loc
                    _locationStatus.value = LocationStatus.READY
                }
            }
        } catch (e: SecurityException) {
            trackingIntervalMs = null
            _locationStatus.value = LocationStatus.ERROR_DENIED
            return
        }

        applyUpdates()
        handler.removeCallbacks(warmFix)
        handler.post(warmFix)
    }

    override fun stopTracking() {
        trackingIntervalMs = null
        handler.removeCallbacks(warmFix)
        applyUpdates()
        if (cachedLocation == null) {
            _locationStatus.value = LocationStatus.IDLE
        }
    }

    override fun clearCachedLocation() {
        cachedLocation = null
    }

    override fun getLastKnownLocation(): Location? {
        return cachedLocation
    }

    override fun acquireLive(intervalMs: Long, minDistanceMeters: Float): AutoCloseable =
        liveDemand.acquire(LiveLocationSpec(intervalMs, minDistanceMeters))

    /** Live demand outranks idle tracking; there is only ever one registered callback. */
    @SuppressLint("MissingPermission")
    private fun applyUpdates() {
        if (updatesActive) client.removeLocationUpdates(locationCallback)
        updatesActive = false
        val live = liveDemand.current
        val tracking = trackingIntervalMs
        val request = when {
            live != null -> LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, live.intervalMs)
                .setMinUpdateDistanceMeters(live.minDistanceMeters)
                .build()
            tracking != null -> LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, tracking).build()
            else -> return
        }
        try {
            client.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            updatesActive = true
        } catch (e: SecurityException) {
            _locationStatus.value = LocationStatus.ERROR_DENIED
        }
    }

    /** Background apps get no fresh fixes without background location, so only a visible app spends GPS on this. */
    private fun needsWarmFix(): Boolean {
        val state = ActivityManager.RunningAppProcessInfo().also(ActivityManager::getMyMemoryState)
        val visible = state.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        val hasGoodFix = cachedLocation?.let {
            isFixUsable(it.time, it.accuracy, System.currentTimeMillis(), WARM_FIX_SKIP_AGE_MS, GOOD_FIX_MAX_ACCURACY_M)
        } ?: false
        return visible && !hasGoodFix
    }

    @SuppressLint("MissingPermission")
    override fun requestPinpointLocation(onResult: (Location?) -> Unit) {
        if (!pinpoint.join(onResult)) return
        // A true cold start needs far longer than a phone that already holds a recent fix.
        val capMs = if (cachedLocation == null) COLD_FIX_CAP_MS else WARM_FIX_CAP_MS
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setDurationMillis(capMs)
            .setMaxUpdateAgeMillis(PINPOINT_MAX_FIX_AGE_MS)
            .build()
        try {
            client.getCurrentLocation(request, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        cachedLocation = location
                        _locationStatus.value = LocationStatus.READY
                    }
                    pinpoint.complete(location)
                }
                .addOnFailureListener { pinpoint.complete(null) }
        } catch (e: SecurityException) {
            _locationStatus.value = LocationStatus.ERROR_DENIED
            pinpoint.complete(null)
        } catch (e: Exception) {
            pinpoint.complete(null)
        }
    }
}

private const val WARM_FIX_INTERVAL_MS = 5 * 60 * 1000L
private const val WARM_FIX_SKIP_AGE_MS = 2 * 60 * 1000L
private const val COLD_FIX_CAP_MS = 60_000L
private const val WARM_FIX_CAP_MS = 20_000L
private const val PINPOINT_MAX_FIX_AGE_MS = 30_000L
