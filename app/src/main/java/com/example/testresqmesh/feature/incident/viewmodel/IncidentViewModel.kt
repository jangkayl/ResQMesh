package com.example.testresqmesh.feature.incident.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.testresqmesh.data.local.entity.DomainEventEntity
import com.example.testresqmesh.data.local.entity.IncidentEntity
import com.example.testresqmesh.data.local.entity.IncidentOfferEntity
import com.example.testresqmesh.data.local.entity.UserEntity
import com.example.testresqmesh.data.repository.IncidentRepository
import com.example.testresqmesh.data.repository.IdentityProvider
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

enum class IncidentScope {
    LIVE_OPERATIONS,
    ARCHIVE
}

enum class TriageQuickFilter {
    ALL,
    NEEDS_TRIAGE,
    CRITICAL_ONLY,
    MY_TASKS,
    UNASSISTED_ONLY
}

typealias IncidentTriageTab = IncidentScope

data class IncidentMetrics(
    val totalActive: Int = 0,
    val needsTriageCount: Int = 0,
    val myTasksCount: Int = 0,
    val criticalCount: Int = 0,
    val unassistedCount: Int = 0
)

class IncidentViewModel(
    private val incidentRepository: IncidentRepository,
    private val identityManager: IdentityProvider
) : ViewModel() {

    val activeIncidents: StateFlow<List<IncidentEntity>> = incidentRepository.getActiveIncidents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allIncidents: StateFlow<List<IncidentEntity>> = incidentRepository.getAllIncidents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val localUser: StateFlow<UserEntity?> = identityManager.observeUser()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allActiveOffers: StateFlow<List<IncidentOfferEntity>> = (incidentRepository.observeAllActiveOffers() ?: flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val offersByIncident: StateFlow<Map<String, List<IncidentOfferEntity>>> = allActiveOffers
        .map { list -> list.groupBy { it.incidentId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val myOfferedIncidentIds: StateFlow<Set<String>> = allActiveOffers
        .map { list ->
            val localKey = incidentRepository.localIncidentSigningKey()
            if (localKey != null) {
                list.filter { it.helperKey == localKey && !it.withdrawn }.map { it.incidentId }.toSet()
            } else emptySet()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _scope = MutableStateFlow(IncidentScope.LIVE_OPERATIONS)
    val scope: StateFlow<IncidentScope> = _scope.asStateFlow()

    private val _triageFilter = MutableStateFlow(TriageQuickFilter.ALL)
    val triageFilter: StateFlow<TriageQuickFilter> = _triageFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTypeFilter = MutableStateFlow<String?>(null)
    val selectedTypeFilter: StateFlow<String?> = _selectedTypeFilter.asStateFlow()

    val metrics: StateFlow<IncidentMetrics> = combine(allIncidents, localUser, offersByIncident) { incidents, user, offersMap ->
        val active = incidents.filter { it.status !in setOf("RESOLVED", "CANCELLED") }
        val localKey = incidentRepository.localIncidentSigningKey()
        IncidentMetrics(
            totalActive = active.size,
            needsTriageCount = incidents.count { it.status == "OPEN" },
            myTasksCount = user?.let { u ->
                incidents.count {
                    (it.primaryResponderId == u.userId && it.status in setOf("ASSIGNED", "RESPONDING")) ||
                        (it.workflowVersion == 2 && it.selectedHelperKey == localKey &&
                            it.status == "AWAITING_HELPER")
                }
            } ?: 0,
            criticalCount = active.count { it.severity.equals("Critical", ignoreCase = true) },
            unassistedCount = active.count { it.status == "OPEN" && (offersMap[it.incidentId]?.isEmpty() != false) }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IncidentMetrics())

    val filteredIncidents: StateFlow<List<IncidentEntity>> = combine(
        allIncidents,
        localUser,
        offersByIncident,
        _scope,
        _triageFilter,
        _searchQuery,
        _selectedTypeFilter
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val incidents = args[0] as List<IncidentEntity>
        val user = args[1] as? UserEntity
        @Suppress("UNCHECKED_CAST")
        val offersMap = args[2] as Map<String, List<IncidentOfferEntity>>
        val currentScope = args[3] as IncidentScope
        val currentTriage = args[4] as TriageQuickFilter
        val query = (args[5] as? String).orEmpty().trim().lowercase()
        val typeFilter = args[6] as? String
        val localKey = incidentRepository.localIncidentSigningKey()

        incidents.filter { incident ->
            // 1. Scope filter
            val matchesScope = when (currentScope) {
                IncidentScope.LIVE_OPERATIONS -> incident.status !in setOf("RESOLVED", "CANCELLED")
                IncidentScope.ARCHIVE -> incident.status in setOf("RESOLVED", "CANCELLED")
            }
            if (!matchesScope) return@filter false

            // 2. Triage quick-filter (applies in LIVE_OPERATIONS)
            if (currentScope == IncidentScope.LIVE_OPERATIONS) {
                val matchesTriage = when (currentTriage) {
                    TriageQuickFilter.ALL -> true
                    TriageQuickFilter.NEEDS_TRIAGE -> incident.status == "OPEN"
                    TriageQuickFilter.CRITICAL_ONLY -> incident.severity.equals("Critical", ignoreCase = true)
                    TriageQuickFilter.UNASSISTED_ONLY -> incident.status == "OPEN" && (offersMap[incident.incidentId]?.isEmpty() != false)
                    TriageQuickFilter.MY_TASKS -> user?.userId != null &&
                            ((incident.primaryResponderId == user.userId &&
                                incident.status in setOf("ASSIGNED", "RESPONDING")) ||
                                (incident.workflowVersion == 2 && incident.selectedHelperKey == localKey &&
                                    incident.status == "AWAITING_HELPER"))
                }
                if (!matchesTriage) return@filter false
            }

            // 3. Emergency Type filter
            if (typeFilter != null && !incident.incidentType.equals(typeFilter, ignoreCase = true)) {
                return@filter false
            }

            // 4. Keyword Search query
            if (query.isNotEmpty()) {
                val matchesQuery = incident.incidentType.lowercase().contains(query) ||
                        incident.description.lowercase().contains(query) ||
                        incident.areaDescription.lowercase().contains(query) ||
                        incident.creatorName.lowercase().contains(query) ||
                        (incident.primaryResponderName?.lowercase()?.contains(query) == true) ||
                        incident.incidentId.lowercase().contains(query)
                if (!matchesQuery) return@filter false
            }

            true
        }.sortedWith(
            compareByDescending<IncidentEntity> {
                // Priority ranking: Critical > Serious > Moderate
                when (it.severity.lowercase()) {
                    "critical" -> 3
                    "serious" -> 2
                    else -> 1
                }
            }.thenByDescending { it.updatedAt }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setScope(scope: IncidentScope) {
        _scope.value = scope
        // When switching to archive, reset triage filters that are active-only
        if (scope == IncidentScope.ARCHIVE) {
            _triageFilter.value = TriageQuickFilter.ALL
        }
    }

    fun setTriageFilter(filter: TriageQuickFilter) {
        _triageFilter.value = if (_triageFilter.value == filter && filter != TriageQuickFilter.ALL) {
            TriageQuickFilter.ALL
        } else {
            filter
        }
    }

    fun setTypeFilter(type: String?) {
        _selectedTypeFilter.value = if (_selectedTypeFilter.value == type) null else type
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearFilters() {
        _searchQuery.value = ""
        _triageFilter.value = TriageQuickFilter.ALL
        _selectedTypeFilter.value = null
    }

    private val _selectedIncident = MutableStateFlow<IncidentEntity?>(null)
    val selectedIncident: StateFlow<IncidentEntity?> = _selectedIncident.asStateFlow()

    private val _incidentEvents = MutableStateFlow<List<DomainEventEntity>>(emptyList())
    val incidentEvents: StateFlow<List<DomainEventEntity>> = _incidentEvents.asStateFlow()
    private val _offers = MutableStateFlow<List<IncidentOfferEntity>>(emptyList())
    val offers: StateFlow<List<IncidentOfferEntity>> = _offers.asStateFlow()
    val localSigningKey: String? get() = incidentRepository.localIncidentSigningKey()
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()
    private val _actionBusy = MutableStateFlow(false)
    val actionBusy: StateFlow<Boolean> = _actionBusy.asStateFlow()
    private var selectedIncidentJob: Job? = null
    private var selectedEventsJob: Job? = null
    private var selectedOffersJob: Job? = null

    init {
        viewModelScope.launch {
            identityManager.getOrCreateUser()
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
        onSuccess: () -> Unit = {},
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
                locationAccuracyMeters = locationAccuracyMeters
            )
                selectIncident(incident)
                onSuccess()
            } catch (_: Exception) {
                onFailure()
            }
        }
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
