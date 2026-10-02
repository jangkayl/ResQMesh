package com.example.testresqmesh.data.repository

import androidx.room.withTransaction
import com.example.testresqmesh.data.local.AppDatabase
import com.example.testresqmesh.data.local.entity.*
import kotlinx.coroutines.flow.Flow

interface SosStore {
    val alerts: Flow<List<SosAlertEntity>>
    fun events(id: String): Flow<List<SosEventEntity>>
    suspend fun all(): List<SosAlertEntity>
    suspend fun alert(id: String): SosAlertEntity?
    suspend fun event(id: String): SosEventEntity?
    suspend fun putAlert(alert: SosAlertEntity)
    suspend fun putEvent(event: SosEventEntity)
    suspend fun pending(): List<SosEventEntity>
    suspend fun accepted(id: String)
    suspend fun retry(id: String)
    suspend fun supersede(id: String, revision: Long)
    suspend fun silence(id: String)
    suspend fun transmission(id: String, revision: Long, state: String)
    suspend fun transaction(block: suspend () -> Unit)
}

class RoomSosStore(private val db: AppDatabase) : SosStore {
    private val dao = db.sosDao()
    override val alerts = dao.observeAlerts()
    override fun events(id: String) = dao.observeEvents(id)
    override suspend fun all() = dao.alerts()
    override suspend fun alert(id: String) = dao.alert(id)
    override suspend fun event(id: String) = dao.event(id)
    override suspend fun putAlert(alert: SosAlertEntity) { dao.putAlert(alert) }
    override suspend fun putEvent(event: SosEventEntity) { dao.putEvent(event) }
    override suspend fun pending() = dao.pending()
    override suspend fun accepted(id: String) { dao.accepted(id) }
    override suspend fun retry(id: String) { dao.retry(id) }
    override suspend fun supersede(id: String, revision: Long) { dao.supersede(id, revision) }
    override suspend fun silence(id: String) { dao.silence(id) }
    override suspend fun transmission(id: String, revision: Long, state: String) { dao.transmission(id, revision, state) }
    override suspend fun transaction(block: suspend () -> Unit) { db.withTransaction { block() } }
}

interface SosAlertSink {
    fun received(alert: SosAlertEntity)
    fun silence(id: String)
    fun ended(id: String)
    fun offline()
}
