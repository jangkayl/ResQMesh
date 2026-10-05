package com.example.testresqmesh.feature.incident.ui.detail

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.SafetyOrange
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.data.local.entity.IncidentEntity
import com.example.testresqmesh.feature.incident.viewmodel.IncidentAction
import com.example.testresqmesh.feature.incident.viewmodel.IncidentPresentation
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun IncidentPrimaryActions(
    incident: IncidentEntity,
    presentation: IncidentPresentation,
    actionBusy: Boolean,
    localSigningKey: String?,
    onEditOffer: (String) -> Unit,
    onConfirmAction: (String) -> Unit,
    onReviewHelpers: () -> Unit,
    onConfirmLead: () -> Unit,
    onAcknowledge: () -> Unit,
    onAssign: () -> Unit,
    onStartResponse: () -> Unit
) {
    // Docked primary action bar (pinned at bottom of sheet)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.Large)
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 680.dp)
        ) {
            when (val primary = presentation.primary) {
                IncidentAction.OFFER -> {
                    Button(
                        onClick = {
                            onEditOffer("")
                        },
                        enabled = !actionBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 52.dp)
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SafetyOrange,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Offer help", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                IncidentAction.EDIT_OFFER -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                onEditOffer(presentation.myOffer?.note ?: "")
                            },
                            enabled = !actionBusy,
                            modifier = Modifier
                                .weight(2.4f)
                                .defaultMinSize(minHeight = 50.dp)
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Edit my offer", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = { onConfirmAction("withdraw") },
                            enabled = !actionBusy,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 50.dp)
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Withdraw", fontSize = 12.5.sp, maxLines = 1)
                        }
                    }
                }
                IncidentAction.REVIEW -> {
                    Button(
                        onClick = {
                            onReviewHelpers()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 50.dp)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Review helpers", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                IncidentAction.REVIEW_SELECTED -> {
                    Button(
                        onClick = {
                            onReviewHelpers()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 50.dp)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Review selected helper", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                IncidentAction.CONFIRM -> {
                    // Asymmetric CTA: prominent hero confirm button + compact secondary decline
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onConfirmLead,
                            enabled = !actionBusy,
                            modifier = Modifier
                                .weight(2.5f)
                                .defaultMinSize(minHeight = 52.dp)
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ResQTheme.colors.success
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Confirm I can help", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { onConfirmAction("decline") },
                            enabled = !actionBusy,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 52.dp)
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Can’t help", fontSize = 12.5.sp, maxLines = 1)
                        }
                    }
                }
                IncidentAction.RESOLVE -> {
                    Button(
                        onClick = { onConfirmAction("resolve") },
                        enabled = !actionBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 52.dp)
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ResQTheme.colors.success
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Mark resolved", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                // Legacy actions
                IncidentAction.ACKNOWLEDGE -> {
                    Button(
                        onClick = onAcknowledge,
                        enabled = !actionBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp)
                    ) {
                        Text("Acknowledge request", fontSize = 16.sp)
                    }
                }
                IncidentAction.ASSIGN -> {
                    Button(
                        onClick = onAssign,
                        enabled = !actionBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp)
                    ) {
                        Text("Volunteer to respond", fontSize = 16.sp)
                    }
                }
                IncidentAction.START -> {
                    Button(
                        onClick = onStartResponse,
                        enabled = !actionBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ResQTheme.colors.sos
                        )
                    ) {
                        Text("Start responding", fontSize = 16.sp)
                    }
                }
                null -> {
                    if (presentation.terminal) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier.padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (incident.status == "RESOLVED") "Incident is resolved (read-only)" else "Incident was cancelled (read-only)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (presentation.isReporter) {
                        // Reporter with no offers or waiting
                        OutlinedButton(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 56.dp)
                        ) {
                            Text("Waiting for helper offers on mesh", fontSize = 15.sp)
                        }
                    } else if (incident.status == "RESPONDING" && presentation.selectedOffer?.helperKey == localSigningKey) {
                        // Confirmed helper explanation
                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 56.dp)
                        ) {
                            Text("You are confirmed to help", fontSize = 15.sp)
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}
