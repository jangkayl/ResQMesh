package edu.cit.resqmesh.feature.comms.viewmodel

import edu.cit.resqmesh.core.network.ratelimit.OutgoingChatBudget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * App-scoped queue of finished radio voice notes waiting for a send token. It is not tied to a screen or
 * ViewModel: it drains on its own refill timer, so a held note still goes out after the user leaves.
 * [send] returns false when the repository refused the note (it is then dropped and its token refunded).
 * The queue is in memory only; a process restart loses held notes, like the budget itself.
 */
class RadioVoiceHold(
    private val budget: OutgoingChatBudget,
    private val scope: CoroutineScope,
    private val send: (channel: String, audio: String) -> Boolean
) {
    private val queue = ArrayDeque<Pair<String, String>>()
    private var timer: Job? = null
    private val _held = MutableStateFlow(0)

    /** Notes currently waiting. At [CAPACITY] new recordings are refused. */
    val held: StateFlow<Int> = _held.asStateFlow()

    /** False when the hold is full and the note could not be accepted. Otherwise sent now or held. */
    @Synchronized
    fun submit(channel: String, audio: String): Boolean {
        if (queue.size >= CAPACITY) return false
        queue.addLast(channel to audio)
        drain()
        return true
    }

    @Synchronized
    private fun drain() {
        while (queue.isNotEmpty() && budget.tryConsume()) {
            val (channel, audio) = queue.removeFirst()
            if (!send(channel, audio)) budget.refund()
        }
        _held.value = queue.size
        if (queue.isNotEmpty() && timer?.isActive != true) {
            val wait = budget.state.value.nextRefillInMs.coerceAtLeast(MIN_WAIT_MS)
            timer = scope.launch {
                delay(wait)
                drainAfterWait()
            }
        }
    }

    @Synchronized
    private fun drainAfterWait() {
        timer = null
        drain()
    }

    companion object {
        const val CAPACITY = 5
        private const val MIN_WAIT_MS = 250L
    }
}
