package com.example.testresqmesh.core.network.bluetooth

import com.example.testresqmesh.core.network.TransportDispatchResult
import com.example.testresqmesh.core.network.bluetooth.state.*
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test

class LinkIdentityAdmissionTest {
    private class Fixture {
        val registry = BleLinkRegistry()
        var time = 0L
        val timers = mutableListOf<Pair<Long, () -> Unit>>()
        val sent = mutableListOf<Triple<BleLink, String, String>>()
        val admitted = mutableListOf<Pair<BleLink, Boolean>>()
        val expired = mutableListOf<BleLink>()
        var result = TransportDispatchResult.ACCEPTED
        val admission = LinkIdentityAdmission({ registry.isCurrent(it) && it.state == BleLinkState.READY },
            { link, nonce, id -> sent += Triple(link, nonce, id); result },
            { delay, action -> timers += time + delay to action },
            { link, legacy -> admitted += link to legacy }, { expired += it })
        fun link(role: BleLinkRole = BleLinkRole.CLIENT): BleLink {
            val link = registry.begin("endpoint", role, "ABCD", ConcurrentLinkedDeque(), AtomicBoolean())
            registry.transition(link, BleLinkState.CONFIGURING); registry.transition(link, BleLinkState.READY)
            return link
        }
        fun tick(duration: Long) {
            val until = time + duration
            while (true) {
                val next = timers.filter { it.first <= until }.minByOrNull { it.first } ?: break
                timers.remove(next); time = next.first; next.second()
            }
            time = until
        }
        fun write() = admission.onFrame("endpoint", sent.last().third, true)
        fun ack(link: BleLink) = admission.onAcknowledgement(link, sent.last().second)
    }

    @Test fun acceptanceAndAdvertisementsDoNotAdmitEitherRole() {
        for (role in BleLinkRole.entries) {
            val f = Fixture(); val link = f.link(role); f.admission.start(link)
            assertFalse(f.registry.isIdentityAdmitted("endpoint"))
            f.admission.onPeerIdentity(link, true); f.ack(link)
            assertFalse(link.identityAdmitted)
            f.write()
            assertTrue(link.identityAdmitted)
            assertEquals(listOf(link to false), f.admitted)
        }
    }

    @Test fun wrongOrPreviousNonceCannotAdmitReplacementGeneration() {
        val f = Fixture(); val old = f.link(); f.admission.start(old)
        val oldFrame = f.sent.single()
        val replacement = f.link(); f.admission.start(replacement)
        f.admission.onPeerIdentity(replacement, true); f.write()
        f.admission.onAcknowledgement(replacement, oldFrame.second)
        f.admission.onAcknowledgement(replacement, "wrong")
        f.admission.onFrame("endpoint", oldFrame.third, true)
        assertFalse(replacement.identityAdmitted)
        f.ack(replacement); assertTrue(replacement.identityAdmitted)
        f.tick(60_000); assertTrue(f.expired.isEmpty())
    }

    @Test fun legacyRequiresPeerIdentityAndActualWriteCompletion() {
        val f = Fixture(); val link = f.link(); f.admission.start(link)
        f.admission.onPeerIdentity(link, false)
        assertFalse(link.identityAdmitted)
        f.write(); assertEquals(listOf(link to true), f.admitted)
    }

    @Test fun currentPeerCannotDowngradeToAvoidAcknowledgement() {
        val f = Fixture(); val link = f.link(); f.admission.start(link)
        f.admission.onPeerIdentity(link, true); f.admission.onPeerIdentity(link, false); f.write()
        assertFalse(link.identityAdmitted)
        f.ack(link); assertTrue(link.identityAdmitted)
    }

    @Test fun missingAcknowledgementRetriesOnlyAfterWriteAndExpiresOnce() {
        val f = Fixture(); val link = f.link(); f.admission.start(link); f.admission.start(link)
        f.admission.onPeerIdentity(link, true)
        f.tick(5_000); assertEquals(1, f.sent.size)
        repeat(3) { f.write(); f.tick(5_000) }
        assertEquals(3, f.sent.size)
        assertEquals(1, f.sent.map { it.second }.distinct().size)
        f.tick(30_000); assertEquals(listOf(link), f.expired)
        assertFalse(link.identityAdmitted)
    }

    @Test fun rejectedQueueRetriesAreBoundedAndRetirementCancelsAllWork() {
        val f = Fixture(); f.result = TransportDispatchResult.REJECTED_QUEUE_FULL
        val link = f.link(); f.admission.start(link); f.tick(10_000)
        assertEquals(3, f.sent.size)
        f.registry.retire(link) {}; f.tick(60_000)
        assertTrue(f.admitted.isEmpty()); assertTrue(f.expired.isEmpty())
    }

    @Test fun missingWriteCompletionTimesOutEvenAfterAckAndPeerIdentity() {
        val f = Fixture(); val link = f.link(); f.admission.start(link)
        f.admission.onPeerIdentity(link, true); f.ack(link); f.tick(30_000)
        assertEquals(listOf(link), f.expired); assertFalse(link.identityAdmitted)
    }

    @Test fun clearAndReplacementNeverInheritAdmission() {
        val f = Fixture(); val link = f.link(); f.admission.start(link)
        f.admission.onPeerIdentity(link, false); f.write(); assertTrue(link.identityAdmitted)
        val replacement = f.link(); assertFalse(f.registry.isIdentityAdmitted("endpoint"))
        f.admission.start(replacement); f.admission.clear(); f.tick(60_000)
        assertTrue(f.expired.isEmpty())
    }
}
