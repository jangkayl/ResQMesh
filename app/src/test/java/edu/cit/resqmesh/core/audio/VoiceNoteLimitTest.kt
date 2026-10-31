package edu.cit.resqmesh.core.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceNoteLimitTest {
    @Test fun limitIsThirtySeconds() = assertEquals(30_000, MAX_VOICE_NOTE_MS)

    @Test fun labelShowsWholeSecondsAgainstTheLimit() {
        assertEquals("0:00 / 0:30", voiceNoteElapsedLabel(0))
        assertEquals("0:09 / 0:30", voiceNoteElapsedLabel(9_999))
        assertEquals("0:24 / 0:30", voiceNoteElapsedLabel(24_000))
    }

    @Test fun labelNeverLeavesTheLimitRange() {
        assertEquals("0:30 / 0:30", voiceNoteElapsedLabel(31_500))
        assertEquals("0:00 / 0:30", voiceNoteElapsedLabel(-1))
    }
}
