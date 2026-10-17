package edu.cit.resqmesh.data.location

import android.content.Context
import edu.cit.resqmesh.core.location.LocationClient
import edu.cit.resqmesh.core.location.LocationClientFactory

class DefaultLocationClientFactory(private val context: Context) : LocationClientFactory {
    override fun create(): LocationClient = DefaultLocationClient(context)
}
