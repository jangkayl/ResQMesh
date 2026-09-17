package com.example.testresqmesh.core.utils

import java.util.Locale

/** Categories intentionally describe an operational concern, not an implementation class. */
enum class TerminalLogCategory(val label: String) {
    CONNECTION("Connection"),
    SYNC("Sync"),
    TRANSPORT("Transport"),
    ROUTING("Routing"),
    SECURITY("Security"),
    SYSTEM("System")
}

enum class TerminalLogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

data class TerminalLogEntry(
    val sequence: Long,
    val timestampLabel: String,
    val level: TerminalLogLevel,
    val category: TerminalLogCategory,
    val event: String,
    val message: String,
    val tag: String,
    val peerName: String? = null,
    val endpoint: String? = null,
    val linkGeneration: Long? = null,
    val role: String? = null,
    val isVerbose: Boolean = false
) {
    fun displayText(): String = buildString {
        append(timestampLabel)
        append("  ")
        append(level.name.padEnd(5))
        append("  ")
        append(category.label.uppercase(Locale.ROOT))
        append("  ")
        append(event)
        if (!peerName.isNullOrBlank()) append("  peer=").append(peerName)
        if (!endpoint.isNullOrBlank()) append("  endpoint=").append(shortEndpoint(endpoint))
        if (linkGeneration != null) append("  link=").append(linkGeneration)
        if (!role.isNullOrBlank()) append("  ").append(role)
        if (message.isNotBlank()) append("\n").append(message)
    }

    companion object {
        fun shortEndpoint(endpoint: String): String =
            if (endpoint.length <= 5) endpoint else "…${endpoint.takeLast(5)}"
    }
}

data class TerminalLinkSnapshot(
    val endpoint: String,
    val peerName: String,
    val role: String,
    val generation: Long,
    val state: String,
    val transport: String = "GATT"
)

/**
 * Temporary compatibility mapping for existing log sites. New network diagnostics should call
 * [AppLogger.event] with an explicit category instead of relying on these message keywords.
 */
object TerminalLogClassifier {
    fun categoryFor(tag: String, message: String): TerminalLogCategory {
        val text = message.lowercase(Locale.ROOT)
        return when (tag) {
            "MeshNetwork_E2EE" -> TerminalLogCategory.SECURITY
            "PayloadDispatcher" -> TerminalLogCategory.ROUTING
            "PAYLOAD_DISPATCHER" -> TerminalLogCategory.SYSTEM
            "BLE_MESH" -> when {
                text.containsAny("system pulse", "heartbeat", "ping", "pong", "identity", "public key", "topology") -> TerminalLogCategory.SYNC
                text.containsAny("l2cap", "mtu", "chunk", "payload queue", "payload sent", "payload received") -> TerminalLogCategory.TRANSPORT
                text.containsAny("route", "relay", "forward") -> TerminalLogCategory.ROUTING
                else -> TerminalLogCategory.CONNECTION
            }
            else -> TerminalLogCategory.SYSTEM
        }
    }

    fun levelFor(message: String): TerminalLogLevel {
        val text = message.lowercase(Locale.ROOT)
        return when {
            text.containsAny("exception", "error parsing", "failed to start") -> TerminalLogLevel.ERROR
            text.containsAny("failed", "timeout", "timed out", "reject", "retiring", "stale", "invalid", "ignored") -> TerminalLogLevel.WARN
            else -> TerminalLogLevel.INFO
        }
    }

    private fun String.containsAny(vararg needles: String): Boolean = needles.any { needle -> contains(needle) }
}
