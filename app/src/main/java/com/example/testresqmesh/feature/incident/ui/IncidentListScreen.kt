package com.example.testresqmesh.feature.incident.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.feature.incident.viewmodel.IncidentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentListScreen(
    viewModel: IncidentViewModel,
    onBack: () -> Unit,
    onViewLocation: (Double, Double, String, String) -> Unit
) {
    val activeIncidents by viewModel.activeIncidents.collectAsState()
    val allIncidents by viewModel.allIncidents.collectAsState()
    val selectedIncident by viewModel.selectedIncident.collectAsState()
    val events by viewModel.incidentEvents.collectAsState()
    val localUser by viewModel.localUser.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }

    val displayedIncidents = if (showAll) allIncidents else activeIncidents

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emergency Incidents", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { showAll = !showAll }) {
                        Text(if (showAll) "Active Only" else "Show All")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Incident") },
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = Color.White
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.Large)
        ) {
            if (displayedIncidents.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (showAll) "No incidents recorded" else "No active incidents",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(Spacing.Small))
                        Text(
                            text = "Tap 'New Incident' to create a trackable SOS",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
                    contentPadding = PaddingValues(vertical = Spacing.Medium)
                ) {
                    items(displayedIncidents, key = { it.incidentId }) { incident ->
                        IncidentCard(
                            incident = incident,
                            onClick = { viewModel.selectIncident(incident) }
                        )
                    }
                }
            }
        }

        if (showCreateDialog) {
            CreateIncidentDialog(
                onDismiss = { showCreateDialog = false },
                onSubmit = { type, severity, desc, area, latitude, longitude, capturedAt, accuracy ->
                    viewModel.createIncident(type, severity, desc, area, latitude, longitude, capturedAt, accuracy)
                }
            )
        }

        selectedIncident?.let { inc ->
            IncidentDetailDialog(
                incident = inc,
                events = events,
                onDismiss = { viewModel.selectIncident(null) },
                onAcknowledge = { viewModel.acknowledge(inc.incidentId) },
                onAssign = { viewModel.assignToMe(inc.incidentId) },
                onStartResponse = { viewModel.startResponse(inc.incidentId) },
                onResolve = { viewModel.resolve(inc.incidentId) },
                onCancel = { viewModel.cancel(inc.incidentId) },
                onReleaseAssignment = { viewModel.releaseAssignment(inc.incidentId) },
                localUserId = localUser?.userId,
                onViewLocation = onViewLocation
            )
        }
    }
}
