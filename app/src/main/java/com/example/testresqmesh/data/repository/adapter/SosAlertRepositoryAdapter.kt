package com.example.testresqmesh.data.repository.adapter

import com.example.testresqmesh.core.domain.repository.SosAlertRepository
import com.example.testresqmesh.core.model.SosActivity
import com.example.testresqmesh.core.model.SosAlert
import com.example.testresqmesh.data.mapper.toEntity
import com.example.testresqmesh.data.mapper.toModel
import com.example.testresqmesh.data.repository.SosEvent
import com.example.testresqmesh.data.repository.SosProtocol
import com.example.testresqmesh.data.repository.SosRepository
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
    override suspend fun updateLocation(id: String, lat: Double, lng: Double, accuracy: Float, captured: Long) =
        repository.updateLocation(id, lat, lng, accuracy, captured)
    override suspend fun end(id: String) = repository.end(id)
    override suspend fun silence(id: String) = repository.silence(id)
}
