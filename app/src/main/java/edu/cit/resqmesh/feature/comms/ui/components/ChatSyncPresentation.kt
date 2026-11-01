package edu.cit.resqmesh.feature.comms.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.cit.resqmesh.core.model.ChatMessage
import edu.cit.resqmesh.core.model.ChatSyncState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Stands in for an attachment that has not arrived; null once there is nothing to explain. */
internal fun syncMediaPlaceholder(message: ChatMessage): String? {
    val label = if (message.syncMediaKind == "VOICE") "Voice note" else "Image"
    return when (message.syncState) {
        ChatSyncState.MEDIA_PENDING -> "$label · downloading…"
        ChatSyncState.MEDIA_NOT_SYNCED -> "$label not synced"
        else -> null
    }
}

/** The original send time, so old emergency news is never read as current. Adds the date on another day. */
internal fun syncedTimeLabel(message: ChatMessage, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): String? {
    if (message.syncState == ChatSyncState.NONE) return null
    val sameDay = dayOf(message.timestamp, zone) == dayOf(nowMs, zone)
    val format = SimpleDateFormat(if (sameDay) "h:mm a" else "MMM d, h:mm a", Locale.US).apply { timeZone = zone }
    return "Synced · " + format.format(Date(message.timestamp))
}

private fun dayOf(timeMs: Long, zone: TimeZone) = Calendar.getInstance(zone).apply { timeInMillis = timeMs }
    .let { it.get(Calendar.YEAR) to it.get(Calendar.DAY_OF_YEAR) }

/** The row the history divider sits under: everything newer arrived live. */
internal fun newestSyncedId(messages: List<ChatMessage>): String? =
    messages.filter { it.syncState != ChatSyncState.NONE }.maxByOrNull { it.timestamp }?.id

/** "Catching up · 3 of 10" while attachments are still arriving, otherwise null. */
internal fun catchUpLabel(messages: List<ChatMessage>): String? {
    val pending = messages.count { it.syncState == ChatSyncState.MEDIA_PENDING }
    val total = messages.count { it.syncMediaKind.isNotEmpty() && it.syncState != ChatSyncState.MEDIA_NOT_SYNCED }
    return if (pending == 0) null else "Catching up · ${total - pending} of $total"
}

/** Unread and seen receipts are for live messages: history must not alert or add network traffic. */
internal fun isLiveMessage(message: ChatMessage) = message.syncState == ChatSyncState.NONE

@Suppress("FunctionNaming")
@Composable
internal fun SyncedHistoryDivider() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("synced_history_divider"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(Modifier.weight(1f))
        Text(
            text = "Synced history above",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        HorizontalDivider(Modifier.weight(1f))
    }
}

@Suppress("FunctionNaming")
@Composable
internal fun CatchUpLine(messages: List<ChatMessage>) {
    val label = catchUpLabel(messages) ?: return
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("catch_up_line")
        )
    }
}
