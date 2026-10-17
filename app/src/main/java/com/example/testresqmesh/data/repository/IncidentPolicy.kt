package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.domain.incident.IncidentRules
import com.example.testresqmesh.data.local.entity.IncidentEntity

/**
 * The single authorization and transition authority for incident events.  UI affordances may
 * mirror these rules, but callers must always use this policy before mutating Room state.
 * Rules live in [IncidentRules]; this wrapper converts entity fields.
 */
object IncidentPolicy {
    fun mayPerform(current: IncidentEntity?, eventType: String, actorId: String): Boolean =
        IncidentRules.mayPerform(
            status = current?.status,
            creatorId = current?.creatorId,
            primaryResponderId = current?.primaryResponderId,
            eventType = eventType,
            actorId = actorId
        )

    fun nextState(eventType: String): String? = IncidentRules.nextState(eventType)
}
