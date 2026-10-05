package com.example.testresqmesh.feature.incident.ui.coordination

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.data.local.entity.IncidentEntity
import com.example.testresqmesh.data.local.entity.IncidentOfferEntity
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CoordinationStatusBanners(
    incident: IncidentEntity,
    isReporter: Boolean,
    isSelectedHelper: Boolean,
    selectedOffer: IncidentOfferEntity?,
    actionMessage: String?,
    wasRevoked: Boolean
) {
    // 1. Triage Status Banner
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = when (incident.status) {
                    "OPEN" -> ResQTheme.colors.warning.copy(alpha = 0.2f)
                    "AWAITING_HELPER" -> ResQTheme.colors.warning.copy(alpha = 0.2f)
                    "RESPONDING" -> ResQTheme.colors.success.copy(alpha = 0.2f)
                    else -> MaterialTheme.colorScheme.primaryContainer
                },
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (incident.status) {
                            "OPEN" -> Icons.Outlined.Handshake
                            "AWAITING_HELPER" -> Icons.Outlined.HourglassTop
                            "RESPONDING" -> Icons.AutoMirrored.Outlined.DirectionsRun
                            "RESOLVED" -> Icons.Outlined.CheckCircle
                            else -> Icons.Outlined.Info
                        },
                        contentDescription = null,
                        tint = when (incident.status) {
                            "OPEN" -> ResQTheme.colors.warning
                            "AWAITING_HELPER" -> ResQTheme.colors.warning
                            "RESPONDING" -> ResQTheme.colors.success
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (incident.status) {
                        "OPEN" -> "COORDINATION OPEN"
                        "AWAITING_HELPER" -> "AWAITING CONFIRMATION"
                        "RESPONDING" -> "RESPONSE IN PROGRESS"
                        "RESOLVED" -> "INCIDENT CONCLUDED"
                        "CANCELLED" -> "REQUEST CANCELLED"
                        else -> incident.status
                    },
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when (incident.status) {
                        "OPEN" -> if (isReporter) "Review volunteer offers below and select a lead helper." else "Submit your skills or ETA below to offer assistance."
                        "AWAITING_HELPER" -> when {
                            isSelectedHelper -> "You have been selected as Lead Helper! Confirm response below."
                            isReporter -> "Waiting for ${selectedOffer?.helperName ?: "lead helper"} to confirm readiness."
                            else -> "${selectedOffer?.helperName ?: "Lead helper"} selected. Awaiting confirmation."
                        }
                        "RESPONDING" -> "Lead helper confirmed field response underway."
                        "RESOLVED" -> "Incident marked resolved by the reporting party."
                        "CANCELLED" -> "Incident was cancelled by the reporter."
                        else -> "Current coordination state: ${incident.status}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Action / Sync Feedback Banner
    if (actionMessage != null) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = actionMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }

    // Revocation Alert Banner
    if (wasRevoked) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Your previous lead helper selection was revoked by the reporter. You may submit an updated offer if available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
