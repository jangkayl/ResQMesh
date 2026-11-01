package edu.cit.resqmesh.feature.comms.ui.components

import edu.cit.resqmesh.core.model.ChatMessage
import edu.cit.resqmesh.core.model.ChatSyncState
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSyncPresentationTest {
    private val day = 86_400_000L
    private val noon = 20_000L * day + day / 2

    private fun message(state: Int = ChatSyncState.NONE, kind: String = "", time: Long = noon, id: String = "m") =
        ChatMessage(id, "Ann#A1", "hello", null, null, isMine = false, timestamp = time, syncState = state,
            syncMediaKind = kind)

    @Test fun placeholderNamesTheAttachmentAndItsState() {
        assertEquals("Voice note · downloading…", syncMediaPlaceholder(message(ChatSyncState.MEDIA_PENDING, "VOICE")))
        assertEquals("Image · downloading…", syncMediaPlaceholder(message(ChatSyncState.MEDIA_PENDING, "IMAGE")))
        assertEquals("Voice note not synced", syncMediaPlaceholder(message(ChatSyncState.MEDIA_NOT_SYNCED, "VOICE")))
        assertEquals("Image not synced", syncMediaPlaceholder(message(ChatSyncState.MEDIA_NOT_SYNCED, "IMAGE")))
        assertNull(syncMediaPlaceholder(message(ChatSyncState.SYNCED, "IMAGE")))
        assertNull(syncMediaPlaceholder(message()))
    }

    @Test fun syncedLabelAddsTheDateOnlyOnAnotherDay() {
        val synced = message(ChatSyncState.SYNCED)
        val utc = TimeZone.getTimeZone("UTC")
        assertEquals("Synced · 12:00 PM", syncedTimeLabel(synced, noon + 1_000L, utc))
        assertEquals("Synced · Oct 4, 12:00 PM", syncedTimeLabel(synced, noon + day, utc))
        assertNull(syncedTimeLabel(message(), noon, utc))
    }

    @Test fun dividerSitsUnderTheNewestSyncedRow() {
        val rows = listOf(message(ChatSyncState.SYNCED, time = 1L, id = "old"),
            message(ChatSyncState.MEDIA_PENDING, "IMAGE", 2L, "newest"), message(time = 3L, id = "live"))
        assertEquals("newest", newestSyncedId(rows))
        assertNull(newestSyncedId(listOf(message())))
    }

    @Test fun catchUpCountsAttachmentsAndDisappearsWhenDone() {
        val filled = message(ChatSyncState.SYNCED, "IMAGE", id = "a")
        val pending = message(ChatSyncState.MEDIA_PENDING, "VOICE", id = "b")
        val gaveUp = message(ChatSyncState.MEDIA_NOT_SYNCED, "IMAGE", id = "c")
        val text = message(ChatSyncState.SYNCED, id = "d")
        assertEquals("Catching up · 1 of 2", catchUpLabel(listOf(filled, pending, gaveUp, text)))
        assertNull(catchUpLabel(listOf(filled, gaveUp, text)))
    }

    @Test fun onlyLiveMessagesCountForSeenReceipts() {
        assertTrue(isLiveMessage(message()))
        assertFalse(isLiveMessage(message(ChatSyncState.SYNCED)))
        assertFalse(isLiveMessage(message(ChatSyncState.MEDIA_PENDING, "VOICE")))
    }
}
