package edu.cit.resqmesh.feature.devtools.export

import edu.cit.resqmesh.core.logging.trace.LogTrace
import edu.cit.resqmesh.core.logging.trace.PacketTrace
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Builds sanitized exports from trace events only: short IDs, no peer names, no free text. */
object TraceExporter {
    private const val DASH = "-"

    private val packetHeader = listOf(
        "time", "dir", "scope", "type", "stage", "packet", "source", "dest", "next_hop",
        "hop", "ttl", "path_len", "route_step", "progress", "total", "bytes", "transport", "drop"
    )
    private val logHeader = listOf("time", "level", "tag", "event", "message")

    private fun time(ms: Long): String = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(Date(ms))

    private fun packetFields(t: PacketTrace): List<String> = listOf(
        time(t.epochMs), t.dir.name, t.scope.name, t.type.name, t.stage.name,
        t.packetKey.ifEmpty { DASH }.let(LogSanitizer::token), t.sourceId?.let(LogSanitizer::token) ?: DASH,
        t.destId?.let(LogSanitizer::token) ?: DASH, t.nextHop?.let(LogSanitizer::token) ?: DASH,
        t.hop?.toString() ?: DASH, t.ttl?.toString() ?: DASH, t.pathLen?.toString() ?: DASH,
        t.routeStep?.toString() ?: DASH, t.progress?.toString() ?: DASH, t.total?.toString() ?: DASH,
        t.bytes.toString(), t.transport?.let(LogSanitizer::token) ?: DASH, t.dropReason.name
    )

    private fun logFields(l: LogTrace): List<String> = listOf(
        time(l.epochMs), l.level.name, LogSanitizer.token(l.tag), LogSanitizer.token(l.event),
        LogSanitizer.sanitize(l.message)
    )

    fun text(packets: List<PacketTrace>, logs: List<LogTrace>, errors: List<LogTrace>): String = buildString {
        appendLine("ResQMesh developer trace (short IDs only, peer names and free text omitted)")
        appendLine()
        appendLine("== Packets (${packets.size}) ==")
        packets.forEach { appendLine(flat(packetFields(it).joinToString("  "))) }
        appendLine()
        appendLine("== Errors (${errors.size}) ==")
        errors.forEach { appendLine(flat(logFields(it).joinToString("  "))) }
        appendLine()
        appendLine("== Logs (${logs.size}) ==")
        logs.forEach { appendLine(flat(logFields(it).joinToString("  "))) }
    }

    /** One line per record: control characters become spaces. */
    internal fun flat(s: String): String = s.map { if (it.isISOControl()) ' ' else it }.joinToString("")

    internal fun csvCell(s: String): String {
        // Neutralise spreadsheet formula prefixes (including tab/CR), then quote when needed.
        val safe = if (s.isNotEmpty() && s[0] in "=+-@\t\r" && s != DASH) "'$s" else s
        val needsQuote = safe.any { it == ',' || it == '"' || it == '\n' }
        return if (needsQuote) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    private fun row(fields: List<String>) = fields.joinToString(",") { csvCell(it) }

    fun csv(packets: List<PacketTrace>, logs: List<LogTrace>, errors: List<LogTrace>): String = buildString {
        appendLine("section,packets")
        appendLine(row(packetHeader))
        packets.forEach { appendLine(row(packetFields(it))) }
        appendLine("section,errors")
        appendLine(row(logHeader))
        errors.forEach { appendLine(row(logFields(it))) }
        appendLine("section,logs")
        appendLine(row(logHeader))
        logs.forEach { appendLine(row(logFields(it))) }
    }
}
