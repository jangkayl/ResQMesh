package edu.cit.resqmesh.core.logging.trace

/** Master switch pushed in by the app layer; `core/` must not know the developer-mode policy. */
object TraceGate {
    private val resetHooks = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    /** Registers trace-only state to clear when tracing is turned off. Never used for mesh state. */
    fun onDisabled(hook: () -> Unit) { resetHooks.add(hook) }

    @Volatile
    var enabled: Boolean = false
        private set

    /** Turning off drops everything buffered so nothing lingers while developer mode is off. */
    fun set(value: Boolean) {
        enabled = value
        if (!value) {
            TraceStore.clear()
            resetHooks.forEach { runCatching(it) }
        }
    }
}
