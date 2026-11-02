package edu.cit.resqmesh.feature.sos.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** Mutes message notifications for one SOS on this phone. Disabled while Settings has them all off. */
@Suppress("FunctionNaming")
@Composable
internal fun SosMessageBell(alertsEnabled: Boolean, muted: Boolean, onMutedChange: (Boolean) -> Unit) {
    val on = alertsEnabled && !muted
    IconButton(
        onClick = { onMutedChange(!muted) },
        enabled = alertsEnabled,
        modifier = Modifier.testTag("sos_message_bell")
    ) {
        Icon(
            imageVector = if (on) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
            contentDescription = when {
                !alertsEnabled -> "SOS message notifications are off in Settings"
                muted -> "Turn on notifications for this SOS"
                else -> "Mute notifications for this SOS"
            }
        )
    }
}
