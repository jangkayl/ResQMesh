package com.example.testresqmesh.feature.incident.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.testresqmesh.data.local.entity.DomainEventEntity
import com.example.testresqmesh.data.local.entity.IncidentEntity
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

    private val _selectedIncident = MutableStateFlow<IncidentEntity?>(null)
    val selectedIncident: StateFlow<IncidentEntity?> = _selectedIncident.asStateFlow()

    private val _incidentEvents = MutableStateFlow<List<DomainEventEntity>>(emptyList())
    val incidentEvents: StateFlow<List<DomainEventEntity>> = _incidentEvents.asStateFlow()
    private var selectedIncidentJob: Job? = null
    private var selectedEventsJob: Job? = null

    init {
        viewModelScope.launch {
            identityManager.getOrCreateUser()
        }
    }

    fun selectIncident(incident: IncidentEntity?) {
        selectedIncidentJob?.cancel()
        selectedEventsJob?.cancel()
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
        } else {
            _incidentEvents.value = emptyList()
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
        locationAccuracyMeters: Float? = null
    ) {
        viewModelScope.launch {
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
            _selectedIncident.value = incident
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
}
