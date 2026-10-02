package com.example.testresqmesh.core.utils

import java.util.ArrayDeque

/** Session-only ordering for automatically played recorded Radio notes. */
internal class RadioVoiceQueue {
    data class Note(val id: String, val audio: String, val sender: String)

    private val pending = ArrayDeque<Note>()
    private val seenIds = LinkedHashSet<String>()
    private var monitoring = false
    var current: Note? = null
        private set

    fun setMonitoring(enabled: Boolean) {
        monitoring = enabled
        if (!enabled) {
            pending.clear()
            seenIds.clear()
            current = null
        }
    }

    fun enqueue(note: Note): Boolean {
        if (!monitoring || pending.size + (if (current == null) 0 else 1) >= 32 || !seenIds.add(note.id)) return false
        if (seenIds.size > 128) seenIds.remove(seenIds.first())
        pending.addLast(note)
        return true
    }

    fun currentOrNext(): Note? {
        if (!monitoring) return null
        if (current == null) current = pending.pollFirst()
        return current
    }

    fun completeCurrent() {
        current = null
    }
}
