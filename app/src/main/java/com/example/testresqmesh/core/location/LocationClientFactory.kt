package com.example.testresqmesh.core.location

/** Creates independent [LocationClient] instances for UI that needs its own client lifetime. */
fun interface LocationClientFactory {
    fun create(): LocationClient
}
