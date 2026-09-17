package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.NodeIdentity

/** Pure routing decision used by private-message dispatch. */
object PrivateDeliveryPlanner {
    sealed interface Target {
        data class Endpoint(val endpointId: String) : Target
        data object Broadcast : Target
    }

    fun select(
        recipientName: String,
        directedRoute: List<String>,
        readyDevices: List<ConnectedDevice>
    ): Target {
        readyDevices.firstOrNull { NodeIdentity.matches(it.name, recipientName) }
            ?.endpointId
            ?.takeIf(String::isNotEmpty)
            ?.let { return Target.Endpoint(it) }

        val nextHopName = directedRoute.getOrNull(1) ?: return Target.Broadcast
        return readyDevices.firstOrNull { NodeIdentity.matches(it.name, nextHopName) }
            ?.endpointId
            ?.takeIf(String::isNotEmpty)
            ?.let(Target::Endpoint)
            ?: Target.Broadcast
    }
}
