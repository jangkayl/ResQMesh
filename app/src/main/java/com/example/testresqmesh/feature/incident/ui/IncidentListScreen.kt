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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.ui.theme.SafetyOrange
import com.example.testresqmesh.core.ui.theme.SignalAmber
import com.example.testresqmesh.core.ui.theme.SignalGreen
import com.example.testresqmesh.core.ui.theme.SignalRed
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.theme.TacticalBlack
import com.example.testresqmesh.core.ui.theme.TacticalCarbon
import com.example.testresqmesh.feature.incident.ui.components.CreateIncidentSheet
import com.example.testresqmesh.feature.incident.ui.components.IncidentDetailSheet
import com.example.testresqmesh.feature.incident.ui.components.IncidentFilterSheet
import com.example.testresqmesh.feature.incident.ui.components.TacticalIncidentCard
import com.example.testresqmesh.feature.incident.viewmodel.AssistanceFilter
import com.example.testresqmesh.feature.incident.viewmodel.IncidentDestination
import com.example.testresqmesh.feature.incident.viewmodel.IncidentFilterState
import com.example.testresqmesh.feature.incident.viewmodel.IncidentViewModel
import com.example.testresqmesh.data.repository.IncidentOwnership
import com.example.testresqmesh.core.ui.model.NodeKind
import com.example.testresqmesh.core.ui.peers.classifyRadarNodes
import com.example.testresqmesh.core.ui.model.RadarUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentListScreen(
    viewModel: IncidentViewModel,
    onBack: () -> Unit,
    onViewLocation: (Double, Double, String, String) -> Unit,
    radarState: RadarUiState = RadarUiState(),
    onDirectChat: (String) -> Unit = {}
) {
    val filteredIncidents by viewModel.filteredIncidents.collectAsState()
    val metrics by viewModel.metrics.collectAsState()
    val currentDestination by viewModel.destination.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val appliedFilterCount by viewModel.appliedFilterCount.collectAsState()

    val selectedIncident by viewModel.selectedIncident.collectAsState()
    val events by viewModel.incidentEvents.collectAsState()
    val offers by viewModel.offers.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val actionBusy by viewModel.actionBusy.collectAsState()
    val identity by viewModel.identityState.collectAsState()
    val localUser = identity.user
    val offersByIncident by viewModel.offersByIncident.collectAsState()
    val myOfferedIncidentIds by viewModel.myOfferedIncidentIds.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    var isSearchVisible by rememberSaveable { mutableStateOf(searchQuery.isNotEmpty()) }
    var showFilterSheet by remember { mutableStateOf(false) }

    var showCreateSheet by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }
    var isCreating by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val activeNodeCount = remember(radarState) {
        classifyRadarNodes(radarState).count {
            it.kind in setOf(NodeKind.DIRECT, NodeKind.RELAY, NodeKind.HOPPED)
        }
    }

    val isSheetOpen = showCreateSheet || selectedIncident != null

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .fillMaxWidth()
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Emergency incidents",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.semantics { heading() }
                            )
                            Text(
                                text = "See requests and offer help.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (activeNodeCount > 0)
                                SignalGreen.copy(alpha = 0.15f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(
                                0.8.dp,
                                if (activeNodeCount > 0) SignalGreen.copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.padding(end = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(
                                            if (activeNodeCount > 0) SignalGreen else SignalAmber.copy(alpha = 0.8f),
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        )
                                )
                                if (activeNodeCount > 0) {
                                    Text(
                                        text = "$activeNodeCount",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = SignalGreen
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                isSearchVisible = !isSearchVisible
                                if (!isSearchVisible && searchQuery.isNotEmpty()) {
                                    viewModel.setSearchQuery("")
                                }
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = if (isSearchVisible) Icons.Default.Close else Icons.Outlined.Search,
                                contentDescription = if (isSearchVisible) "Close search" else "Search",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        BadgedBox(
                            badge = {
                                if (appliedFilterCount > 0) {
                                    Badge { Text("$appliedFilterCount") }
                                }
                            }
                        ) {
                            IconButton(
                                onClick = { showFilterSheet = true },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.FilterList,
                                    contentDescription = "Advanced filters",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                if (identity.loading || identity.error != null) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.Large), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (identity.loading) "Checking your identity…" else identity.error.orEmpty(),
                            modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface)
                        if (!identity.loading) TextButton(onClick = viewModel::retryIdentity) { Text("Retry identity") }
                    }
                }

                // Compact Tactical Search input
                AnimatedVisibility(
                    visible = isSearchVisible || searchQuery.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(
                            0.8.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.Large, vertical = 3.dp)
                            .height(38.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search title, landmark, reporter, or helper…",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 12.5.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                androidx.compose.foundation.text.BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.setSearchQuery(it) },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.fillMaxWidth().semantics {
                                        contentDescription = "Search incidents"
                                    }
                                )
                            }

                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.setSearchQuery("") },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear search",
                                        modifier = Modifier.size(15.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Primary Destination Segmented Pill Bar (Matching Concept Mockup)
                val isNight = MaterialTheme.colorScheme.background == TacticalBlack || MaterialTheme.colorScheme.surface == TacticalCarbon
                val activeTabBg = if (isNight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary
                val activeTabTextColor = if (isNight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.Large, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IncidentDestination.values().forEach { destination ->
                            val isSelected = currentDestination == destination
                            val count = when (destination) {
                                IncidentDestination.ACTIVE -> metrics.totalActive
                                IncidentDestination.MY_ACTIVITY -> metrics.myActivityCount
                                IncidentDestination.HISTORY -> metrics.historyCount
                            }
                            val label = when (destination) {
                                IncidentDestination.ACTIVE -> "Active ($count)"
                                IncidentDestination.MY_ACTIVITY -> "My activity ($count)"
                                IncidentDestination.HISTORY -> "History ($count)"
                            }

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) activeTabBg else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { viewModel.setDestination(destination) }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 7.dp, horizontal = 2.dp)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (isSelected) activeTabTextColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Quick Triage Filter Row (tightened horizontal gaps)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.Large, vertical = 3.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isAllSelected = filters.emergencyType == null && filters.urgency == null && filters.assistance == AssistanceFilter.ANY && searchQuery.isEmpty()
                    val isCriticalSelected = filters.urgency.equals("Critical", ignoreCase = true)
                    val isMedicalSelected = filters.emergencyType.equals("Medical", ignoreCase = true)
                    val isNeedsHelpSelected = filters.assistance == AssistanceFilter.LOOKING_FOR_HELP

                    QuickFilterChip(
                        label = "All",
                        isSelected = isAllSelected,
                        onClick = { viewModel.clearFilters() }
                    )

                    QuickFilterChip(
                        label = "Critical only",
                        isSelected = isCriticalSelected,
                        accentColor = SignalRed,
                        onClick = {
                            viewModel.applyFilters(
                                filters.copy(urgency = if (isCriticalSelected) null else "Critical")
                            )
                        }
                    )

                    QuickFilterChip(
                        label = "Medical",
                        isSelected = isMedicalSelected,
                        accentColor = SafetyOrange,
                        onClick = {
                            viewModel.applyFilters(
                                filters.copy(emergencyType = if (isMedicalSelected) null else "Medical")
                            )
                        }
                    )

                    if (currentDestination != IncidentDestination.HISTORY) {
                        QuickFilterChip(
                            label = "Needs Helper",
                            isSelected = isNeedsHelpSelected,
                            accentColor = Color(0xFFFF9500),
                            onClick = {
                                viewModel.applyFilters(
                                    filters.copy(
                                        assistance = if (isNeedsHelpSelected) AssistanceFilter.ANY else AssistanceFilter.LOOKING_FOR_HELP
                                    )
                                )
                            }
                        )
                    }

                    QuickFilterChip(
                        label = if (appliedFilterCount > 0) "Filters ($appliedFilterCount)" else "Filters…",
                        isSelected = showFilterSheet || appliedFilterCount > 0,
                        onClick = { showFilterSheet = true }
                    )
                }

                // Compact active filter banner / reset row
                if (appliedFilterCount > 0 || searchQuery.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.Large, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val filterText = when {
                            appliedFilterCount > 0 && searchQuery.isNotEmpty() ->
                                "$appliedFilterCount filter(s) • \"$searchQuery\""
                            appliedFilterCount > 0 ->
                                "$appliedFilterCount filter(s) active"
                            else ->
                                "\"$searchQuery\""
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .background(MaterialTheme.colorScheme.primary, shape = androidx.compose.foundation.shape.CircleShape)
                                )
                                Text(
                                    text = filterText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.clearFilters() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Clear",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!isSheetOpen) {
                ExtendedFloatingActionButton(
                    onClick = { showCreateSheet = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Report incident", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
                    containerColor = SafetyOrange,
                    contentColor = Color.White
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 680.dp)
        ) {
            if (filteredIncidents.isEmpty()) {
                val isFiltered = appliedFilterCount > 0 || searchQuery.isNotEmpty()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.Large),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(Spacing.Large)
                    ) {
                        Text(
                            text = when {
                                currentDestination == IncidentDestination.MY_ACTIVITY && identity.loading -> "Checking your identity…"
                                currentDestination == IncidentDestination.MY_ACTIVITY && identity.error != null -> "Identity unavailable"
                                isFiltered -> "No matching incidents"
                                currentDestination == IncidentDestination.ACTIVE -> "No active incidents"
                                currentDestination == IncidentDestination.MY_ACTIVITY -> "No activity yet"
                                else -> "No emergency history"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = when {
                                currentDestination == IncidentDestination.MY_ACTIVITY && identity.loading ->
                                    "Your reports and offers will appear when your identity is ready."
                                currentDestination == IncidentDestination.MY_ACTIVITY && identity.error != null ->
                                    "Use Retry identity above to restore access to your activity."
                                isFiltered -> "No emergency incidents match your filters on this phone."
                                currentDestination == IncidentDestination.ACTIVE -> "No active emergency incidents on this phone."
                                currentDestination == IncidentDestination.MY_ACTIVITY -> "No emergency reports or offers involving you on this phone."
                                else -> "Resolved and cancelled emergency requests on this phone will appear here."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        if (isFiltered) {
                            OutlinedButton(onClick = { viewModel.clearFilters() }) {
                                Text("Clear filters")
                            }
                        } else if (currentDestination == IncidentDestination.ACTIVE) {
                            Button(onClick = { showCreateSheet = true }) {
                                Text("Report incident")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(
                        start = Spacing.Large,
                        end = Spacing.Large,
                        top = Spacing.Medium,
                        bottom = 100.dp
                    )
                ) {
                    items(filteredIncidents, key = { it.incidentId }) { incident ->
                        val isMine = IncidentOwnership.isReporter(incident, localUser?.userId, identity.signingKey)
                        val isAssignedToMe = localUser?.userId != null && localUser!!.userId == incident.primaryResponderId
                        val incidentOffers = offersByIncident[incident.incidentId].orEmpty()
                        val activeOffersCount = incidentOffers.count { !it.withdrawn }
                        val hasMyOffer = myOfferedIncidentIds.contains(incident.incidentId)
                        val confirmedHelperName = incident.primaryResponderName ?: incidentOffers.firstOrNull {
                            it.offerId == incident.selectionOfferId
                        }?.helperName

                        TacticalIncidentCard(
                            incident = incident,
                            isMine = isMine,
                            isAssignedToMe = isAssignedToMe,
                            activeOffersCount = activeOffersCount,
                            hasMyOffer = hasMyOffer,
                            confirmedHelperName = confirmedHelperName,
                            onClick = { viewModel.selectIncident(incident) }
                        )
                    }
                }
            }
        }
    }

    // Create Incident Form
    AnimatedVisibility(
        visible = showCreateSheet,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
            CreateIncidentSheet(
                onDismiss = { showCreateSheet = false },
                submissionError = createError,
                isSubmitting = isCreating,
                onSubmit = { title, type, severity, desc, area, lat, lng, time, acc ->
                    isCreating = true
                    createError = null
                    viewModel.createIncident(
                        title = title,
                        type = type,
                        severity = severity,
                        description = desc,
                        areaDescription = area,
                        latitude = lat,
                        longitude = lng,
                        locationCapturedAt = time,
                        locationAccuracyMeters = acc,
                        onSuccess = { created ->
                            isCreating = false
                            showCreateSheet = false
                            viewModel.selectIncident(created)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    "Saved on this phone. Sharing depends on available mesh connections."
                                )
                            }
                        },
                        onFailure = {
                            isCreating = false
                            createError = "Could not save request. Your draft is still here."
                        }
                    )
                }
            )
        }

        // Incident Detail Sheet
        AnimatedVisibility(
            visible = selectedIncident != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
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
                    isLeadMe -> "This phone"
                    leadNode?.kind == NodeKind.DIRECT -> "Direct"
                    leadNode?.kind in setOf(NodeKind.RELAY, NodeKind.HOPPED) -> "Relayed"
                    leadNode?.kind in setOf(NodeKind.UNRESPONSIVE, NodeKind.HANDSHAKING, NodeKind.SYNCING) -> "Checking"
                    else -> "Unavailable"
                }

                IncidentDetailSheet(
                    incident = inc,
                    events = events,
                    localUserId = localUser?.userId,
                    offers = offers,
                    localSigningKey = viewModel.localSigningKey,
                    identityLoading = identity.loading,
                    identityError = identity.error.takeIf { inc.workflowVersion == 2 || identity.user == null },
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
                    onResolve = {
                        if (inc.workflowVersion == 2) viewModel.resolveHelp(inc.incidentId)
                        else viewModel.resolve(inc.incidentId)
                    },
                    onCancel = {
                        if (inc.workflowVersion == 2) viewModel.cancelHelp(inc.incidentId)
                        else viewModel.cancel(inc.incidentId)
                    },
                    onReleaseAssignment = { viewModel.releaseAssignment(inc.incidentId) },
                    onViewLocation = onViewLocation,
                    onDirectChat = onDirectChat
                )
            }
        }

        if (showFilterSheet) {
            IncidentFilterSheet(
                currentFilters = filters,
                destination = currentDestination,
                onFilterChange = {
                    viewModel.applyFilters(it)
                },
                onDismiss = { showFilterSheet = false }
            )
        }
    }
}

@Composable
internal fun QuickFilterChip(
    label: String,
    isSelected: Boolean,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) accentColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) accentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = isSelected, role = Role.Button, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(accentColor, shape = androidx.compose.foundation.shape.CircleShape)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isSelected) {
                    if (accentColor == MaterialTheme.colorScheme.primary) MaterialTheme.colorScheme.primary else accentColor
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
