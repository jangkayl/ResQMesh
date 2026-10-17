package edu.cit.resqmesh.core.domain.usecase.messaging

import edu.cit.resqmesh.core.domain.repository.MeshNodeRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

class SendPrivateChatMessageUseCaseTest {
    private val calls = mutableListOf<String>()

    private fun repository(sent: Boolean, pendingKey: Boolean): MeshNodeRepository =
        Proxy.newProxyInstance(
            MeshNodeRepository::class.java.classLoader,
            arrayOf(MeshNodeRepository::class.java)
        ) { _, method, _ ->
            calls += method.name
            when (method.name) {
                "sendPrivateMessage" -> sent
                "hasPendingPublicKeyChange" -> pendingKey
                else -> error("unexpected ${method.name}")
            }
        } as MeshNodeRepository

    @Test
    fun acceptedSendIsSentWithoutKeyLookup() {
        assertEquals(PrivateSendResult.Sent,
            SendPrivateChatMessageUseCase(repository(sent = true, pendingKey = true))("bob", "hi", null, null))
        assertEquals(listOf("sendPrivateMessage"), calls)
    }

    @Test
    fun refusedSendWithPendingKeyChangeNeedsVerification() {
        assertEquals(PrivateSendResult.NeedsKeyVerification,
            SendPrivateChatMessageUseCase(repository(sent = false, pendingKey = true))("bob", "hi", null, null))
        assertEquals(listOf("sendPrivateMessage", "hasPendingPublicKeyChange"), calls)
    }

    @Test
    fun refusedSendWithoutPendingKeyChangeFails() {
        assertEquals(PrivateSendResult.Failed,
            SendPrivateChatMessageUseCase(repository(sent = false, pendingKey = false))("bob", "hi", null, null))
    }
}
