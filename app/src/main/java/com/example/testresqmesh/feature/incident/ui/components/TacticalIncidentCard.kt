package com.example.testresqmesh.feature.incident.ui.components

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.SafetyOrange
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.theme.TacticalBlack
import com.example.testresqmesh.core.ui.theme.TacticalCarbon
import com.example.testresqmesh.data.local.entity.IncidentEntity
import com.example.testresqmesh.feature.incident.viewmodel.displayTitle

internal fun incidentCategoryIcon(incidentType: String): ImageVector = when (incidentType.trim().lowercase()) {
    "medical" -> Icons.Outlined.MedicalServices
    "fire" -> Icons.Outlined.LocalFireDepartment
    "search & rescue", "rescue" -> Icons.Outlined.TravelExplore
    "infrastructure" -> Icons.Outlined.Construction
    "security" -> Icons.Outlined.Security
    else -> Icons.Outlined.WarningAmber
}

@Composable
fun TacticalIncidentCard(
    incident: IncidentEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isMine: Boolean = false,
    isAssignedToMe: Boolean = false,
    activeOffersCount: Int = 0,
    hasMyOffer: Boolean = false,
    confirmedHelperName: String? = null,
    onAcknowledge: (() -> Unit)? = null,
    onAssign: (() -> Unit)? = null,
    onStartResponse: (() -> Unit)? = null,
    onViewLocation: ((Double, Double) -> Unit)? = null
) {
    val urgencyColor = when (incident.severity.lowercase()) {
        "critical" -> ResQTheme.colors.sos
        "serious" -> ResQTheme.colors.warning
        else -> MaterialTheme.colorScheme.primary
    }

    val statusText = when (incident.status) {
        "OPEN" -> "Looking for help"
        "AWAITING_HELPER" -> "Awaiting confirmation"
        "RESPONDING" -> if (incident.workflowVersion == 2) "Helper confirmed" else "Responding"
        "ACKNOWLEDGED" -> "Acknowledged"
        "ASSIGNED" -> "Assigned"
        "RESOLVED" -> "Resolved"
        "CANCELLED" -> "Cancelled"
        else -> incident.status
    }

    val helper = confirmedHelperName ?: incident.primaryResponderName
    val isConfirmed = helper != null && (incident.status == "RESPONDING" || incident.selectionConfirmedAt != null)
    val helperSummaryText = when {
        isConfirmed -> "$helper confirmed"
        activeOffersCount == 0 -> "No offers yet"
        activeOffersCount == 1 -> "1 offer"
        else -> "$activeOffersCount offers"
    }.let { base ->
        if (hasMyOffer && !isConfirmed) "$base · You offered" else base
    }

    val relativeTime = DateUtils.getRelativeTimeSpanString(
        incident.updatedAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()

    val isNight = MaterialTheme.colorScheme.background == TacticalBlack || MaterialTheme.colorScheme.surface == TacticalCarbon
    val hasCoordinates = incident.latitude != null && incident.longitude != null
    val hasLandmark = incident.areaDescription.isNotBlank()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "${incident.displayTitle()}, ${incident.severity} urgency, $statusText, $helperSummaryText"
            },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Author Header Row (Social Post Header)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Avatar Circle with initial
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = incident.creatorName.take(1).uppercase(),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = incident.creatorName,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (isMine) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "You",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "$relativeTime · ${incident.incidentType}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Urgency Badge (Top-right of post)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = urgencyColor
                ) {
                    Text(
                        text = incident.severity.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // 2. Post Headline (Title)
            Text(
                text = incident.displayTitle(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // 3. Post Body (Natural, Unboxed Description)
            if (incident.description.isNotBlank()) {
                Text(
                    text = incident.description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 4. Attached Location (Media Attachment Style)
            if (hasLandmark || hasCoordinates) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = ResQTheme.colors.sos
                            )
                            Text(
                                text = if (hasLandmark) incident.areaDescription else "Attached GPS Location",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (hasCoordinates && onViewLocation != null) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SafetyOrange.copy(alpha = 0.15f),
                                modifier = Modifier.clickable {
                                    onViewLocation(incident.latitude!!, incident.longitude!!)
                                }
                            ) {
                                Text(
                                    text = "Map",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = SafetyOrange,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 5. Engagement Footer Row (Offers & Status)
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                thickness = 0.5.dp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Offers Summary with Icon
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.People,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = if (isConfirmed) ResQTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = helperSummaryText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = if (isConfirmed) ResQTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Status Pill + Arrow
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
