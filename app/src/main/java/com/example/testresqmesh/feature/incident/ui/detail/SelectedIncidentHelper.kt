package com.example.testresqmesh.feature.incident.ui.detail

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.data.local.entity.IncidentEntity
import com.example.testresqmesh.feature.incident.viewmodel.IncidentAction
import com.example.testresqmesh.feature.incident.viewmodel.IncidentPresentation
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SelectedIncidentHelper(
    incident: IncidentEntity,
    presentation: IncidentPresentation,
    localSigningKey: String?,
    formattedReachability: String,
    onDirectChat: (String) -> Unit,
    onRevokeSelection: () -> Unit
) {
    // ==========================================
    // CARD 5: SELECTED HELPER SPOTLIGHT (IF ANY)
    // ==========================================
    val selectedHelper = presentation.selectedOffer
    val selectedHelperChatTarget = selectedHelper
        ?.takeUnless { it.helperKey == localSigningKey }
        ?.let(::helperChatTarget)
    if (selectedHelper != null || (incident.workflowVersion != 2 && incident.primaryResponderName != null)) {
        val helperName = selectedHelper?.helperName ?: incident.primaryResponderName ?: "Helper"
        val isConfirmed = incident.status == "RESPONDING" || incident.selectionConfirmedAt != null
        val statusLabel = if (isConfirmed) "Confirmed" else "Awaiting confirmation"
        val statusBadgeColor = if (isConfirmed) ResQTheme.colors.success else ResQTheme.colors.warning

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, statusBadgeColor),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isConfirmed) Icons.Outlined.CheckCircle else Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = statusBadgeColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (isConfirmed) "CONFIRMED RESPONDER" else "SELECTED RESPONDER",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            ),
                            color = statusBadgeColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusBadgeColor.copy(alpha = 0.14f),
                        border = BorderStroke(0.5.dp, statusBadgeColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = statusBadgeColor,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (selectedHelperChatTarget != null) {
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onDirectChat(selectedHelperChatTarget) }
                            } else Modifier
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = statusBadgeColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = helperName.take(1).uppercase(),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = statusBadgeColor
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = helperName,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Mesh radio: $formattedReachability",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (selectedHelper?.note?.isNotBlank() == true) {
                    Text(
                        text = "“${selectedHelper.note}”",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.5.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (presentation.secondary.contains(IncidentAction.REVOKE)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = { onRevokeSelection() },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Select different helper", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}
