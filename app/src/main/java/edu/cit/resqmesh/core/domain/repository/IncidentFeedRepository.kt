package edu.cit.resqmesh.core.domain.repository

import edu.cit.resqmesh.core.model.DomainEvent
import edu.cit.resqmesh.core.model.Incident
import edu.cit.resqmesh.core.model.IncidentOffer
import kotlinx.coroutines.flow.Flow

/** Incident observation and commands. Commands return false when the change was not applied. */
@Suppress("TooManyFunctions")
interface IncidentFeedRepository {
    fun getActiveIncidents(): Flow<List<Incident>>
    fun getAllIncidents(): Flow<List<Incident>>
    fun observeIncidentById(id: String): Flow<Incident?>
    fun observeEventsForIncident(id: String): Flow<List<DomainEvent>>

    /** Empty when the help workflow is unavailable. */
    fun observeOffersForIncident(id: String): Flow<List<IncidentOffer>>

    /** Empty when the help workflow is unavailable. */
    fun observeAllActiveOffers(): Flow<List<IncidentOffer>>
    fun localIncidentSigningKey(): String?

    @Suppress("LongParameterList")
    suspend fun createIncident(
        incidentType: String,
        severity: String,
        description: String,
        areaDescription: String,
        latitude: Double? = null,
        longitude: Double? = null,
        locationCapturedAt: Long? = null,
        locationAccuracyMeters: Float? = null,
        title: String = ""
    ): Incident
    suspend fun acknowledgeIncident(incidentId: String): Boolean
    suspend fun assignIncident(incidentId: String): Boolean
    suspend fun startResponding(incidentId: String): Boolean
    suspend fun resolveIncident(incidentId: String): Boolean
    suspend fun cancelIncident(incidentId: String): Boolean
    suspend fun releaseAssignment(incidentId: String): Boolean
    suspend fun offerHelp(id: String, note: String): Boolean
    suspend fun withdrawOffer(id: String): Boolean
    suspend fun selectLead(id: String, offerId: String): Boolean
    suspend fun confirmLead(id: String): Boolean
    suspend fun declineLead(id: String): Boolean
    suspend fun revokeLead(id: String): Boolean
    suspend fun resolveHelp(id: String): Boolean
    suspend fun cancelHelp(id: String): Boolean
}
