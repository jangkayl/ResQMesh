package edu.cit.resqmesh.core.audio

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

    @Test
    fun holdSilencesPlaybackButKeepsArrivalsInOrder() {
        val queue = RadioVoiceQueue()
        queue.setMonitoring(true)
        queue.hold()
        assertTrue(queue.enqueue(note("first")))
        assertTrue(queue.enqueue(note("second")))
        assertNull(queue.currentOrNext())

        queue.release()
        assertEquals("first", queue.currentOrNext()?.id)
        queue.completeCurrent()
        assertEquals("second", queue.currentOrNext()?.id)
    }

    @Test
    fun holdKeepsTheInterruptedNoteForResume() {
        val queue = RadioVoiceQueue()
        queue.setMonitoring(true)
        queue.enqueue(note("playing"))
        assertEquals("playing", queue.currentOrNext()?.id)

        queue.hold()
        assertNull(queue.currentOrNext())
        assertEquals("playing", queue.current?.id)

        queue.release()
        assertEquals("playing", queue.currentOrNext()?.id)
    }

    @Test
    fun switchingOffClearsTheHold() {
        val queue = RadioVoiceQueue()
        queue.setMonitoring(true)
        queue.hold()
        queue.setMonitoring(false)

        queue.setMonitoring(true)
        queue.enqueue(note("new"))
        assertEquals("new", queue.currentOrNext()?.id)
    }
}
