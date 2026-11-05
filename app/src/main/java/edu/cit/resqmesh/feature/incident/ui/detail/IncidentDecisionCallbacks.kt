package edu.cit.resqmesh.feature.incident.ui.detail

/** The incident decisions a confirmation sheet can commit. */
internal class IncidentDecisionCallbacks(
    val onSelectLead: (String) -> Unit,
    val onRevokeLead: () -> Unit,
    val onWithdrawOffer: () -> Unit,
    val onDeclineLead: () -> Unit,
    val onResolve: () -> Unit,
    val onCancel: () -> Unit
)
