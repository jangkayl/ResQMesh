package edu.cit.resqmesh.feature.incident.ui.detail

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import edu.cit.resqmesh.core.ui.theme.ResQTheme
import edu.cit.resqmesh.core.ui.theme.SafetyOrange
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api

private enum class ConfirmTone { DANGER, SUCCESS }

/** Copy and styling of one fixed confirmation sheet. */
private class ConfirmationCopy(
    val title: String,
    val message: String,
    val confirmText: String,
    val cancelText: String,
    val icon: ImageVector,
    val tone: ConfirmTone
)

private val REVOKE_COPY = ConfirmationCopy(
    title = "Remove selection?",
    message = "This removes the selected helper from this incident so you can review offers again.",
    confirmText = "Remove selection", cancelText = "Keep helper",
    icon = Icons.Default.Warning, tone = ConfirmTone.DANGER
)
private val WITHDRAW_COPY = ConfirmationCopy(
    title = "Withdraw offer?",
    message = "Are you sure you want to withdraw your help offer?",
    confirmText = "Withdraw offer", cancelText = "Keep offer",
    icon = Icons.Default.Warning, tone = ConfirmTone.DANGER
)
private val DECLINE_COPY = ConfirmationCopy(
    title = "Decline selection?",
    message = "Let the reporter know that you cannot help with this incident right now.",
    confirmText = "I can’t help", cancelText = "Back",
    icon = Icons.Default.Warning, tone = ConfirmTone.DANGER
)
private val RESOLVE_COPY = ConfirmationCopy(
    title = "Mark incident resolved?",
    message = "This records on this phone that help was completed and closes the incident.",
    confirmText = "Mark resolved", cancelText = "Keep open",
    icon = Icons.Default.CheckCircle, tone = ConfirmTone.SUCCESS
)
private val CANCEL_COPY = ConfirmationCopy(
    title = "Cancel incident?",
    message = "This will cancel the emergency request on this phone.",
    confirmText = "Cancel incident", cancelText = "Keep incident",
    icon = Icons.Default.Warning, tone = ConfirmTone.DANGER
)

private const val CHOOSE_PREFIX = "choose:"
private const val CHOOSE_PARTS = 4
private const val HELPER_MUST_CONFIRM = "This helper must confirm they can assist before they are confirmed."

/**
 * Shows the sheet for [confirmDialogAction]: "choose:<offerId>:<name>:<note>", "revoke", "withdraw",
 * "decline", "resolve" or "cancel". Confirming runs the decision, then [onDismissConfirm].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun IncidentConfirmationDialogs(
    confirmDialogAction: String?,
    callbacks: IncidentDecisionCallbacks,
    onDismissConfirm: () -> Unit
) {
    val actionStr = confirmDialogAction ?: return
    if (actionStr.startsWith(CHOOSE_PREFIX)) {
        ChooseHelperConfirmation(actionStr, callbacks.onSelectLead, onDismissConfirm)
    } else {
        FixedIncidentConfirmation(actionStr, callbacks, onDismissConfirm)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FixedIncidentConfirmation(
    actionStr: String,
    callbacks: IncidentDecisionCallbacks,
    onDismissConfirm: () -> Unit
) {
    val (copy, decision) = when (actionStr) {
        "revoke" -> REVOKE_COPY to callbacks.onRevokeLead
        "withdraw" -> WITHDRAW_COPY to callbacks.onWithdrawOffer
        "decline" -> DECLINE_COPY to callbacks.onDeclineLead
        "resolve" -> RESOLVE_COPY to callbacks.onResolve
        "cancel" -> CANCEL_COPY to callbacks.onCancel
        else -> return
    }
    val isSuccess = copy.tone == ConfirmTone.SUCCESS
    TacticalActionConfirmationSheet(
        title = copy.title,
        message = copy.message,
        confirmText = copy.confirmText,
        cancelText = copy.cancelText,
        icon = copy.icon,
        confirmColor = if (isSuccess) ResQTheme.colors.success else ResQTheme.colors.sos,
        confirmContentColor = if (isSuccess) ResQTheme.colors.onSuccess else ResQTheme.colors.onSos,
        onConfirm = {
            decision()
            onDismissConfirm()
        },
        onDismiss = { onDismissConfirm() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChooseHelperConfirmation(
    actionStr: String,
    onSelectLead: (String) -> Unit,
    onDismissConfirm: () -> Unit
) {
    val parts = actionStr.split(":", limit = CHOOSE_PARTS)
    val offerId = parts.getOrNull(1) ?: ""
    val helperName = parts.getOrNull(2) ?: "this helper"
    val offerNote = parts.getOrNull(3) ?: ""
    val message = if (offerNote.isNotBlank()) "“$offerNote”\n\n$HELPER_MUST_CONFIRM" else HELPER_MUST_CONFIRM

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
