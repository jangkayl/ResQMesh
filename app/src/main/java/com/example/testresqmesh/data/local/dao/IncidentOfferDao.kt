package com.example.testresqmesh.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.example.testresqmesh.data.local.entity.IncidentOfferEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IncidentOfferDao {
    @Query("SELECT * FROM incident_offers WHERE incidentId = :incidentId ORDER BY updatedAt DESC, offerId")
    fun observeForIncident(incidentId: String): Flow<List<IncidentOfferEntity>>

    @Query("SELECT * FROM incident_offers WHERE withdrawn = 0 ORDER BY updatedAt DESC")
    fun observeAllActiveOffers(): Flow<List<IncidentOfferEntity>>

    @Query("SELECT * FROM incident_offers WHERE incidentId = :incidentId ORDER BY updatedAt DESC, offerId")
    suspend fun getForIncident(incidentId: String): List<IncidentOfferEntity>

    @Query("SELECT * FROM incident_offers WHERE offerId = :offerId LIMIT 1")
    suspend fun getById(offerId: String): IncidentOfferEntity?

    @Query("SELECT * FROM incident_offers WHERE incidentId = :incidentId AND helperKey = :helperKey LIMIT 1")
    suspend fun getByHelper(incidentId: String, helperKey: String): IncidentOfferEntity?

    suspend fun upsert(offer: IncidentOfferEntity): Long = insertOffer(
        offer.offerId, offer.incidentId, offer.helperKey, offer.helperNodeId,
        offer.helperName, offer.note, offer.revision, offer.withdrawn, offer.updatedAt
    )

    @Query("INSERT OR REPLACE INTO incident_offers (offerId, incidentId, helperKey, helperNodeId, helperName, note, revision, withdrawn, updatedAt) VALUES (:offerId, :incidentId, :helperKey, :helperNodeId, :helperName, :note, :revision, :withdrawn, :updatedAt)")
    suspend fun insertOffer(offerId: String, incidentId: String, helperKey: String,
        helperNodeId: String, helperName: String, note: String, revision: Long,
        withdrawn: Boolean, updatedAt: Long): Long
}
