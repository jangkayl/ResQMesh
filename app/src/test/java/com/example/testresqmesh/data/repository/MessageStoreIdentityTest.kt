package com.example.testresqmesh.data.repository

import com.example.testresqmesh.data.local.entity.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageStoreIdentityTest {

    @Test
    fun privateMessages_withSameNodeId_shareOneCanonicalConversation() {
        val truncated = "SM-P615 [NODE#A1B2"
        val complete = "SM-P615 [NODE]#A1B2"
        val grouped = groupPrivateMessages(
            listOf(
                entity("outgoing", sender = "Me [NODE]#FFFF", target = truncated, isMine = true, timestamp = 1L),
                entity("incoming", sender = complete, target = complete, isMine = false, timestamp = 2L)
            )
        )

        assertEquals(setOf(complete), grouped.keys)
        assertEquals(listOf("outgoing", "incoming"), grouped.getValue(complete).map { it.id })
        assertEquals(complete, grouped.getValue(complete).last().senderName)
    }

    private fun entity(
        id: String,
        sender: String,
        target: String,
        isMine: Boolean,
        timestamp: Long
    ) = MessageEntity(
        msgId = id,
        senderName = sender,
        targetName = target,
        text = "hello",
        imageBase64 = null,
        audioBase64 = null,
        locationLat = null,
        locationLng = null,
        timestamp = timestamp,
        isSOS = false,
        isMine = isMine,
        deliveredTo = "",
        seenBy = "",
        outboundRoute = ""
    )
}
