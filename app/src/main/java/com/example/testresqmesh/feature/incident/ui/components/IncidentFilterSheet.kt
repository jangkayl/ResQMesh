package com.example.testresqmesh.feature.incident.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    onApply: (IncidentFilterState) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(currentFilters) { mutableStateOf(currentFilters) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Large)
                .padding(bottom = Spacing.Large)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filter incidents",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                TextButton(
                    onClick = { draft = IncidentFilterState() },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Clear")
                }
            }

            // Emergency Type
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Emergency type",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val types = listOf("Medical", "Fire", "Search & Rescue", "Infrastructure", "Security", "Other")
                    IncidentFilterChip(
                        selected = draft.emergencyType == null,
                        onClick = { draft = draft.copy(emergencyType = null) },
                        label = { Text("Any type") }
                    )
                    types.forEach { type ->
                        IncidentFilterChip(
                            selected = draft.emergencyType.equals(type, ignoreCase = true),
                            onClick = {
                                draft = draft.copy(
                                    emergencyType = if (draft.emergencyType.equals(type, ignoreCase = true)) null else type
                                )
                            },
                            label = { Text(type) }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Urgency
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Urgency",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val urgencies = listOf("Critical", "Serious", "Moderate")
                    IncidentFilterChip(
                        selected = draft.urgency == null,
                        onClick = { draft = draft.copy(urgency = null) },
                        label = { Text("Any urgency") }
                    )
                    urgencies.forEach { urgency ->
                        IncidentFilterChip(
                            selected = draft.urgency.equals(urgency, ignoreCase = true),
                            onClick = {
                                draft = draft.copy(
                                    urgency = if (draft.urgency.equals(urgency, ignoreCase = true)) null else urgency
                                )
                            },
                            label = { Text(urgency) }
                        )
                    }
                }
            }

            // Assistance (active destinations only)
            if (destination != IncidentDestination.HISTORY) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Assistance needed",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistanceFilter.entries.forEach { filter ->
                            IncidentFilterChip(
                                selected = draft.assistance == filter,
                                onClick = { draft = draft.copy(assistance = filter) },
                                label = { Text(filter.label) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Apply button
            Button(
                onClick = {
                    onApply(draft)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
            ) {
                Text("Apply filters", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
