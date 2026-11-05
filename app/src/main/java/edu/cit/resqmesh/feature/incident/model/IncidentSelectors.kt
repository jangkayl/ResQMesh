package edu.cit.resqmesh.feature.incident.model

import edu.cit.resqmesh.core.model.Incident
import edu.cit.resqmesh.core.model.IncidentOffer
import edu.cit.resqmesh.core.model.IncidentState

private const val SEVERITY_RANK_CRITICAL = 3
private const val SEVERITY_RANK_SERIOUS = 2
private const val SEVERITY_RANK_OTHER = 1

internal fun incidentMetrics(
    incidents: List<Incident>,
    identity: IncidentIdentityState,
    offersMap: Map<String, List<IncidentOffer>>
): IncidentMetrics {
    val active = incidents.filter { !it.isTerminal() }
    val history = incidents.filter { it.isTerminal() }
    val myActivity = active.filter { incident ->
        isInvolved(incident, offersMap[incident.incidentId].orEmpty(), identity.user?.userId, identity.signingKey)
    }
    return IncidentMetrics(
        totalActive = active.size,
        myActivityCount = myActivity.size,
        historyCount = history.size,
        criticalCount = active.count { it.severity.equals("Critical", ignoreCase = true) }
    )
}

/** What the incident list is showing: the destination tab, its filters and a lower-case search text. */
internal data class IncidentListQuery(
    val destination: IncidentDestination,
    val filters: IncidentFilterState,
    val text: String
)

internal fun selectIncidents(
    incidents: List<Incident>,
    identity: IncidentIdentityState,
    offersMap: Map<String, List<IncidentOffer>>,
    listQuery: IncidentListQuery
): List<Incident> = incidents.filter { incident ->
    val offers = offersMap[incident.incidentId].orEmpty()
    val destination = listQuery.destination
    matchesDestination(incident, destination, offers, identity.user?.userId, identity.signingKey) &&
        matchesCategoryFilters(incident, listQuery.filters) &&
        (destination == IncidentDestination.HISTORY ||
            matchesAssistance(incident, listQuery.filters.assistance, offers)) &&
        (listQuery.text.isEmpty() || matchesQuery(incident, offers, listQuery.text))
}.sortedWith(incidentOrder(listQuery.destination))

private fun matchesDestination(
    incident: Incident,
    destination: IncidentDestination,
    offers: List<IncidentOffer>,
    userId: String?,
    localKey: String?
): Boolean = when (destination) {
    IncidentDestination.ACTIVE -> !incident.isTerminal()
    IncidentDestination.MY_ACTIVITY -> !incident.isTerminal() && isInvolved(incident, offers, userId, localKey)
    IncidentDestination.HISTORY -> incident.isTerminal()
}

/** Emergency type and urgency filters; a null filter matches everything. */
private fun matchesCategoryFilters(incident: Incident, filters: IncidentFilterState): Boolean {
    val typeMatches = filters.emergencyType == null ||
        incident.incidentType.equals(filters.emergencyType, ignoreCase = true)
    val urgencyMatches = filters.urgency == null ||
        incident.severity.equals(filters.urgency, ignoreCase = true)
    return typeMatches && urgencyMatches
}

/** Applies to active destinations only; the caller skips it for history. */
private fun matchesAssistance(incident: Incident, filter: AssistanceFilter, offers: List<IncidentOffer>): Boolean =
    when (filter) {
        AssistanceFilter.ANY -> true
        AssistanceFilter.LOOKING_FOR_HELP -> incident.hasStatus(IncidentState.OPEN)
        AssistanceFilter.NO_OFFERS_YET -> incident.hasStatus(IncidentState.OPEN) && offers.none { !it.withdrawn }
    }

/** [query] is expected lower-case; matches title, description, landmark, reporter, helpers or ID. */
private fun matchesQuery(incident: Incident, offers: List<IncidentOffer>, query: String): Boolean =
    incident.title.lowercase().contains(query) ||
        incident.displayTitle().lowercase().contains(query) ||
        incident.description.lowercase().contains(query) ||
        incident.areaDescription.lowercase().contains(query) ||
        incident.creatorName.lowercase().contains(query) ||
        incident.primaryResponderName?.lowercase()?.contains(query) == true ||
        offers.any { it.helperName.lowercase().contains(query) } ||
        incident.incidentId.lowercase().contains(query)

private fun severityRank(severity: String): Int = when (severity.lowercase()) {
    "critical" -> SEVERITY_RANK_CRITICAL
    "serious" -> SEVERITY_RANK_SERIOUS
    else -> SEVERITY_RANK_OTHER
}

/** History: latest update first. Active and my activity: urgency, then latest update. Ties break by ID. */
private fun incidentOrder(destination: IncidentDestination): Comparator<Incident> =
    if (destination == IncidentDestination.HISTORY) {
        compareByDescending<Incident> { it.updatedAt }.thenBy { it.incidentId }
    } else {
        compareByDescending<Incident> { severityRank(it.severity) }
            .thenByDescending { it.updatedAt }.thenBy { it.incidentId }
    }
