package edu.cit.resqmesh.core.logging

/** Sanitizers for peer-controlled values embedded in log markers. */
object LogFields {
    private const val MAX_TYPE_CHARS = 24

    /** Payload type for a log line: only [A-Z0-9_] (anything else becomes '?'), at most 24 chars. */
    fun type(value: String): String = buildString {
        for (c in value.take(MAX_TYPE_CHARS)) append(if (c in 'A'..'Z' || c in '0'..'9' || c == '_') c else '?')
    }
}
