package com.example.testresqmesh.core.location

import android.location.Location

interface LocationClient {
    fun startTracking(interval: Long)
    fun stopTracking()
    fun getLastKnownLocation(): Location?
    fun requestPinpointLocation(onResult: (Location?) -> Unit)
}
