package edu.cit.resqmesh.feature.comms.ui.privatechat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import edu.cit.resqmesh.R
import edu.cit.resqmesh.core.model.ChatMessage
import edu.cit.resqmesh.core.network.SendProgress
import edu.cit.resqmesh.core.ui.components.dialogs.ResQConfirmationDialog
import edu.cit.resqmesh.feature.comms.ui.DeliveryFeedback
import edu.cit.resqmesh.feature.comms.ui.components.TransferStage
import edu.cit.resqmesh.feature.comms.ui.components.transferStage

/** Which send action the sender's own private bubble offers, if any. */
internal enum class PrivateSendAction { Cancel, Retry }

internal fun privateSendAction(message: ChatMessage, stage: TransferStage?): PrivateSendAction? {
    if (!message.isMine || !message.isPrivate) return null
    val feedback = deliveryFeedback(message)
    return when {
        feedback == DeliveryFeedback.Failed -> PrivateSendAction.Retry
        feedback == DeliveryFeedback.Pending -> PrivateSendAction.Cancel
        // Live piece progress without a receipt: still uploading or only held by the next phone.
        feedback == DeliveryFeedback.Sent && stage != null && stage != TransferStage.Failed -> PrivateSendAction.Cancel
        else -> null
    }
}

/** Cancel (with confirmation) or Retry under the sender's private bubble, aligned with it. */
@Composable
internal fun PrivateSendActions(
    message: ChatMessage,
    progress: SendProgress?,
    onCancel: (ChatMessage) -> Unit,
    onRetry: (ChatMessage) -> Unit
) {
    val stage = transferStage(message, progress)
    var confirming by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        SendActionButton(
            action = privateSendAction(message, stage),
            onCancel = { confirming = true },
            onRetry = { onRetry(message) }
        )
    }
    if (confirming) {
        CancelSendDialog(
            reachedNextPhone = stage == TransferStage.ReachedNextPhone,
            onDismiss = { confirming = false },
            onConfirm = { confirming = false; onCancel(message) }
        )
    }
}

@Composable
private fun SendActionButton(action: PrivateSendAction?, onCancel: () -> Unit, onRetry: () -> Unit) {
    when (action) {
        PrivateSendAction.Cancel -> TextButton(onClick = onCancel) {
            Text(stringResource(R.string.transfer_cancel_action), style = MaterialTheme.typography.labelMedium)
        }
        PrivateSendAction.Retry -> TextButton(onClick = onRetry) {
            Text(stringResource(R.string.transfer_retry_action), style = MaterialTheme.typography.labelMedium)
        }
        null -> Unit
    }
}

@Composable
private fun CancelSendDialog(reachedNextPhone: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val description = stringResource(R.string.transfer_cancel_description)
    val custody = stringResource(R.string.transfer_cancel_custody_note)
    ResQConfirmationDialog(
        title = stringResource(R.string.transfer_cancel_title),
        message = if (reachedNextPhone) "$description $custody" else description,
        confirmText = stringResource(R.string.transfer_cancel_confirm),
        cancelText = stringResource(R.string.transfer_cancel_keep),
        icon = Icons.Outlined.Close,
        isDestructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
