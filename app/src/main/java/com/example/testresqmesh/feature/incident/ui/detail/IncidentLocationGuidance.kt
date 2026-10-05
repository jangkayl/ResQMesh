package com.example.testresqmesh.feature.incident.ui.detail

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.SafetyOrange
import com.example.testresqmesh.data.local.entity.IncidentEntity
import com.example.testresqmesh.feature.incident.viewmodel.IncidentPresentation
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun IncidentLocationGuidance(
    incident: IncidentEntity,
    presentation: IncidentPresentation,
    isNight: Boolean,
    onViewLocation: (Double, Double, String, String) -> Unit
) {
    // 4. Attached Location (Compact Single-Row Banner)
    val hasCoordinates = incident.latitude != null && incident.longitude != null
    val hasLandmark = incident.areaDescription.isNotBlank()
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
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = ResQTheme.colors.sos
                    )
                    Column {
                        Text(
                            text = if (hasLandmark) incident.areaDescription else "Attached GPS Location",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (hasCoordinates) {
                            Text(
                                text = "%.5f, %.5f".format(incident.latitude, incident.longitude),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (hasCoordinates) {
                    Button(
                        onClick = {
                            onViewLocation(
                                incident.latitude!!,
                                incident.longitude!!,
                                incident.creatorName,
                                incident.description
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SafetyOrange
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Map,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Map",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    // 5. Context & Guidance Bar (Social Thread Status)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = when (incident.status) {
                    "RESPONDING" -> ResQTheme.colors.success.copy(alpha = 0.12f)
                    "AWAITING_HELPER" -> ResQTheme.colors.warning.copy(alpha = 0.12f)
                    "RESOLVED" -> ResQTheme.colors.success.copy(alpha = 0.08f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                border = BorderStroke(
                    0.5.dp,
                    when (incident.status) {
                        "RESPONDING" -> ResQTheme.colors.success.copy(alpha = 0.4f)
                        "AWAITING_HELPER" -> ResQTheme.colors.warning.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    }
                )
            ) {
                Text(
                    text = presentation.status,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = when (incident.status) {
                        "RESPONDING" -> ResQTheme.colors.success
                        "AWAITING_HELPER" -> ResQTheme.colors.warning
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                )
            }

            if (presentation.myOffer != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ResQTheme.colors.successContainer.copy(alpha = 0.8f),
                    border = BorderStroke(0.5.dp, ResQTheme.colors.success.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "You offered",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = ResQTheme.colors.onSuccessContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                    )
                }
            }
        }

        if (presentation.nextStep.isNotBlank()) {
            Text(
                text = presentation.nextStep,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 17.sp
                ),
                color = if (presentation.terminal) MaterialTheme.colorScheme.onSurfaceVariant
                    else if (isNight) SafetyOrange else MaterialTheme.colorScheme.primary
            )
        }
    }

    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        thickness = 0.5.dp
    )
}
