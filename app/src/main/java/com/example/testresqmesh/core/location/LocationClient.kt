package com.example.testresqmesh.core.location

import android.location.Location

import kotlinx.coroutines.flow.StateFlow

interface LocationClient {
    val locationStatus: StateFlow<LocationStatus>
    
    fun startTracking(interval: Long)
    fun stopTracking()
    fun getLastKnownLocation(): Location?
    fun requestPinpointLocation(onResult: (Location?) -> Unit)
}
