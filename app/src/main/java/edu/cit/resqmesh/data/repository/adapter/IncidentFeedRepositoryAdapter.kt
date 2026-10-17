package edu.cit.resqmesh.data.repository.adapter

import edu.cit.resqmesh.core.domain.repository.IncidentFeedRepository
import edu.cit.resqmesh.core.model.DomainEvent
import edu.cit.resqmesh.core.model.Incident
import edu.cit.resqmesh.core.model.IncidentOffer
import edu.cit.resqmesh.data.mapper.toModel
import edu.cit.resqmesh.data.repository.IncidentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Suppress("TooManyFunctions")
class IncidentFeedRepositoryAdapter(private val repository: IncidentRepository) : IncidentFeedRepository {
    override fun getActiveIncidents(): Flow<List<Incident>> =
        repository.getActiveIncidents().map { list -> list.map { it.toModel() } }
    override fun getAllIncidents(): Flow<List<Incident>> =
        repository.getAllIncidents().map { list -> list.map { it.toModel() } }
    override fun observeIncidentById(id: String): Flow<Incident?> =
        repository.observeIncidentById(id).map { it?.toModel() }
    override fun observeEventsForIncident(id: String): Flow<List<DomainEvent>> =
        repository.observeEventsForIncident(id).map { list -> list.map { it.toModel() } }
    override fun observeOffersForIncident(id: String): Flow<List<IncidentOffer>> =
        (repository.observeOffersForIncident(id) ?: flowOf(emptyList())).map { list -> list.map { it.toModel() } }
    override fun observeAllActiveOffers(): Flow<List<IncidentOffer>> =
        (repository.observeAllActiveOffers() ?: flowOf(emptyList())).map { list -> list.map { it.toModel() } }
    override fun localIncidentSigningKey(): String? = repository.localIncidentSigningKey()

    override suspend fun createIncident(
        incidentType: String,
        severity: String,
        description: String,
        areaDescription: String,
        latitude: Double?,
        longitude: Double?,
        locationCapturedAt: Long?,
        locationAccuracyMeters: Float?,
        title: String
    ): Incident = repository.createIncident(
        incidentType, severity, description, areaDescription, latitude, longitude,
        locationCapturedAt, locationAccuracyMeters, title
    ).toModel()
    override suspend fun acknowledgeIncident(incidentId: String) = repository.acknowledgeIncident(incidentId)
    override suspend fun assignIncident(incidentId: String) = repository.assignIncident(incidentId)
    override suspend fun startResponding(incidentId: String) = repository.startResponding(incidentId)
    override suspend fun resolveIncident(incidentId: String) = repository.resolveIncident(incidentId)
    override suspend fun cancelIncident(incidentId: String) = repository.cancelIncident(incidentId)
    override suspend fun releaseAssignment(incidentId: String) = repository.releaseAssignment(incidentId)
    override suspend fun offerHelp(id: String, note: String) = repository.offerHelp(id, note)
    override suspend fun withdrawOffer(id: String) = repository.withdrawOffer(id)
    override suspend fun selectLead(id: String, offerId: String) = repository.selectLead(id, offerId)
    override suspend fun confirmLead(id: String) = repository.confirmLead(id)
    override suspend fun declineLead(id: String) = repository.declineLead(id)
    override suspend fun revokeLead(id: String) = repository.revokeLead(id)
    override suspend fun resolveHelp(id: String) = repository.resolveHelp(id)
    override suspend fun cancelHelp(id: String) = repository.cancelHelp(id)
}
