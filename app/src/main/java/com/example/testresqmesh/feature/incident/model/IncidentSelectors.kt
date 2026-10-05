package com.example.testresqmesh.feature.incident.model

import com.example.testresqmesh.data.local.entity.IncidentEntity
import com.example.testresqmesh.data.local.entity.IncidentOfferEntity
import com.example.testresqmesh.data.local.entity.UserEntity
import com.example.testresqmesh.feature.incident.viewmodel.IncidentIdentityState
import com.example.testresqmesh.feature.incident.viewmodel.IncidentMetrics
import com.example.testresqmesh.feature.incident.viewmodel.IncidentDestination
import com.example.testresqmesh.feature.incident.viewmodel.IncidentFilterState
import com.example.testresqmesh.feature.incident.viewmodel.AssistanceFilter
import com.example.testresqmesh.feature.incident.viewmodel.isInvolved
import com.example.testresqmesh.feature.incident.viewmodel.displayTitle

internal fun incidentMetrics(incidents: List<IncidentEntity>, identity: IncidentIdentityState, offersMap: Map<String, List<IncidentOfferEntity>>): IncidentMetrics = run {
        val active = incidents.filter { it.status !in setOf("RESOLVED", "CANCELLED") }
        val history = incidents.filter { it.status in setOf("RESOLVED", "CANCELLED") }
        val localKey = identity.signingKey
        val myActivity = active.filter { inc ->
            isInvolved(inc, offersMap[inc.incidentId].orEmpty(), identity.user?.userId, localKey)
        }
        IncidentMetrics(
            totalActive = active.size,
            myActivityCount = myActivity.size,
            historyCount = history.size,
            criticalCount = active.count { it.severity.equals("Critical", ignoreCase = true) }
        )
}

internal fun selectIncidents(incidents: List<IncidentEntity>, user: UserEntity?, offersMap: Map<String, List<IncidentOfferEntity>>, currentDest: IncidentDestination, currentFilters: IncidentFilterState, query: String, localKey: String?): List<IncidentEntity> = run {
        incidents.filter { incident ->
            // 1. Destination filter
            val matchesDest = when (currentDest) {
                IncidentDestination.ACTIVE -> incident.status !in setOf("RESOLVED", "CANCELLED")
                IncidentDestination.MY_ACTIVITY -> {
                    incident.status !in setOf("RESOLVED", "CANCELLED") &&
                        isInvolved(incident, offersMap[incident.incidentId].orEmpty(), user?.userId, localKey)
                }
                IncidentDestination.HISTORY -> incident.status in setOf("RESOLVED", "CANCELLED")
            }
            if (!matchesDest) return@filter false

            // 2. Emergency Type filter
            if (currentFilters.emergencyType != null &&
                !incident.incidentType.equals(currentFilters.emergencyType, ignoreCase = true)
            ) {
                return@filter false
            }

            // 3. Urgency filter
            if (currentFilters.urgency != null &&
                !incident.severity.equals(currentFilters.urgency, ignoreCase = true)
            ) {
                return@filter false
            }

            // 4. Assistance filter (active destinations only)
            if (currentDest != IncidentDestination.HISTORY) {
                val activeOffers = offersMap[incident.incidentId].orEmpty().filter { !it.withdrawn }
                when (currentFilters.assistance) {
                    AssistanceFilter.ANY -> Unit
                    AssistanceFilter.LOOKING_FOR_HELP -> {
                        if (incident.status != "OPEN") return@filter false
                    }
                    AssistanceFilter.NO_OFFERS_YET -> {
                        if (incident.status != "OPEN" || activeOffers.isNotEmpty()) return@filter false
                    }
                }
            }

            // 5. Keyword search query
            if (query.isNotEmpty()) {
                val matchesTitle = incident.title.lowercase().contains(query) ||
                    incident.displayTitle().lowercase().contains(query)
                val matchesDesc = incident.description.lowercase().contains(query)
                val matchesLandmark = incident.areaDescription.lowercase().contains(query)
                val matchesReporter = incident.creatorName.lowercase().contains(query)
                val matchesHelper = (incident.primaryResponderName?.lowercase()?.contains(query) == true) ||
                    offersMap[incident.incidentId].orEmpty().any { it.helperName.lowercase().contains(query) }
                val matchesId = incident.incidentId.lowercase().contains(query)

                if (!matchesTitle && !matchesDesc && !matchesLandmark && !matchesReporter && !matchesHelper && !matchesId) {
                    return@filter false
                }
            }

            true
        }.sortedWith(
            if (currentDest == IncidentDestination.HISTORY) {
                // History: latest update descending, stable ID
                compareByDescending<IncidentEntity> { it.updatedAt }.thenBy { it.incidentId }
            } else {
                // Active / My activity: urgency descending, latest update, stable ID
                compareByDescending<IncidentEntity> {
                    when (it.severity.lowercase()) {
                        "critical" -> 3
                        "serious" -> 2
                        else -> 1
                    }
                }.thenByDescending { it.updatedAt }.thenBy { it.incidentId }
            }
        )
}
