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

class DefaultLocationClient(
    private val context: Context
) : LocationClient {

    private val client: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context.applicationContext)
    private var cachedLocation: Location? = null
    private var locationCallback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    override fun startTracking(interval: Long) {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, interval)
            .setMinUpdateDistanceMeters(20f)
            .build()
            
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    cachedLocation = location
                }
            }
        }
        
        try {
            client.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
        } catch (e: SecurityException) {
            // Permission denied, ignore gracefully
        }
    }

    override fun stopTracking() {
        locationCallback?.let {
            client.removeLocationUpdates(it)
        }
        locationCallback = null
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
                    }
                    onResult(location)
                }
                .addOnFailureListener {
                    onResult(null)
                }
        } catch (e: SecurityException) {
            onResult(null)
        } catch (e: Exception) {
            onResult(null)
        }
    }
}
