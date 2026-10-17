package com.example.testresqmesh.core.domain.usecase.messaging

import com.example.testresqmesh.core.domain.repository.MeshNodeRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

class SendPrivateMessageUseCaseTest {
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
            SendPrivateMessageUseCase(repository(sent = true, pendingKey = true))("bob", "hi", null, null))
        assertEquals(listOf("sendPrivateMessage"), calls)
    }

    @Test
    fun refusedSendWithPendingKeyChangeNeedsVerification() {
        assertEquals(PrivateSendResult.NeedsKeyVerification,
            SendPrivateMessageUseCase(repository(sent = false, pendingKey = true))("bob", "hi", null, null))
        assertEquals(listOf("sendPrivateMessage", "hasPendingPublicKeyChange"), calls)
    }

    @Test
    fun refusedSendWithoutPendingKeyChangeFails() {
        assertEquals(PrivateSendResult.Failed,
            SendPrivateMessageUseCase(repository(sent = false, pendingKey = false))("bob", "hi", null, null))
    }
}
