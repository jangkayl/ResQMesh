package edu.cit.resqmesh.core.domain.repository

import edu.cit.resqmesh.core.model.SosActivity
import edu.cit.resqmesh.core.model.SosAlert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** SOS alert state and commands. State flows mirror the current value from the first frame. */
interface SosAlertRepository {
    val alerts: StateFlow<List<SosAlert>>
    val ownActive: StateFlow<SosAlert?>
    val incoming: StateFlow<SosAlert?>

    /** Retained SOS revisions; a stored row that fails to decode throws, as the UI decode did. */
    fun sosActivity(id: String): Flow<List<SosActivity>>
    fun isOwnSos(alert: SosAlert): Boolean
    suspend fun create(
        type: String,
        lat: Double? = null,
        lng: Double? = null,
        accuracy: Float? = null,
        captured: Long? = null
    ): String
    suspend fun updateLocation(id: String, lat: Double, lng: Double, accuracy: Float, captured: Long)
    suspend fun end(id: String)
    suspend fun silence(id: String)
}
