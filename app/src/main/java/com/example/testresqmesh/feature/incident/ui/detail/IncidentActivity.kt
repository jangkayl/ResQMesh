package com.example.testresqmesh.feature.incident.ui.detail

import android.text.format.DateUtils
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.data.local.entity.DomainEventEntity
import org.json.JSONObject
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun IncidentActivity(
    appliedEvents: List<DomainEventEntity>,
    showActivitySection: Boolean,
    onToggle: () -> Unit
) {
    // Activity section
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(Spacing.Medium)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Outlined.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Activity (${appliedEvents.size})",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Icon(
                    imageVector = if (showActivitySection) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (showActivitySection) {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    appliedEvents.forEach { evt ->
                        val eventDescription = when (evt.eventType) {
                            "INCIDENT_CREATED" -> "${evt.actorName} reported this incident"
                            "INCIDENT_OFFER_UPDATED" -> "${evt.actorName} offered help"
                            "INCIDENT_OFFER_WITHDRAWN" -> "${evt.actorName} withdrew their offer"
                            "INCIDENT_LEAD_SELECTED" -> {
                                val target = runCatching { JSONObject(evt.payloadJson).optString("helperName") }.getOrNull()?.takeIf { it.isNotBlank() } ?: "a helper"
                                "${evt.actorName} selected $target"
                            }
                            "INCIDENT_LEAD_CONFIRMED" -> "${evt.actorName} confirmed they can help"
                            "INCIDENT_LEAD_DECLINED" -> "${evt.actorName} declined the selection"
                            "INCIDENT_LEAD_REVOKED" -> "${evt.actorName} removed the selected helper"
                            "INCIDENT_RESOLVED" -> "${evt.actorName} marked the incident resolved"
                            "INCIDENT_CANCELLED" -> "${evt.actorName} cancelled the incident"
                            "INCIDENT_ACKNOWLEDGED" -> "${evt.actorName} acknowledged the request"
                            "INCIDENT_ASSIGNED" -> "${evt.actorName} volunteered to respond"
                            "INCIDENT_RESPONSE_STARTED" -> "${evt.actorName} started responding"
                            "INCIDENT_ASSIGNMENT_RELEASED" -> "${evt.actorName} released assignment"
                            else -> "${evt.actorName} performed an update"
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = eventDescription,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = DateUtils.getRelativeTimeSpanString(evt.timestamp).toString(),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
