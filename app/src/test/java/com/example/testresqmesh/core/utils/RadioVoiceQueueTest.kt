package com.example.testresqmesh.core.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioVoiceQueueTest {
    private fun note(id: String) = RadioVoiceQueue.Note(id, "audio-$id", "sender-$id")

    @Test
    fun playsWholeNotesInArrivalOrderAndIgnoresDuplicates() {
        val queue = RadioVoiceQueue()
        queue.setMonitoring(true)
        assertTrue(queue.enqueue(note("first")))
        assertTrue(queue.enqueue(note("second")))
        assertFalse(queue.enqueue(note("first")))

        assertEquals("first", queue.currentOrNext()?.id)
        assertEquals("first", queue.currentOrNext()?.id)
        queue.completeCurrent()
        assertEquals("second", queue.currentOrNext()?.id)
        queue.completeCurrent()
        assertNull(queue.currentOrNext())
    }

    @Test
    fun switchingOffClearsPlaybackAndDoesNotBackfillOffArrivals() {
        val queue = RadioVoiceQueue()
        queue.setMonitoring(true)
        queue.enqueue(note("playing"))
        queue.enqueue(note("pending"))
        assertEquals("playing", queue.currentOrNext()?.id)

        queue.setMonitoring(false)
        assertNull(queue.currentOrNext())
        assertFalse(queue.enqueue(note("while-off")))

        queue.setMonitoring(true)
        assertNull(queue.currentOrNext())
        assertTrue(queue.enqueue(note("new")))
        assertEquals("new", queue.currentOrNext()?.id)
    }
}
