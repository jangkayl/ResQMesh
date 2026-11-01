package edu.cit.resqmesh.core.network.dispatch

import edu.cit.resqmesh.core.network.MeshPayload
import edu.cit.resqmesh.core.network.PayloadDispatcher
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
class ChatSyncDispatchTest {
    private val callback = RecordingDispatcherCallback()
    private val dispatcher = PayloadDispatcher(callback)

    private fun dispatch(payload: MeshPayload) = dispatcher.dispatch("ep", ProtoBuf.encodeToByteArray(payload))

    @Test fun requestsAndResponsesReachOnlyChatSync() {
        dispatch(MeshPayload(id = "a", type = "CHAT_SYNC_REQ", senderNodeId = "A1"))
        dispatch(MeshPayload(id = "b", type = "CHAT_SYNC_RES", senderNodeId = "A1"))
        assertEquals(listOf("a", "b"), callback.chatSyncPackets.map { it.id })
        assertTrue(callback.conversations.isEmpty())
    }

    @Test fun aSyncCopyBypassesTheNormalMessagePath() {
        dispatch(MeshPayload(id = "copy", type = "CONVERSATION", chatSync = true, senderNodeId = "Z9"))
        assertEquals(listOf("copy"), callback.chatSyncPackets.map { it.id })
        assertTrue(callback.conversations.isEmpty())
    }

    @Test fun aLiveConversationKeepsItsNormalPath() {
        dispatch(MeshPayload(id = "live", type = "CONVERSATION", senderName = "Ann#A1", senderNodeId = "A1"))
        assertTrue(callback.chatSyncPackets.isEmpty())
        assertEquals(1, callback.conversations.size)
    }
}
