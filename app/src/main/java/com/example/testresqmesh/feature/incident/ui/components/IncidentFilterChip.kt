package com.example.testresqmesh.feature.incident.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun IncidentFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    FilterChip(selected = selected, onClick = onClick, label = label, modifier = modifier, enabled = enabled,
        leadingIcon = if (selected) { { Icon(Icons.Default.Check, contentDescription = null) } } else null,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSurface
        ))
}
