package com.example.testresqmesh.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.testresqmesh.data.local.AppDatabase
import com.example.testresqmesh.data.local.entity.MessageEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessageStoreDeliveryTest {
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

    @Test fun receiptWinsOverAnAlreadyScheduledTimeout() = runBlocking {
        database.messageDao().insertMessage(message("accepted", ""))
        store.markDelivered("accepted", "recipient")
        store.markFailed("accepted")
        assertEquals("recipient", database.messageDao().getMessageById("accepted")?.deliveredTo)
    }

    @Test fun pendingExpiryCannotFailAnAcceptedOrDeliveredSend() = runBlocking {
        database.messageDao().insertMessage(message("pending", "PENDING"))
        store.markSent("pending")
        store.expirePending("pending")
        assertEquals("", database.messageDao().getMessageById("pending")?.deliveredTo)
        store.markDelivered("pending", "recipient")
        store.expirePending("pending")
        assertEquals("recipient", database.messageDao().getMessageById("pending")?.deliveredTo)
    }

    @Test fun pendingExpiryAndAcceptedTimeoutFailOnlyTheirOwnStates() = runBlocking {
        database.messageDao().insertMessage(message("pending", "PENDING"))
        database.messageDao().insertMessage(message("accepted", ""))
        store.expirePending("pending")
        store.markFailed("accepted")
        assertEquals("FAILED", database.messageDao().getMessageById("pending")?.deliveredTo)
        assertEquals("FAILED", database.messageDao().getMessageById("accepted")?.deliveredTo)
    }

    private fun message(id: String, delivery: String) = MessageEntity(
        msgId = id,
        senderName = "sender#1234",
        targetName = "recipient#5678",
        text = "test",
        imageBase64 = null,
        audioBase64 = null,
        locationLat = null,
        locationLng = null,
        timestamp = 1L,
        isSOS = false,
        isMine = true,
        deliveredTo = delivery,
        seenBy = "",
        outboundRoute = ""
    )
}
