package edu.cit.resqmesh.data.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(tableName = "domain_events")
data class DomainEventEntity(
    @PrimaryKey val eventId: String,
    val entityId: String,
    val entityType: String,
    val eventType: String,
    val actorId: String,
    val actorName: String,
    val logicalVersion: Long,
    val timestamp: Long,
    val payloadJson: String,
    val signature: String? = null,
    val applied: Boolean = true,
    /** Validation and projection application are separate: verified prerequisites may wait. */
    @ColumnInfo(defaultValue = "'UNVERIFIED'")
    val validationStatus: String = if (applied) EventValidationStatus.ACCEPTED else EventValidationStatus.UNVERIFIED
)

/** Persisted [DomainEventEntity.validationStatus] values. */
object EventValidationStatus {
    const val ACCEPTED = "ACCEPTED"
    const val REJECTED = "REJECTED"
    const val UNVERIFIED = "UNVERIFIED"
}
