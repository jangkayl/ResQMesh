package edu.cit.resqmesh.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import edu.cit.resqmesh.data.local.AppDatabase
import edu.cit.resqmesh.data.local.entity.MessageEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Only public, sent, attributed messages may leave this phone through chat sync. */
@RunWith(AndroidJUnit4::class)
class ChatSyncQueryTest {
    private lateinit var database: AppDatabase
    private lateinit var store: RoomMessageStore

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
        store = RoomMessageStore(database.messageDao())
    }

    @After fun tearDown() = database.close()

    private fun row(id: String, time: Long = 1L, kind: String = "COMMUNITY", sosId: String = "") =
        MessageEntity(id, "Ann#A1", null, "hello", null, null, null, null, time, false, false, "", "", "",
            kind, "", sosId, "A1")

    private fun put(vararg rows: MessageEntity) = runBlocking {
        rows.forEach { database.messageDao().insertMessage(it) }
    }

    @Test fun privateUnsentRadioAndUnattributedRowsAreNeverOffered() = runBlocking {
        put(row("keep"), row("private", kind = "PRIVATE").copy(targetName = "Bob#B1"),
            row("pending").copy(deliveredTo = "PENDING"), row("failed").copy(deliveredTo = "FAILED"),
            row("radio", kind = "RADIO"), row("legacy", kind = "LEGACY_RADIO"),
            row("anonymous").copy(senderNodeId = ""))
        assertEquals(listOf("keep"), store.chatSyncIndex(50, 8).map { it.id })
        val ids = listOf("keep", "private", "pending", "failed", "radio", "legacy", "anonymous")
        assertEquals(listOf("keep"), store.chatSyncRows(ids).map { it.id })
    }

    @Test fun eachConversationKeepsItsOwnNewestLimit() = runBlocking {
        repeat(3) { put(row("c$it", time = it.toLong())) }
        repeat(3) { put(row("s$it", time = it.toLong(), kind = "SOS", sosId = "one")) }
        repeat(3) { put(row("t$it", time = it.toLong(), kind = "SOS", sosId = "two")) }
        assertEquals(setOf("c2", "c1", "s2", "s1", "t2", "t1"), store.chatSyncIndex(2, 8).map { it.id }.toSet())
        assertEquals(4, store.chatSyncIndex(2, 1).size)
    }

    @Test fun mediaIsFlaggedInTheIndexButNeverServedAsText() = runBlocking {
        put(row("voice").copy(audioBase64 = "audio"))
        assertTrue(store.chatSyncIndex(50, 8).single().hasMedia)
        assertTrue(store.chatSyncRows(listOf("voice")).isEmpty())
    }
}
