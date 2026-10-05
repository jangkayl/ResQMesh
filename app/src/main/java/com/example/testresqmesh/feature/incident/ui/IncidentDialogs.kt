package com.example.testresqmesh.feature.incident.ui

import com.example.testresqmesh.feature.incident.ui.components.IncidentCard as IncidentCardSection
import com.example.testresqmesh.feature.incident.ui.components.CreateIncidentDialog as CreateIncidentDialogSection
import com.example.testresqmesh.feature.incident.ui.components.IncidentDetailDialog as IncidentDetailDialogSection
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.testresqmesh.data.local.entity.DomainEventEntity
import com.example.testresqmesh.data.local.entity.IncidentEntity
import java.util.*

@Composable
fun IncidentCard(
    incident: IncidentEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = IncidentCardSection(incident, onClick, modifier)

@Composable
fun CreateIncidentDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String, Double?, Double?, Long?, Float?) -> Unit
) = CreateIncidentDialogSection(onDismiss, onSubmit)

@Composable
fun IncidentDetailDialog(
    incident: IncidentEntity,
    events: List<DomainEventEntity>,
    onDismiss: () -> Unit,
    onAcknowledge: () -> Unit,
    onAssign: () -> Unit,
    onStartResponse: () -> Unit,
    onResolve: () -> Unit,
    onCancel: () -> Unit,
    onReleaseAssignment: () -> Unit,
    localUserId: String?,
    onViewLocation: (Double, Double, String, String) -> Unit
) = IncidentDetailDialogSection(incident, events, onDismiss, onAcknowledge, onAssign, onStartResponse, onResolve, onCancel, onReleaseAssignment, localUserId, onViewLocation)
