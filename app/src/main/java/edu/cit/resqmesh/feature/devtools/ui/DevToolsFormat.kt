package edu.cit.resqmesh.feature.devtools.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import edu.cit.resqmesh.core.logging.trace.PacketTrace
import edu.cit.resqmesh.core.logging.trace.TraceScope
import edu.cit.resqmesh.core.logging.trace.TraceStage
import edu.cit.resqmesh.core.logging.trace.TraceType
import edu.cit.resqmesh.core.ui.components.feedback.ResQStatusTone
import edu.cit.resqmesh.core.ui.theme.Spacing
import edu.cit.resqmesh.feature.devtools.export.LogSanitizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal const val LABEL_WEIGHT = 0.4f
internal const val DASH = "—"

/** On-screen peer label `Name (short id)`. Names come from a UI-only map and are never exported. */
/** Peer-supplied IDs pass the same token check as the export; an unsafe value shows as a dash. */
internal fun safeToken(raw: String?): String? = raw?.let(LogSanitizer::token)?.takeIf { it != "-" }

internal fun peerLabel(id: String?, names: Map<String, String>): String {
    val safe = safeToken(id) ?: return DASH
    return names[safe]?.let { "$it ($safe)" } ?: safe
}

internal fun destLabel(t: PacketTrace, names: Map<String, String>): String =
    t.destId?.let { peerLabel(it, names) } ?: if (t.scope == TraceScope.PUBLIC) "all" else DASH

/**
 * Public relayed packets show hop and TTL left; private packets use the directed route because private
 * relaying leaves hop and TTL unchanged.
 */
internal fun hopLabel(t: PacketTrace): String = when {
    t.scope == TraceScope.PUBLIC && t.ttl != null && t.ttl > 0 ->
        "hop ${t.hop?.toString() ?: DASH} · TTL left ${t.ttl}"
    t.scope == TraceScope.PRIVATE && t.routeStep != null && t.pathLen != null && t.pathLen > 1 ->
        "route step ${t.routeStep}/${t.pathLen - 1}"
    else -> "hop $DASH · TTL $DASH"
}

/** Voice and transfer rows get honest names: only known voice is called voice. */
internal fun typeLabel(t: PacketTrace): String = when (t.type) {
    TraceType.VOICE_NOTE -> "VOICE NOTE"
    TraceType.MEDIA_TRANSFER -> "MEDIA (content unknown)"
    else -> t.type.name
}

/** Piece progress such as 12/18, and whether the transfer was reassembled on this phone. */
internal fun progressLabel(t: PacketTrace): String? {
    val p = t.progress
    val n = t.total
    if (p == null || n == null) return null
    return if (t.stage == TraceStage.RECEIVED && p >= n) "$p/$n reassembled" else "$p/$n"
}

internal fun formatTime(epochMs: Long): String =
    SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(epochMs))

internal fun stageTone(t: PacketTrace): ResQStatusTone = when (t.stage.name) {
    "FAILED", "DROPPED" -> ResQStatusTone.Critical
    "COMPLETED", "RECEIVED" -> ResQStatusTone.Success
    else -> ResQStatusTone.Neutral
}

/** Monospace is reserved for IDs and numbers. */
internal val MonoFont = FontFamily.Monospace

@Composable
internal fun LabeledValue(label: String, value: String, mono: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.ExtraSmall),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(LABEL_WEIGHT)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (mono) MonoFont else null,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f - LABEL_WEIGHT)
        )
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Column(Modifier.padding(top = Spacing.Medium, bottom = Spacing.Small)) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
