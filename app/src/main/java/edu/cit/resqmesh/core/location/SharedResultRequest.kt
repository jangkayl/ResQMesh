package edu.cit.resqmesh.core.location

/** One in-flight request with many waiters, so a later caller never cancels an earlier one. Main thread only. */
class SharedResultRequest<T> {
    private val waiting = mutableListOf<(T) -> Unit>()

    /** Adds [onResult]; true means this caller is first and must start the request. */
    fun join(onResult: (T) -> Unit): Boolean {
        val first = waiting.isEmpty()
        waiting += onResult
        return first
    }

    fun complete(result: T) {
        val callers = waiting.toList()
        waiting.clear()
        callers.forEach { it(result) }
    }
}
