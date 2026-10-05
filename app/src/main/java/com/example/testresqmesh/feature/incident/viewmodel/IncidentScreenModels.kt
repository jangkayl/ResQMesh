package com.example.testresqmesh.feature.incident.viewmodel

import com.example.testresqmesh.data.local.entity.UserEntity

enum class IncidentDestination(val label: String) {
    ACTIVE("Active"),
    MY_ACTIVITY("My activity"),
    HISTORY("History")
}

typealias IncidentScope = IncidentDestination

enum class AssistanceFilter(val label: String) {
    ANY("Any"),
    LOOKING_FOR_HELP("Looking for help"),
    NO_OFFERS_YET("No offers yet")
}

data class IncidentFilterState(
    val emergencyType: String? = null,
    val urgency: String? = null,
    val assistance: AssistanceFilter = AssistanceFilter.ANY
) {
    fun activeCount(destination: IncidentDestination): Int {
        var count = 0
        if (emergencyType != null) count++
        if (urgency != null) count++
        if (destination != IncidentDestination.HISTORY && assistance != AssistanceFilter.ANY) count++
        return count
    }
}

enum class TriageQuickFilter {
    ALL,
    NEEDS_TRIAGE,
    CRITICAL_ONLY,
    MY_TASKS,
    UNASSISTED_ONLY
}

typealias IncidentTriageTab = IncidentDestination

data class IncidentMetrics(
    val totalActive: Int = 0,
    val myActivityCount: Int = 0,
    val historyCount: Int = 0,
    val criticalCount: Int = 0
)

data class IncidentIdentityState(
    val user: UserEntity? = null,
    val signingKey: String? = null,
    val loading: Boolean = true,
    val error: String? = null
)
