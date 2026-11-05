package edu.cit.resqmesh.feature.incident.model

import edu.cit.resqmesh.core.model.HELP_WORKFLOW_VERSION
import edu.cit.resqmesh.core.model.Incident
import edu.cit.resqmesh.core.model.IncidentState

/** Stored status strings that end an incident. Values match [IncidentState] names exactly. */
internal val TERMINAL_INCIDENT_STATUSES = setOf(IncidentState.RESOLVED.name, IncidentState.CANCELLED.name)

internal fun Incident.isTerminal(): Boolean = status in TERMINAL_INCIDENT_STATUSES

internal fun Incident.hasStatus(state: IncidentState): Boolean = status == state.name

internal fun Incident.usesHelpWorkflow(): Boolean = workflowVersion == HELP_WORKFLOW_VERSION
