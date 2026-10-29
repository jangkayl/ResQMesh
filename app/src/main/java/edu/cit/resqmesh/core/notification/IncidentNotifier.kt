package edu.cit.resqmesh.core.notification

import edu.cit.resqmesh.core.model.Incident

/** The local node's identity, supplied by the data layer. */
data class IncidentLocalIdentity(val userId: String, val deviceId: String, val signingKey: String)

/** The fields of an applied incident event that notification rules need. */
data class IncidentEventNotice(
    val eventType: String,
    val actorId: String,
    val actorName: String,
    val revision: Long,
    val local: IncidentLocalIdentity
)

/** Alerting side of applied incident events; never called for replayed history. */
interface IncidentNotifier {
    suspend fun applied(event: IncidentEventNotice, incident: Incident)
}
