package edu.cit.resqmesh.data.repository.adapter

import edu.cit.resqmesh.core.domain.repository.SosAlertRepository
import edu.cit.resqmesh.core.model.SosActivity
import edu.cit.resqmesh.core.model.SosAlert
import edu.cit.resqmesh.core.model.SosRaiseCharge
import edu.cit.resqmesh.core.model.SosRaiseResult
import edu.cit.resqmesh.data.mapper.toEntity
import edu.cit.resqmesh.data.mapper.toModel
import edu.cit.resqmesh.data.repository.SosEvent
import edu.cit.resqmesh.data.repository.SosProtocol
import edu.cit.resqmesh.data.repository.SosRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

class SosAlertRepositoryAdapter(
    private val repository: SosRepository,
    scope: CoroutineScope
) : SosAlertRepository {
    override val alerts: StateFlow<List<SosAlert>> =
        repository.alerts.mapState(scope) { list -> list.map { it.toModel() } }
    override val ownActive: StateFlow<SosAlert?> = repository.ownActive.mapState(scope) { it?.toModel() }
    override val incoming: StateFlow<SosAlert?> = repository.incoming.mapState(scope) { it?.toModel() }

    // Decode does not catch: a corrupt retained row fails exactly as the screen decode did.
    override fun sosActivity(id: String): Flow<List<SosActivity>> = repository.events(id).map { rows ->
        rows.map { row ->
            val event = SosProtocol.json.decodeFromString<SosEvent>(row.eventJson)
            SosActivity(row.eventId, row.revision, event.ended, event.updatedAt, event.latitude, event.accuracyMeters)
        }
    }

    override fun isOwnSos(alert: SosAlert): Boolean = repository.owns(alert.toEntity())
    override suspend fun create(type: String, lat: Double?, lng: Double?, accuracy: Float?, captured: Long?): String =
        repository.create(type, lat, lng, accuracy, captured)
    override suspend fun raise(
        type: String, charge: SosRaiseCharge, lat: Double?, lng: Double?, accuracy: Float?, captured: Long?
    ): SosRaiseResult = repository.raise(type, charge, lat, lng, accuracy, captured)
    override suspend fun updateLocation(id: String, lat: Double, lng: Double, accuracy: Float, captured: Long) =
        repository.updateLocation(id, lat, lng, accuracy, captured)
    override suspend fun end(id: String) = repository.end(id)
    override suspend fun silence(id: String) = repository.silence(id)
}
