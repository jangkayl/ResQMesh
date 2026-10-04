package com.example.testresqmesh.feature.incident.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.feature.incident.viewmodel.AssistanceFilter
import com.example.testresqmesh.feature.incident.viewmodel.IncidentDestination
import com.example.testresqmesh.feature.incident.viewmodel.IncidentFilterState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IncidentFilterSheet(
    currentFilters: IncidentFilterState,
    destination: IncidentDestination,
    onFilterChange: (IncidentFilterState) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasActiveFilters = currentFilters.activeCount(destination) > 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle(modifier = Modifier.padding(vertical = 4.dp)) },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Large)
                .padding(bottom = Spacing.Medium)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header: Title + Compact Clear All + Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filter incidents",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (hasActiveFilters) {
                        TextButton(
                            onClick = { onFilterChange(IncidentFilterState()) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.defaultMinSize(minHeight = 28.dp)
                        ) {
                            Text("Clear all", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 1. Emergency Type (Tight 5dp horizontal gaps, 1-tap live apply)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "EMERGENCY TYPE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val types = listOf("Medical", "Fire", "Search & Rescue", "Infrastructure", "Security", "Other")
                    IncidentFilterChip(
                        selected = currentFilters.emergencyType == null,
                        onClick = { onFilterChange(currentFilters.copy(emergencyType = null)) },
                        label = { Text("Any type", fontSize = 12.sp) }
                    )
                    types.forEach { type ->
                        val isSelected = currentFilters.emergencyType.equals(type, ignoreCase = true)
                        IncidentFilterChip(
                            selected = isSelected,
                            onClick = {
                                onFilterChange(
                                    currentFilters.copy(
                                        emergencyType = if (isSelected) null else type
                                    )
                                )
                            },
                            label = { Text(type, fontSize = 12.sp) }
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                thickness = 0.5.dp
            )

            // 2. Urgency (Tight 5dp horizontal gaps, 1-tap live apply)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "URGENCY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val urgencies = listOf("Critical", "Serious", "Moderate")
                    IncidentFilterChip(
                        selected = currentFilters.urgency == null,
                        onClick = { onFilterChange(currentFilters.copy(urgency = null)) },
                        label = { Text("Any urgency", fontSize = 12.sp) }
                    )
                    urgencies.forEach { urgency ->
                        val isSelected = currentFilters.urgency.equals(urgency, ignoreCase = true)
                        IncidentFilterChip(
                            selected = isSelected,
                            onClick = {
                                onFilterChange(
                                    currentFilters.copy(
                                        urgency = if (isSelected) null else urgency
                                    )
                                )
                            },
                            label = { Text(urgency, fontSize = 12.sp) }
                        )
                    }
                }
            }

            // 3. Assistance needed (Active destinations only)
            if (destination != IncidentDestination.HISTORY) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                    thickness = 0.5.dp
                )

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "ASSISTANCE NEEDED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AssistanceFilter.entries.forEach { filter ->
                            IncidentFilterChip(
                                selected = currentFilters.assistance == filter,
                                onClick = { onFilterChange(currentFilters.copy(assistance = filter)) },
                                label = { Text(filter.label, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

            // Done Button
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = "Done",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
