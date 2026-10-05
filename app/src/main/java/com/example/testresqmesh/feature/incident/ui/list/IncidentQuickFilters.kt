package com.example.testresqmesh.feature.incident.ui.list

import com.example.testresqmesh.feature.incident.viewmodel.IncidentFilterState
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.example.testresqmesh.core.ui.theme.SafetyOrange
import com.example.testresqmesh.core.ui.theme.SignalRed
import com.example.testresqmesh.feature.incident.viewmodel.AssistanceFilter
import com.example.testresqmesh.feature.incident.viewmodel.IncidentDestination
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun IncidentQuickFilters(
    filters: IncidentFilterState,
    searchQuery: String,
    currentDestination: IncidentDestination,
    appliedFilterCount: Int,
    showFilterSheet: Boolean,
    onClearFilters: () -> Unit,
    onOpenFilters: () -> Unit,
    onToggleCritical: (Boolean) -> Unit,
    onToggleMedical: (Boolean) -> Unit,
    onToggleNeedsHelp: (Boolean) -> Unit
) {
    val isAllSelected = filters.emergencyType == null && filters.urgency == null && filters.assistance == AssistanceFilter.ANY && searchQuery.isEmpty()
    val isCriticalSelected = filters.urgency.equals("Critical", ignoreCase = true)
    val isMedicalSelected = filters.emergencyType.equals("Medical", ignoreCase = true)
    val isNeedsHelpSelected = filters.assistance == AssistanceFilter.LOOKING_FOR_HELP

    QuickFilterChip(
        label = "All",
        isSelected = isAllSelected,
        onClick = { onClearFilters() }
    )

    QuickFilterChip(
        label = "Critical only",
        isSelected = isCriticalSelected,
        accentColor = SignalRed,
        onClick = {
            onToggleCritical(isCriticalSelected)
        }
    )

    QuickFilterChip(
        label = "Medical",
        isSelected = isMedicalSelected,
        accentColor = SafetyOrange,
        onClick = {
            onToggleMedical(isMedicalSelected)
        }
    )

    if (currentDestination != IncidentDestination.HISTORY) {
        QuickFilterChip(
            label = "Needs Helper",
            isSelected = isNeedsHelpSelected,
            accentColor = Color(0xFFFF9500),
            onClick = {
                onToggleNeedsHelp(isNeedsHelpSelected)
            }
        )
    }

    QuickFilterChip(
        label = if (appliedFilterCount > 0) "Filters ($appliedFilterCount)" else "Filters…",
        isSelected = showFilterSheet || appliedFilterCount > 0,
        onClick = { onOpenFilters() }
    )
}
