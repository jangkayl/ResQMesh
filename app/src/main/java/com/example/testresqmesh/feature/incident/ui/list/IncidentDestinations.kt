package com.example.testresqmesh.feature.incident.ui.list

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.theme.TacticalBlack
import com.example.testresqmesh.core.ui.theme.TacticalCarbon
import com.example.testresqmesh.feature.incident.viewmodel.IncidentDestination
import com.example.testresqmesh.feature.incident.viewmodel.IncidentMetrics
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun IncidentDestinations(
    currentDestination: IncidentDestination,
    metrics: IncidentMetrics,
    onDestinationChanged: (IncidentDestination) -> Unit
) {
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
                        .clickable { onDestinationChanged(destination) }
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
}
