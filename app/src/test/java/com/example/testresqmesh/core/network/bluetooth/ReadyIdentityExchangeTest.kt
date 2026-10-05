package com.example.testresqmesh.core.network.bluetooth

import com.example.testresqmesh.core.network.TransportDispatchResult
import com.example.testresqmesh.core.network.bluetooth.state.*
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test

class ReadyIdentityExchangeTest {
    @Test fun bothRolesSendImmediatelyAndQueueRejectionsAreBounded() {
        BleLinkRole.entries.forEach { role ->
            val registry = BleLinkRegistry()
            val link = registry.begin("endpoint", role, "A001", ConcurrentLinkedDeque(), AtomicBoolean(false))
            registry.transition(link, BleLinkState.CONFIGURING)
            registry.transition(link, BleLinkState.READY)
            val scheduled = ArrayDeque<() -> Unit>()
            var sends = 0
            val exchange = ReadyIdentityExchange(
                { registry.isCurrent(it) && it.state == BleLinkState.READY },
                { sends++; TransportDispatchResult.REJECTED_QUEUE_FULL },
                { delay, action -> assertEquals(5_000L, delay); scheduled.add(action) }
            )
            exchange.start(link)
            assertEquals(1, sends)
            while (scheduled.isNotEmpty()) scheduled.removeFirst().invoke()
            assertEquals(3, sends)
        }
    }

    @Test fun replacementGenerationCannotReceiveOldRetry() {
        val registry = BleLinkRegistry()
        val link = registry.begin("endpoint", BleLinkRole.SERVER, null, ConcurrentLinkedDeque(), AtomicBoolean(false))
        registry.transition(link, BleLinkState.CONFIGURING); registry.transition(link, BleLinkState.READY)
        val scheduled = ArrayDeque<() -> Unit>()
        var sends = 0
        val exchange = ReadyIdentityExchange(
            { registry.isCurrent(it) && it.state == BleLinkState.READY },
            { sends++; TransportDispatchResult.REJECTED_QUEUE_FULL },
            { _, action -> scheduled.add(action) }
        )
        exchange.start(link)
        registry.begin("endpoint", BleLinkRole.SERVER, null, ConcurrentLinkedDeque(), AtomicBoolean(false))
        scheduled.removeFirst().invoke()
        assertEquals(1, sends)
        assertTrue(scheduled.isEmpty())
    }

    @Test fun acceptedIdentityDoesNotScheduleAnotherTransmission() {
        val registry = BleLinkRegistry()
        val link = registry.begin("endpoint", BleLinkRole.CLIENT, "A001", ConcurrentLinkedDeque(), AtomicBoolean(false))
        registry.transition(link, BleLinkState.CONFIGURING); registry.transition(link, BleLinkState.READY)
        var sends = 0
        ReadyIdentityExchange({ true }, { sends++; TransportDispatchResult.ACCEPTED }, { _, _ -> fail("Accepted identity must not retry") }).start(link)
        assertEquals(1, sends)
    }
}
