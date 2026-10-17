package edu.cit.resqmesh.data.repository

import edu.cit.resqmesh.core.domain.incident.IncidentRules
import edu.cit.resqmesh.data.local.entity.IncidentEntity

/** Display names are historical labels and never confer incident authority. */
object IncidentOwnership {
    fun isReporter(incident: IncidentEntity, userId: String?, signingKey: String?): Boolean =
        IncidentRules.isReporter(
            creatorId = incident.creatorId,
            workflowVersion = incident.workflowVersion,
            reporterSigningKey = incident.reporterSigningKey,
            userId = userId,
            signingKey = signingKey
        )
}
