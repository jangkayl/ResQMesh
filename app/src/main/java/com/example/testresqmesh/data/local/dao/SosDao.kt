package com.example.testresqmesh.data.local.dao

import androidx.room.*
import com.example.testresqmesh.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SosDao {
    @Query("SELECT * FROM sos_alerts ORDER BY createdAt DESC") fun observeAlerts(): Flow<List<SosAlertEntity>>
    @Query("SELECT * FROM sos_alerts ORDER BY sosId") suspend fun alerts(): List<SosAlertEntity>
    @Query("SELECT * FROM sos_alerts WHERE sosId = :id") suspend fun alert(id: String): SosAlertEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAlert(alert: SosAlertEntity): Long
    @Query("SELECT * FROM sos_events WHERE eventId = :id") suspend fun event(id: String): SosEventEntity?
    @Query("SELECT * FROM sos_events WHERE sosId = :id ORDER BY revision") fun observeEvents(id: String): Flow<List<SosEventEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun putEvent(event: SosEventEntity): Long
    @Query("SELECT * FROM sos_events WHERE pending = 1 ORDER BY revision DESC") suspend fun pending(): List<SosEventEntity>
    @Query("UPDATE sos_events SET pending = 0 WHERE eventId = :id") suspend fun accepted(id: String): Int
    @Query("UPDATE sos_events SET pending = 1 WHERE eventId = :id") suspend fun retry(id: String): Int
    @Query("UPDATE sos_events SET pending = 0 WHERE sosId = :id AND revision < :revision") suspend fun supersede(id: String, revision: Long): Int
    @Query("UPDATE sos_alerts SET locallySilenced = 1 WHERE sosId = :id") suspend fun silence(id: String): Int
    @Query("UPDATE sos_alerts SET transmission = :state WHERE sosId = :id AND revision = :revision")
    suspend fun transmission(id: String, revision: Long, state: String): Int
}

@Dao
abstract class ConversationStateDao {
    @Query("SELECT * FROM conversation_state") abstract fun observe(): Flow<List<ConversationStateEntity>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun ensure(state: ConversationStateEntity): Long
    @Query("UPDATE conversation_state SET draft = :draft WHERE conversationId = :id")
    abstract suspend fun updateDraft(id: String, draft: String): Int
    @Query("UPDATE conversation_state SET lastReadAt = :time WHERE conversationId = :id")
    abstract suspend fun updateRead(id: String, time: Long): Int
    @Transaction open suspend fun draft(id: String, draft: String): Int { ensure(ConversationStateEntity(id)); return updateDraft(id, draft) }
    @Transaction open suspend fun read(id: String, time: Long): Int { ensure(ConversationStateEntity(id)); return updateRead(id, time) }
}
