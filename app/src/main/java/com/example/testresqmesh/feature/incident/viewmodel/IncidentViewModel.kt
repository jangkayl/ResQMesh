package com.example.testresqmesh.feature.incident.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.testresqmesh.data.local.entity.DomainEventEntity
import com.example.testresqmesh.data.local.entity.IncidentEntity
import com.example.testresqmesh.data.local.entity.IncidentOfferEntity
import com.example.testresqmesh.data.local.entity.UserEntity
import com.example.testresqmesh.data.repository.IncidentRepository
import com.example.testresqmesh.data.repository.IdentityProvider
import com.example.testresqmesh.data.repository.IncidentOwnership
import com.example.testresqmesh.core.utils.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

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

class IncidentViewModel(
    private val incidentRepository: IncidentRepository,
    private val identityManager: IdentityProvider
) : ViewModel() {

    val activeIncidents: StateFlow<List<IncidentEntity>> = incidentRepository.getActiveIncidents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allIncidents: StateFlow<List<IncidentEntity>> = incidentRepository.getAllIncidents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _identityState = MutableStateFlow(IncidentIdentityState())
    val identityState: StateFlow<IncidentIdentityState> = _identityState.asStateFlow()
    val localUser: StateFlow<UserEntity?> = identityState.map { it.user }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private var identityJob: Job? = null

    val allActiveOffers: StateFlow<List<IncidentOfferEntity>> = (incidentRepository.observeAllActiveOffers() ?: flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val offersByIncident: StateFlow<Map<String, List<IncidentOfferEntity>>> = allActiveOffers
        .map { list -> list.groupBy { it.incidentId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val myOfferedIncidentIds: StateFlow<Set<String>> = combine(allActiveOffers, identityState) { list, identity ->
            val localKey = identity.signingKey
            if (localKey != null) {
                list.filter { it.helperKey == localKey && !it.withdrawn }.map { it.incidentId }.toSet()
            } else emptySet()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _destination = MutableStateFlow(IncidentDestination.ACTIVE)
    val destination: StateFlow<IncidentDestination> = _destination.asStateFlow()

    // Backward-compatible scope property
    val scope: StateFlow<IncidentDestination> get() = destination

    private val _filters = MutableStateFlow(IncidentFilterState())
    val filters: StateFlow<IncidentFilterState> = _filters.asStateFlow()

    val appliedFilterCount: StateFlow<Int> = combine(_destination, _filters) { dest, f ->
        f.activeCount(dest)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val metrics: StateFlow<IncidentMetrics> = combine(allIncidents, identityState, offersByIncident) { incidents, identity, offersMap ->
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IncidentMetrics())

    val filteredIncidents: StateFlow<List<IncidentEntity>> = combine(
        allIncidents,
        identityState,
        offersByIncident,
        _destination,
        _filters,
        _searchQuery
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val incidents = args[0] as List<IncidentEntity>
        val identity = args[1] as IncidentIdentityState
        val user = identity.user
        @Suppress("UNCHECKED_CAST")
        val offersMap = args[2] as Map<String, List<IncidentOfferEntity>>
        val currentDest = args[3] as IncidentDestination
        val currentFilters = args[4] as IncidentFilterState
        val query = (args[5] as? String).orEmpty().trim().lowercase()
        val localKey = identity.signingKey

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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setDestination(dest: IncidentDestination) {
        _destination.value = dest
        // History clears active-only assistance filters
        if (dest == IncidentDestination.HISTORY && _filters.value.assistance != AssistanceFilter.ANY) {
            _filters.value = _filters.value.copy(assistance = AssistanceFilter.ANY)
        }
    }

    fun setScope(scope: IncidentDestination) = setDestination(scope)

    fun applyFilters(filterState: IncidentFilterState) {
        _filters.value = if (_destination.value == IncidentDestination.HISTORY) {
            filterState.copy(assistance = AssistanceFilter.ANY)
        } else {
            filterState
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearFilters() {
        _searchQuery.value = ""
        _filters.value = IncidentFilterState()
    }

    private val _selectedIncident = MutableStateFlow<IncidentEntity?>(null)
    val selectedIncident: StateFlow<IncidentEntity?> = _selectedIncident.asStateFlow()

    private val _incidentEvents = MutableStateFlow<List<DomainEventEntity>>(emptyList())
    val incidentEvents: StateFlow<List<DomainEventEntity>> = _incidentEvents.asStateFlow()
    private val _offers = MutableStateFlow<List<IncidentOfferEntity>>(emptyList())
    val offers: StateFlow<List<IncidentOfferEntity>> = _offers.asStateFlow()
    val localSigningKey: String? get() = identityState.value.signingKey
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()
    private val _actionBusy = MutableStateFlow(false)
    val actionBusy: StateFlow<Boolean> = _actionBusy.asStateFlow()
    private var selectedIncidentJob: Job? = null
    private var selectedEventsJob: Job? = null
    private var selectedOffersJob: Job? = null

    init {
        retryIdentity()
        viewModelScope.launch {
            combine(_selectedIncident, identityState) { incident, identity -> incident to identity }.collect { (incident, identity) ->
                if (incident != null) AppLogger.d("INCIDENT_IDENTITY",
                    "OWNERSHIP_CHECK incident=${incident.incidentId} loading=${identity.loading} " +
                        "userIdMatches=${identity.user?.userId == incident.creatorId} " +
                        "signingKeyMatches=${identity.signingKey != null && identity.signingKey == incident.reporterSigningKey} " +
                        "reporter=${IncidentOwnership.isReporter(incident, identity.user?.userId, identity.signingKey)}")
            }
        }
    }

    fun retryIdentity() {
        identityJob?.cancel()
        _identityState.value = IncidentIdentityState()
        identityJob = viewModelScope.launch {
            try {
                identityManager.getOrCreateUser()
                identityManager.observeUser().collect { user ->
                    val key = incidentRepository.localIncidentSigningKey()
                    _identityState.value = IncidentIdentityState(user, key, loading = false,
                        error = when {
                            user == null -> "Your identity is unavailable. Retry to restore incident controls."
                            key == null -> "Your signing identity is unavailable. Retry or reopen the app."
                            else -> null
                        })
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _identityState.value = IncidentIdentityState(loading = false,
                    error = "Could not load your identity. Retry or reopen the app.")
            }
        }
    }

    fun selectIncident(incident: IncidentEntity?) {
        selectedIncidentJob?.cancel()
        selectedEventsJob?.cancel()
        selectedOffersJob?.cancel()
        _offers.value = emptyList()
        _actionMessage.value = null
        _selectedIncident.value = incident
        if (incident != null) {
            selectedIncidentJob = viewModelScope.launch {
                incidentRepository.observeIncidentById(incident.incidentId).collect {
                    _selectedIncident.value = it
                }
            }
            selectedEventsJob = viewModelScope.launch {
                incidentRepository.observeEventsForIncident(incident.incidentId).collect {
                    _incidentEvents.value = it
                }
            }
            selectedOffersJob = viewModelScope.launch {
                (incidentRepository.observeOffersForIncident(incident.incidentId) ?: flowOf(emptyList())).collect {
                    _offers.value = it
                }
            }
        } else {
            _incidentEvents.value = emptyList()
            _offers.value = emptyList()
        }
    }

    fun createIncident(
        type: String,
        severity: String,
        description: String,
        areaDescription: String,
        latitude: Double? = null,
        longitude: Double? = null,
        locationCapturedAt: Long? = null,
        locationAccuracyMeters: Float? = null,
        title: String = "",
        onSuccess: (IncidentEntity) -> Unit = {},
        onFailure: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val incident = incidentRepository.createIncident(
                    incidentType = type,
                    severity = severity,
                    description = description,
                    areaDescription = areaDescription,
                    latitude = latitude,
                    longitude = longitude,
                    locationCapturedAt = locationCapturedAt,
                    locationAccuracyMeters = locationAccuracyMeters,
                    title = title
                )
                selectIncident(incident)
                onSuccess(incident)
            } catch (_: Exception) {
                onFailure()
            }
        }
    }

    fun createIncident(
        type: String,
        severity: String,
        description: String,
        areaDescription: String,
        latitude: Double? = null,
        longitude: Double? = null,
        locationCapturedAt: Long? = null,
        locationAccuracyMeters: Float? = null,
        onSuccess: () -> Unit,
        onFailure: () -> Unit = {}
    ) {
        createIncident(
            type = type,
            severity = severity,
            description = description,
            areaDescription = areaDescription,
            latitude = latitude,
            longitude = longitude,
            locationCapturedAt = locationCapturedAt,
            locationAccuracyMeters = locationAccuracyMeters,
            title = "",
            onSuccess = { _ -> onSuccess() },
            onFailure = onFailure
        )
    }

    fun acknowledge(incidentId: String) {
        viewModelScope.launch {
            incidentRepository.acknowledgeIncident(incidentId)
        }
    }

    fun assignToMe(incidentId: String) {
        viewModelScope.launch {
            incidentRepository.assignIncident(incidentId)
        }
    }

    fun startResponse(incidentId: String) {
        viewModelScope.launch {
            incidentRepository.startResponding(incidentId)
        }
    }

    fun resolve(incidentId: String) {
        viewModelScope.launch {
            incidentRepository.resolveIncident(incidentId)
        }
    }

    fun cancel(incidentId: String) {
        viewModelScope.launch {
            incidentRepository.cancelIncident(incidentId)
        }
    }

    fun releaseAssignment(incidentId: String) {
        viewModelScope.launch {
            incidentRepository.releaseAssignment(incidentId)
        }
    }

    private fun perform(label: String, operation: suspend () -> Boolean) {
        if (_actionBusy.value) return
        _actionBusy.value = true
        viewModelScope.launch {
            try {
                _actionMessage.value = if (operation()) "$label saved locally; waiting for mesh sync." else
                    "$label could not be applied. Refresh the incident and try again."
            } catch (_: Exception) {
                _actionMessage.value = "$label failed. Your incident remains available."
            } finally {
                _actionBusy.value = false
            }
        }
    }

    fun clearActionMessage() { _actionMessage.value = null }
    fun offerHelp(id: String, note: String) = perform("Offer") { incidentRepository.offerHelp(id, note) }
    fun withdrawOffer(id: String) = perform("Withdrawal") { incidentRepository.withdrawOffer(id) }
    fun selectLead(id: String, offerId: String) = perform("Selection") { incidentRepository.selectLead(id, offerId) }
    fun confirmLead(id: String) = perform("Confirmation") { incidentRepository.confirmLead(id) }
    fun declineLead(id: String) = perform("Decline") { incidentRepository.declineLead(id) }
    fun revokeLead(id: String) = perform("Revocation") { incidentRepository.revokeLead(id) }
    fun resolveHelp(id: String) = perform("Resolution") { incidentRepository.resolveHelp(id) }
    fun cancelHelp(id: String) = perform("Cancellation") { incidentRepository.cancelHelp(id) }
}
