package edu.cit.resqmesh.app.navigation

import android.content.Intent
import edu.cit.resqmesh.core.security.input.TextPolicy
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** Validated in-process navigation request produced by [NotificationRouterActivity]. */
sealed interface NavigationRequest {
    data class OpenSos(
        val sender: String?,
        val text: String?,
        val sosId: String?,
        val viewMap: Boolean
    ) : NavigationRequest

    data class OpenChat(val nodeId: String) : NavigationRequest

    data class OpenIncident(val incidentId: String) : NavigationRequest
}

/**
 * Process-local hand-off from the non-exported router to MainActivity. Each request is consumed
 * once, so a recreated activity never replays an old deep link.
 */
object NavigationRequestBus {
    private val channel = Channel<NavigationRequest>(capacity = 8)

    val requests: Flow<NavigationRequest> = channel.receiveAsFlow()

    fun post(request: NavigationRequest) {
        channel.trySend(request)
    }
}

/** Pure extra validation; the only place notification extras are trusted into requests. */
object NavigationRequestParser {
    const val EXTRA_TRIGGER_SOS = "EXTRA_TRIGGER_SOS"
    const val EXTRA_VIEW_SOS_MAP = "EXTRA_VIEW_SOS_MAP"
    const val EXTRA_SOS_SENDER = "EXTRA_SOS_SENDER"
    const val EXTRA_SOS_TEXT = "EXTRA_SOS_TEXT"
    const val EXTRA_SOS_ID = "EXTRA_SOS_ID"
    const val EXTRA_OPEN_CHAT_NODE = "EXTRA_OPEN_CHAT_NODE"
    const val EXTRA_OPEN_INCIDENT = "EXTRA_OPEN_INCIDENT"

    const val MAX_SOS_TEXT = 1000
    const val MAX_NAME = 128
    const val MAX_SOS_ID = 64

    private val NODE_ID = Regex("[A-Za-z0-9_-]{1,64}")
    private val INCIDENT_ID = Regex("[A-Za-z0-9_.:-]{1,$MAX_SOS_ID}")
    private val SOS_ID = Regex("[A-Za-z0-9_.:-]{1,$MAX_SOS_ID}")

    /** Drops control characters, trims, and caps length; null when nothing remains. */
    fun sanitize(value: String?, maxLength: Int): String? {
        if (value == null) return null
        val trimmed = value.filter { !it.isISOControl() }.trim()
        val cleaned = TextPolicy.clampLength(trimmed, maxLength).trim()
        return cleaned.ifEmpty { null }
    }

    /** A chat target must be a qualified name ending in `#NODE_ID`; anything else is rejected. */
    fun validChatNode(raw: String?): String? {
        val name = sanitize(raw, MAX_NAME) ?: return null
        val id = name.substringAfterLast('#', missingDelimiterValue = "")
        return if (NODE_ID.matches(id)) name else null
    }

    /** Untrusted intent extras, grouped so [parse] stays a single-argument call. */
    data class RawExtras(
        val triggerSos: Boolean = false,
        val viewSosMap: Boolean = false,
        val sender: String? = null,
        val text: String? = null,
        val sosId: String? = null,
        val chatNode: String? = null,
        val incidentId: String? = null
    )

    fun parse(extras: RawExtras): NavigationRequest? = when {
        extras.triggerSos || extras.viewSosMap -> NavigationRequest.OpenSos(
            sender = sanitize(extras.sender, MAX_NAME),
            text = sanitize(extras.text, MAX_SOS_TEXT),
            sosId = sanitize(extras.sosId, MAX_SOS_ID)?.takeIf { SOS_ID.matches(it) },
            viewMap = true
        )
        else -> sanitize(extras.incidentId, MAX_SOS_ID)?.takeIf { INCIDENT_ID.matches(it) }
            ?.let { NavigationRequest.OpenIncident(it) }
            ?: validChatNode(extras.chatNode)?.let { NavigationRequest.OpenChat(it) }
    }

    fun fromIntent(intent: Intent?): NavigationRequest? {
        if (intent == null) return null
        return parse(
            RawExtras(
                triggerSos = intent.getBooleanExtra(EXTRA_TRIGGER_SOS, false),
                viewSosMap = intent.getBooleanExtra(EXTRA_VIEW_SOS_MAP, false),
                sender = intent.getStringExtra(EXTRA_SOS_SENDER),
                text = intent.getStringExtra(EXTRA_SOS_TEXT),
                sosId = intent.getStringExtra(EXTRA_SOS_ID),
                chatNode = intent.getStringExtra(EXTRA_OPEN_CHAT_NODE),
                incidentId = intent.getStringExtra(EXTRA_OPEN_INCIDENT)
            )
        )
    }
}
