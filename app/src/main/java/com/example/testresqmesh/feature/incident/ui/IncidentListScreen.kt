package com.example.testresqmesh.feature.incident.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.data.local.entity.IncidentEntity
import com.example.testresqmesh.feature.incident.ui.components.CreateIncidentSheet
import com.example.testresqmesh.feature.incident.ui.components.IncidentDetailSheet
import com.example.testresqmesh.feature.incident.ui.components.TacticalIncidentCard
import com.example.testresqmesh.feature.radar.ui.NodeKind
import com.example.testresqmesh.feature.radar.ui.classifyRadarNodes
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.ui.state.RadarUiState
import com.example.testresqmesh.feature.incident.viewmodel.IncidentMetrics
import com.example.testresqmesh.feature.incident.viewmodel.IncidentScope
import com.example.testresqmesh.feature.incident.viewmodel.IncidentViewModel
import com.example.testresqmesh.feature.incident.viewmodel.TriageQuickFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentListScreen(
    viewModel: IncidentViewModel,
    onBack: () -> Unit,
    onViewLocation: (Double, Double, String, String) -> Unit,
    radarState: RadarUiState = RadarUiState()
) {
    val filteredIncidents by viewModel.filteredIncidents.collectAsState()
    val metrics by viewModel.metrics.collectAsState()
    val currentScope by viewModel.scope.collectAsState()
    val currentTriage by viewModel.triageFilter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedTypeFilter by viewModel.selectedTypeFilter.collectAsState()

    val selectedIncident by viewModel.selectedIncident.collectAsState()
    val events by viewModel.incidentEvents.collectAsState()
    val offers by viewModel.offers.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val actionBusy by viewModel.actionBusy.collectAsState()
    val localUser by viewModel.localUser.collectAsState()
    val offersByIncident by viewModel.offersByIncident.collectAsState()
    val myOfferedIncidentIds by viewModel.myOfferedIncidentIds.collectAsState()

    var showCreateSheet by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }
    var isCreating by remember { mutableStateOf(false) }
    var isSearchVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                // 1. Top App Bar
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Emergency Incidents", fontWeight = FontWeight.Bold)
                            if (metrics.totalActive > 0) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (metrics.criticalCount > 0) ResQTheme.colors.sos else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${metrics.totalActive}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Black,
                                                fontSize = 11.sp
                                            ),
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchVisible = !isSearchVisible }) {
                            Icon(
                                imageVector = if (isSearchVisible) Icons.Default.Close else Icons.Outlined.Search,
                                contentDescription = "Search",
                                tint = if (searchQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                )

                // 2. Expandable Search Bar
                AnimatedVisibility(
                    visible = isSearchVisible,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Search landmark, reporter, or details…") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.Large, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // 3. Primary Segment Switcher (LIVE OPERATIONS vs ARCHIVE)
                PrimaryTabRow(
                    selectedTabIndex = currentScope.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {}
                ) {
                    Tab(
                        selected = currentScope == IncidentScope.LIVE_OPERATIONS,
                        onClick = { viewModel.setScope(IncidentScope.LIVE_OPERATIONS) },
                        text = {
                            Text(
                                "Live Operations (${metrics.totalActive})",
                                fontSize = 13.sp,
                                fontWeight = if (currentScope == IncidentScope.LIVE_OPERATIONS) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                    Tab(
                        selected = currentScope == IncidentScope.ARCHIVE,
                        onClick = { viewModel.setScope(IncidentScope.ARCHIVE) },
                        text = {
                            Text(
                                "Archive / History",
                                fontSize = 13.sp,
                                fontWeight = if (currentScope == IncidentScope.ARCHIVE) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }

                // 4. Smart Triage Pill Bar (Horizontal scroll of essential quick-filters)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.Large, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val hasActiveFilters = currentTriage != TriageQuickFilter.ALL || selectedTypeFilter != null || searchQuery.isNotEmpty()

                    if (hasActiveFilters) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.clearFilters() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp))
                                Text("Reset", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    if (!isSearchVisible && searchQuery.isNotEmpty()) {
                        FilterChip(selected = true, onClick = { isSearchVisible = true },
                            label = { Text("Search: $searchQuery", maxLines = 1) })
                    }

                    // Triage Quick-Filter Pills (Active Operations only)
                    if (currentScope == IncidentScope.LIVE_OPERATIONS) {
                        // All Live Pill
                        FilterChip(
                            selected = currentTriage == TriageQuickFilter.ALL,
                            onClick = { viewModel.setTriageFilter(TriageQuickFilter.ALL) },
                            label = { Text("All (${metrics.totalActive})", fontSize = 12.sp) }
                        )

                        // Needs Triage Pill
                        FilterChip(
                            selected = currentTriage == TriageQuickFilter.NEEDS_TRIAGE,
                            onClick = { viewModel.setTriageFilter(TriageQuickFilter.NEEDS_TRIAGE) },
                            label = { Text("⚠️ Needs Triage (${metrics.needsTriageCount})", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ResQTheme.colors.warningContainer,
                                selectedLabelColor = ResQTheme.colors.onWarningContainer
                            )
                        )

                        // Needs Helpers (0 Offers) Pill
                        FilterChip(
                            selected = currentTriage == TriageQuickFilter.UNASSISTED_ONLY,
                            onClick = { viewModel.setTriageFilter(TriageQuickFilter.UNASSISTED_ONLY) },
                            label = { Text("🤝 Needs Helpers (${metrics.unassistedCount})", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ResQTheme.colors.warningContainer,
                                selectedLabelColor = ResQTheme.colors.onWarningContainer
                            )
                        )

                        // Critical Only Pill
                        FilterChip(
                            selected = currentTriage == TriageQuickFilter.CRITICAL_ONLY,
                            onClick = { viewModel.setTriageFilter(TriageQuickFilter.CRITICAL_ONLY) },
                            label = { Text("🚨 Critical (${metrics.criticalCount})", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ResQTheme.colors.sosContainer,
                                selectedLabelColor = ResQTheme.colors.onSosContainer
                            )
                        )

                        // My Tasks Pill
                        FilterChip(
                            selected = currentTriage == TriageQuickFilter.MY_TASKS,
                            onClick = { viewModel.setTriageFilter(TriageQuickFilter.MY_TASKS) },
                            label = { Text("👤 My Tasks (${metrics.myTasksCount})", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }

                    // Emergency Type Filter Chips
                    val types = listOf("Medical", "Trapped", "Fire", "Injury", "Flood", "Other")
                    types.forEach { t ->
                        val isSelected = selectedTypeFilter == t
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setTypeFilter(t) },
                            label = { Text(t, fontSize = 12.sp) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateSheet = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Report Incident", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = Color.White
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (filteredIncidents.isEmpty()) {
                IncidentEmptyState(
                    scope = currentScope,
                    triageFilter = currentTriage,
                    searchQuery = searchQuery,
                    hasTypeFilter = selectedTypeFilter != null,
                    onReport = { showCreateSheet = true },
                    onResetFilters = { viewModel.clearFilters() }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(
                        start = Spacing.Large,
                        end = Spacing.Large,
                        top = Spacing.Small,
                        bottom = 100.dp
                    )
                ) {
                    items(filteredIncidents, key = { it.incidentId }) { incident ->
                        val isMine = localUser?.userId != null && localUser!!.userId == incident.creatorId
                        val isAssignedToMe = localUser?.userId != null && localUser!!.userId == incident.primaryResponderId
                        val incidentOffers = offersByIncident[incident.incidentId].orEmpty()
                        val activeOffersCount = incidentOffers.count { !it.withdrawn }
                        val hasMyOffer = myOfferedIncidentIds.contains(incident.incidentId)

                        TacticalIncidentCard(
                            incident = incident,
                            isMine = isMine,
                            isAssignedToMe = isAssignedToMe,
                            activeOffersCount = activeOffersCount,
                            hasMyOffer = hasMyOffer,
                            onClick = { viewModel.selectIncident(incident) },
                            onAcknowledge = if (incident.workflowVersion == 1) { { viewModel.acknowledge(incident.incidentId) } } else null,
                            onAssign = if (incident.workflowVersion == 1) { { viewModel.assignToMe(incident.incidentId) } } else null,
                            onStartResponse = if (incident.workflowVersion == 1) { { viewModel.startResponse(incident.incidentId) } } else null,
                            onViewLocation = if (incident.latitude != null && incident.longitude != null) {
                                { lat, lng ->
                                    onViewLocation(lat, lng, incident.creatorName, incident.description)
                                }
                            } else null
                        )
                    }
                }
            }
        }

        // Incident Creation Sheet
        if (showCreateSheet) {
            CreateIncidentSheet(
                onDismiss = { showCreateSheet = false },
                submissionError = createError,
                isSubmitting = isCreating,
                onSubmit = { type, severity, desc, area, lat, lng, time, acc ->
                    isCreating = true
                    createError = null
                    viewModel.createIncident(type, severity, desc, area, lat, lng, time, acc,
                        onSuccess = { isCreating = false; showCreateSheet = false },
                        onFailure = { isCreating = false; createError = "Could not save request. Your draft is still here." })
                }
            )
        }

        // Incident Detail Sheet
        selectedIncident?.let { inc ->
            val leadOffer = offers.firstOrNull { it.offerId == inc.selectionOfferId }
            val leadNodeId = leadOffer?.helperNodeId ?: inc.primaryResponderId
            val isLeadMe = (leadNodeId != null && leadNodeId == localUser?.deviceId) ||
                (inc.selectedHelperKey != null && inc.selectedHelperKey == viewModel.localSigningKey) ||
                (inc.primaryResponderId != null && inc.primaryResponderId == localUser?.userId)
            val leadNode = if (isLeadMe) null else classifyRadarNodes(radarState).firstOrNull {
                leadNodeId != null && NodeIdentity.idOf(it.name) == leadNodeId
            }
            val leadReachability = when {
                isLeadMe -> "Local Device"
                leadNode?.kind == NodeKind.DIRECT -> "Direct"
                leadNode?.kind in setOf(NodeKind.RELAY, NodeKind.HOPPED) -> "Relayed"
                leadNode?.kind in setOf(NodeKind.UNRESPONSIVE, NodeKind.HANDSHAKING, NodeKind.SYNCING) -> "Checking"
                else -> "Unreachable"
            }
            IncidentDetailSheet(
                incident = inc,
                events = events,
                localUserId = localUser?.userId,
                offers = offers,
                localSigningKey = viewModel.localSigningKey,
                leadReachability = leadReachability,
                actionBusy = actionBusy,
                actionMessage = actionMessage,
                onOffer = { viewModel.offerHelp(inc.incidentId, it) },
                onWithdrawOffer = { viewModel.withdrawOffer(inc.incidentId) },
                onSelectLead = { viewModel.selectLead(inc.incidentId, it) },
                onConfirmLead = { viewModel.confirmLead(inc.incidentId) },
                onDeclineLead = { viewModel.declineLead(inc.incidentId) },
                onRevokeLead = { viewModel.revokeLead(inc.incidentId) },
                onDismiss = { viewModel.selectIncident(null) },
                onAcknowledge = { viewModel.acknowledge(inc.incidentId) },
                onAssign = { viewModel.assignToMe(inc.incidentId) },
                onStartResponse = { viewModel.startResponse(inc.incidentId) },
                onResolve = { if (inc.workflowVersion == 2) viewModel.resolveHelp(inc.incidentId) else viewModel.resolve(inc.incidentId) },
                onCancel = { if (inc.workflowVersion == 2) viewModel.cancelHelp(inc.incidentId) else viewModel.cancel(inc.incidentId) },
                onReleaseAssignment = { viewModel.releaseAssignment(inc.incidentId) },
                onViewLocation = onViewLocation
            )
        }
    }
}

@Composable
private fun IncidentEmptyState(
    scope: IncidentScope,
    triageFilter: TriageQuickFilter,
    searchQuery: String,
    hasTypeFilter: Boolean,
    onReport: () -> Unit,
    onResetFilters: () -> Unit
) {
    val hasFilters = triageFilter != TriageQuickFilter.ALL || hasTypeFilter || searchQuery.isNotEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.Large),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when {
                            hasFilters -> Icons.Outlined.FilterAltOff
                            scope == IncidentScope.ARCHIVE -> Icons.Outlined.Inventory2
                            triageFilter == TriageQuickFilter.NEEDS_TRIAGE -> Icons.Outlined.DoneAll
                            triageFilter == TriageQuickFilter.UNASSISTED_ONLY -> Icons.Outlined.Handshake
                            triageFilter == TriageQuickFilter.MY_TASKS -> Icons.Outlined.AssignmentLate
                            else -> Icons.Outlined.Shield
                        },
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = when {
                        searchQuery.isNotEmpty() -> "No matching incidents"
                        triageFilter == TriageQuickFilter.UNASSISTED_ONLY -> "All Incidents Have Helpers"
                        hasFilters -> "No incidents match filters"
                        scope == IncidentScope.ARCHIVE -> "No Incident History"
                        triageFilter == TriageQuickFilter.NEEDS_TRIAGE -> "Triage Queue Clear"
                        triageFilter == TriageQuickFilter.MY_TASKS -> "No Tasks Assigned"
                        else -> "No Active Incidents Here"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        searchQuery.isNotEmpty() -> "Try searching for a different landmark, caller, or description."
                        triageFilter == TriageQuickFilter.UNASSISTED_ONLY -> "Every active emergency currently has at least one volunteer offer recorded."
                        hasFilters -> "Adjust or reset your active filters to view all emergencies."
                        scope == IncidentScope.ARCHIVE -> "Resolved and cancelled emergency records will be archived here."
                        triageFilter == TriageQuickFilter.NEEDS_TRIAGE -> "All reported emergencies have been claimed or responded to."
                        triageFilter == TriageQuickFilter.MY_TASKS -> "Claim an open emergency from Live Operations to begin response."
                        else -> "No active incidents recorded on this phone. Tap 'Report Incident' if help is needed."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            if (hasFilters) {
                OutlinedButton(onClick = onResetFilters) {
                    Text("Clear Active Filters")
                }
            } else if (scope == IncidentScope.LIVE_OPERATIONS) {
                Button(
                    onClick = onReport,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Report Incident")
                }
            }
        }
    }
}
