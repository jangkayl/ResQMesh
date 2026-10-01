package com.example.testresqmesh.data.repository

import com.example.testresqmesh.data.local.entity.IncidentEntity

/** Display names are historical labels and never confer incident authority. */
object IncidentOwnership {
    fun isReporter(incident: IncidentEntity, userId: String?, signingKey: String?): Boolean =
        userId != null && userId == incident.creatorId &&
            (incident.workflowVersion != 2 || !signingKey.isNullOrBlank() && signingKey == incident.reporterSigningKey)
}
