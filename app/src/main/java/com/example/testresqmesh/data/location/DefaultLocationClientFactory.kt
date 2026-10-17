package com.example.testresqmesh.data.location

import android.content.Context
import com.example.testresqmesh.core.location.LocationClient
import com.example.testresqmesh.core.location.LocationClientFactory

class DefaultLocationClientFactory(private val context: Context) : LocationClientFactory {
    override fun create(): LocationClient = DefaultLocationClient(context)
}
