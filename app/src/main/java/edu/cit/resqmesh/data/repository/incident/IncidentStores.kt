package edu.cit.resqmesh.data.repository.incident

import edu.cit.resqmesh.data.local.AppDatabase
import edu.cit.resqmesh.data.local.dao.DomainEventDao
import edu.cit.resqmesh.data.local.dao.IncidentDao
import edu.cit.resqmesh.data.local.dao.IncidentOfferDao
import edu.cit.resqmesh.data.local.entity.DomainEventEntity
import edu.cit.resqmesh.core.model.Incident

/**
 * Room access shared by the incident collaborators. [offers] and [database] are null when the
 * repository runs without Room (JVM tests); callers then skip offer writes and transactions.
 */
internal class IncidentStores(
    val incidents: IncidentDao,
    val events: DomainEventDao,
    val offers: IncidentOfferDao?,
    val database: AppDatabase?
)

/** Repository callbacks the event ingestor invokes after it decides an event's fate. */
internal class IncidentIngestHooks(
    val broadcast: (DomainEventEntity, Boolean) -> Unit,
    val legacyProjection: suspend (DomainEventEntity) -> Boolean,
    val rejectEvent: suspend (DomainEventEntity, String) -> Boolean,
    val notifyApplied: suspend (DomainEventEntity, Incident) -> Unit = { _, _ -> }
)
