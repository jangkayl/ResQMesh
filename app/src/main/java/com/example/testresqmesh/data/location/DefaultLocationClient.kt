package com.example.testresqmesh.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.example.testresqmesh.core.location.LocationClient
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
import com.example.testresqmesh.core.location.LocationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DefaultLocationClient(
    private val context: Context
) : LocationClient {

    private val client: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context.applicationContext)
    private var cachedLocation: Location? = null
    private var locationCallback: LocationCallback? = null

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

        // Check if we already have a reasonably fresh cached location
        try {
            client.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    cachedLocation = loc
                    _locationStatus.value = LocationStatus.READY
                }
            }
        } catch (e: SecurityException) {
            _locationStatus.value = LocationStatus.ERROR_DENIED
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, interval)
            .setMinUpdateDistanceMeters(20f)
            .build()
            
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    cachedLocation = location
                    _locationStatus.value = LocationStatus.READY
                }
            }
        }
        
        try {
            client.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
        } catch (e: SecurityException) {
            _locationStatus.value = LocationStatus.ERROR_DENIED
        }
    }

    override fun stopTracking() {
        locationCallback?.let {
            client.removeLocationUpdates(it)
        }
        locationCallback = null
        if (cachedLocation == null) {
            _locationStatus.value = LocationStatus.IDLE
        }
    }

    override fun getLastKnownLocation(): Location? {
        return cachedLocation
    }

    @SuppressLint("MissingPermission")
    override fun requestPinpointLocation(onResult: (Location?) -> Unit) {
        try {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        cachedLocation = location
                        _locationStatus.value = LocationStatus.READY
                    }
                    onResult(location)
                }
                .addOnFailureListener {
                    onResult(null)
                }
        } catch (e: SecurityException) {
            _locationStatus.value = LocationStatus.ERROR_DENIED
            onResult(null)
        } catch (e: Exception) {
            onResult(null)
        }
    }
}
