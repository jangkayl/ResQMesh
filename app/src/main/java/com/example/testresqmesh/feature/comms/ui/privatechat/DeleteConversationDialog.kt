package com.example.testresqmesh.feature.comms.ui.privatechat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import com.example.testresqmesh.core.ui.components.dialogs.ResQConfirmationDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.example.testresqmesh.R

@Composable
internal fun DeleteConversationDialog(
    name: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    ResQConfirmationDialog(
        title = stringResource(R.string.private_chat_delete_title),
        message = stringResource(R.string.private_chat_delete_description, name),
        confirmText = stringResource(R.string.private_chat_delete_confirm),
        cancelText = stringResource(R.string.private_chat_cancel_action),
        icon = Icons.Outlined.DeleteOutline,
        isDestructive = true,
        onConfirm = onDelete,
        onDismiss = onDismiss
    )
}
