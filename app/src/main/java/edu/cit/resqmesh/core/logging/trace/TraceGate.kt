package edu.cit.resqmesh.core.logging.trace

/** Master switch pushed in by the app layer; `core/` must not know the developer-mode policy. */
object TraceGate {
    @Volatile
    var enabled: Boolean = false
        private set

    /** Turning off drops everything buffered so nothing lingers while developer mode is off. */
    fun set(value: Boolean) {
        enabled = value
        if (!value) TraceStore.clear()
    }
}
