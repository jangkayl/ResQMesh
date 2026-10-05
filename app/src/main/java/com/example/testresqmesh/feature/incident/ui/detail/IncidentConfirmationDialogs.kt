package com.example.testresqmesh.feature.incident.ui.detail

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.SafetyOrange
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun IncidentConfirmationDialogs(
    confirmDialogAction: String?,
    onSelectLead: (String) -> Unit,
    onRevokeLead: () -> Unit,
    onWithdrawOffer: () -> Unit,
    onDeclineLead: () -> Unit,
    onResolve: () -> Unit,
    onCancel: () -> Unit,
    onDismissConfirm: () -> Unit
) {
    // Confirmation Dialogs (Stop Mesh Session UI/UX style)
    confirmDialogAction?.let { actionStr ->
        when {
            actionStr.startsWith("choose:") -> {
                val parts = actionStr.split(":", limit = 4)
                val offerId = parts.getOrNull(1) ?: ""
                val helperName = parts.getOrNull(2) ?: "this helper"
                val offerNote = parts.getOrNull(3) ?: ""
                val message = if (offerNote.isNotBlank()) {
                    "“$offerNote”\n\nThis helper must confirm they can assist before they are confirmed."
                } else {
                    "This helper must confirm they can assist before they are confirmed."
                }

                TacticalActionConfirmationSheet(
                    title = "Choose $helperName?",
                    message = message,
                    confirmText = "Choose helper",
                    cancelText = "Cancel",
                    icon = Icons.Default.CheckCircle,
                    confirmColor = SafetyOrange,
                    confirmContentColor = Color.White,
                    onConfirm = {
                        onSelectLead(offerId)
                        onDismissConfirm()
                    },
                    onDismiss = { onDismissConfirm() }
                )
            }
            actionStr == "revoke" -> {
                TacticalActionConfirmationSheet(
                    title = "Remove selection?",
                    message = "This removes the selected helper from this incident so you can review offers again.",
                    confirmText = "Remove selection",
                    cancelText = "Keep helper",
                    icon = Icons.Default.Warning,
                    confirmColor = ResQTheme.colors.sos,
                    confirmContentColor = ResQTheme.colors.onSos,
                    onConfirm = {
                        onRevokeLead()
                        onDismissConfirm()
                    },
                    onDismiss = { onDismissConfirm() }
                )
            }
            actionStr == "withdraw" -> {
                TacticalActionConfirmationSheet(
                    title = "Withdraw offer?",
                    message = "Are you sure you want to withdraw your help offer?",
                    confirmText = "Withdraw offer",
                    cancelText = "Keep offer",
                    icon = Icons.Default.Warning,
                    confirmColor = ResQTheme.colors.sos,
                    confirmContentColor = ResQTheme.colors.onSos,
                    onConfirm = {
                        onWithdrawOffer()
                        onDismissConfirm()
                    },
                    onDismiss = { onDismissConfirm() }
                )
            }
            actionStr == "decline" -> {
                TacticalActionConfirmationSheet(
                    title = "Decline selection?",
                    message = "Let the reporter know that you cannot help with this incident right now.",
                    confirmText = "I can’t help",
                    cancelText = "Back",
                    icon = Icons.Default.Warning,
                    confirmColor = ResQTheme.colors.sos,
                    confirmContentColor = ResQTheme.colors.onSos,
                    onConfirm = {
                        onDeclineLead()
                        onDismissConfirm()
                    },
                    onDismiss = { onDismissConfirm() }
                )
            }
            actionStr == "resolve" -> {
                TacticalActionConfirmationSheet(
                    title = "Mark incident resolved?",
                    message = "This records on this phone that help was completed and closes the incident.",
                    confirmText = "Mark resolved",
                    cancelText = "Keep open",
                    icon = Icons.Default.CheckCircle,
                    confirmColor = ResQTheme.colors.success,
                    confirmContentColor = ResQTheme.colors.onSuccess,
                    onConfirm = {
                        onResolve()
                        onDismissConfirm()
                    },
                    onDismiss = { onDismissConfirm() }
                )
            }
            actionStr == "cancel" -> {
                TacticalActionConfirmationSheet(
                    title = "Cancel incident?",
                    message = "This will cancel the emergency request on this phone.",
                    confirmText = "Cancel incident",
                    cancelText = "Keep incident",
                    icon = Icons.Default.Warning,
                    confirmColor = ResQTheme.colors.sos,
                    confirmContentColor = ResQTheme.colors.onSos,
                    onConfirm = {
                        onCancel()
                        onDismissConfirm()
                    },
                    onDismiss = { onDismissConfirm() }
                )
            }
        }
    }
}
