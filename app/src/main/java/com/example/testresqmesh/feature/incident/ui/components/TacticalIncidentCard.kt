package com.example.testresqmesh.feature.incident.ui.components

import android.text.format.DateUtils
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.components.feedback.ResQStatusChip
import com.example.testresqmesh.core.ui.components.feedback.ResQStatusTone
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.data.local.entity.IncidentEntity

@Composable
fun TacticalIncidentCard(
    incident: IncidentEntity,
    isMine: Boolean,
    isAssignedToMe: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeOffersCount: Int = 0,
    hasMyOffer: Boolean = false,
    onAcknowledge: (() -> Unit)? = null,
    onAssign: (() -> Unit)? = null,
    onStartResponse: (() -> Unit)? = null,
    onViewLocation: ((Double, Double) -> Unit)? = null
) {
    val isCritical = incident.severity.equals("Critical", ignoreCase = true)
    val isOpen = incident.status == "OPEN"

    // Severity Accent Color
    val severityColor = when (incident.severity.lowercase()) {
        "critical" -> ResQTheme.colors.sos
        "serious" -> ResQTheme.colors.warning
        else -> MaterialTheme.colorScheme.primary
    }

    // Status Tone Mapping
    val (statusTone, statusLabel) = when (incident.status) {
        "OPEN" -> ResQStatusTone.Warning to if (incident.workflowVersion == 2) "OPEN FOR OFFERS" else "OPEN • UNCLAIMED"
        "AWAITING_HELPER" -> ResQStatusTone.Warning to "AWAITING HELPER"
        "ACKNOWLEDGED" -> ResQStatusTone.Information to "ACKNOWLEDGED"
        "ASSIGNED" -> ResQStatusTone.Information to "ASSIGNED"
        "RESPONDING" -> ResQStatusTone.Critical to if (incident.workflowVersion == 2) "HELP IN PROGRESS" else "RESPONDING"
        "RESOLVED" -> ResQStatusTone.Success to "RESOLVED"
        "CANCELLED" -> ResQStatusTone.Neutral to "CANCELLED"
        else -> ResQStatusTone.Neutral to incident.status
    }

    // Animated pulse for critical open incidents
    val infiniteTransition = rememberInfiniteTransition(label = "CriticalPulse")
    val pulseAlpha by if (isCritical && isOpen) {
        infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isCritical && isOpen) 1.5.dp else 1.dp,
            color = if (isCritical && isOpen) severityColor.copy(alpha = pulseAlpha) else MaterialTheme.colorScheme.outlineVariant
        ),
        shadowElevation = if (isCritical && isOpen) 4.dp else 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Left tactical severity accent bar
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(severityColor)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.Medium)
            ) {
                // Tier 1 Header: Status Chip on Left, Relative Timestamp on Right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ResQStatusChip(
                        label = statusLabel,
                        tone = statusTone,
                        modifier = Modifier.height(26.dp)
                    )

                    Text(
                        text = DateUtils.getRelativeTimeSpanString(
                            incident.updatedAt,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_RELATIVE
                        ).toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Tier 2 Header: Emergency Type Icon + Title + Severity Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = getIncidentIcon(incident.incidentType),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = severityColor
                        )
                        Text(
                            text = "${incident.incidentType.uppercase()} EMERGENCY",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.2.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Tactical Severity Pill
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = severityColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, severityColor.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isCritical && isOpen) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(severityColor.copy(alpha = pulseAlpha))
                                )
                            }
                            Text(
                                text = incident.severity.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 10.sp,
                                    letterSpacing = 0.5.sp
                                ),
                                color = severityColor
                            )
                        }
                    }
                }

                // Landmark / Area Description Callout
                if (incident.areaDescription.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = incident.areaDescription,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Situation Description
                if (incident.description.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = incident.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Coordination / Offers Strip
                if (incident.workflowVersion == 2 || activeOffersCount > 0 || hasMyOffer) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isOpen) {
                            if (activeOffersCount == 0) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = ResQTheme.colors.warning.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, ResQTheme.colors.warning.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Handshake,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = ResQTheme.colors.warning
                                        )
                                        Text(
                                            text = "Needs Helpers • 0 Offers",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            ),
                                            color = ResQTheme.colors.warning
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Handshake,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "$activeOffersCount ${if (activeOffersCount == 1) "Offer" else "Offers"}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }

                        if (hasMyOffer) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ResQTheme.colors.successContainer.copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, ResQTheme.colors.success.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(11.dp),
                                        tint = ResQTheme.colors.success
                                    )
                                    Text(
                                        text = "You Offered",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        ),
                                        color = ResQTheme.colors.onSuccessContainer
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.Small))

                // Metadata: Reporter & Assigned Responder
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isMine) "Reported by You" else "By: ${incident.creatorName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isMine) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (incident.primaryResponderName != null) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            modifier = Modifier.weight(1f, fill = false),
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Shield,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = when {
                                        incident.workflowVersion == 2 && incident.status == "AWAITING_HELPER" ->
                                            "Lead: ${incident.primaryResponderName} (Pending)"
                                        incident.workflowVersion == 2 -> "Lead: ${incident.primaryResponderName}"
                                        isAssignedToMe -> "Lead: You"
                                        else -> "Lead: ${incident.primaryResponderName}"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Quick Action Bar for Field Responders (One-tap fast triage)
                if (isOpen && !isMine && (onAcknowledge != null || onAssign != null)) {
                    Spacer(Modifier.height(Spacing.Small))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onAcknowledge != null) {
                            OutlinedButton(
                                onClick = onAcknowledge,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Outlined.Done, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Acknowledge", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (onAssign != null) {
                            Button(
                                onClick = onAssign,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Outlined.NearMe, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Claim / Respond", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else if (incident.status == "ASSIGNED" && isAssignedToMe && onStartResponse != null) {
                    Spacer(Modifier.height(Spacing.Small))
                    Button(
                        onClick = onStartResponse,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ResQTheme.colors.warning),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.DirectionsRun, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Start En Route / Responding", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

fun getIncidentIcon(type: String): ImageVector = when (type.lowercase()) {
    "medical" -> Icons.Outlined.LocalHospital
    "trapped" -> Icons.Outlined.WarningAmber
    "fire" -> Icons.Outlined.LocalFireDepartment
    "injury" -> Icons.Outlined.Healing
    "flood" -> Icons.Outlined.WaterDamage
    else -> Icons.Outlined.Emergency
}
