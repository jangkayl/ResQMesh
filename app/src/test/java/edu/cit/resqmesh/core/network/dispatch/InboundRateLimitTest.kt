package edu.cit.resqmesh.core.network.dispatch

import edu.cit.resqmesh.core.network.MeshPayload
import edu.cit.resqmesh.core.network.PayloadDispatcher
import edu.cit.resqmesh.core.network.ratelimit.InboundOriginGate
import edu.cit.resqmesh.core.network.ratelimit.OriginRateLimiter
import edu.cit.resqmesh.core.network.ratelimit.RateLimitPolicy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class InboundRateLimitTest {
    private var now = 0L
    private val cb = RecordingDispatcherCallback(myName = "Me#B001", myId = "B001")
    private val policy = RateLimitPolicy(chatBurst = 2.0, chatPerMin = 1.0)
    private val gate = InboundOriginGate(OriginRateLimiter(policy) { now }, { now })
    private val dispatcher = PayloadDispatcher(
        cb, originGate = gate
    )

    private fun send(relay: String, p: MeshPayload) =
        dispatcher.dispatch(relay, ProtoBuf.encodeToByteArray(MeshPayload.serializer(), p))

    private fun chat(id: String, originId: String) = MeshPayload(
        id = id, type = "MESSAGE", senderName = "N#$originId", senderNodeId = originId, text = "hi"
    )

    @Test fun originOverLimitIsDroppedAndRelayIsNotPenalised() {
        // One relay endpoint forwards two different origins; only the noisy origin is limited.
        repeat(5) { send("relay", chat("a$it", "A001")) }
        repeat(2) { send("relay", chat("c$it", "C001")) }
        assertEquals(listOf("a0", "a1", "c0", "c1"), cb.received)
    }

    @Test fun cooldownStartsAfterThreeViolationsAndBlocksOtherClassesThenExpires() {
        repeat(5) { send("relay", chat("a$it", "A001")) } // 2 allowed, 3 violations -> cooldown
        now += 5_000 // chat bucket has refilled a little, but the origin is cooling down
        send("relay", chat("later", "A001"))
        assertEquals(listOf("a0", "a1"), cb.received)
        now += 61_000
        send("relay", chat("after", "A001"))
        assertEquals(listOf("a0", "a1", "after"), cb.received)
    }

    @Test fun ownNodeIsNeverLimited() {
        val own = (0 until 6).map {
            MeshPayload(id = "o$it", type = "MESSAGE", senderName = "Other#X", senderNodeId = "B001", text = "t")
        }
        own.forEach { send("relay", it) }
        assertEquals(6, cb.received.count { it.startsWith("o") })
    }


    @Test fun presenceFromBusyRelayDoesNotAffectOtherOriginsChat() {
        repeat(20) {
            send("relay", MeshPayload(id = "p$it", type = "SYSTEM", senderName = "N#A001", senderNodeId = "A001",
                routePath = listOf("N#A001"), ttl = 1))
        }
        send("relay", chat("c1", "C001"))
        assertTrue("c1" in cb.received)
    }

    @Test fun droppedPayloadCanBeRetriedWithSameIdAfterRefill() {
        send("relay", chat("a0", "A001")); send("relay", chat("a1", "A001"))
        send("relay", chat("retry", "A001")) // over burst: dropped, must not be remembered
        assertEquals(listOf("a0", "a1"), cb.received)
        assertEquals(false, cb.seen.contains("retry"))
        now += 60_000 // one chat token refills
        send("relay", chat("retry", "A001"))
        assertEquals(listOf("a0", "a1", "retry"), cb.received)
    }

    @Test fun pingAndPongAreNeverOriginLimited() {
        repeat(100) {
            send("ep", MeshPayload(id = "HB:$it", type = "PING", senderName = "N#A001"))
        }
        repeat(3) { send("ep", chat("c$it", "A001")) }
        assertEquals(listOf("c0", "c1"), cb.received)
    }

    private fun relayed(id: String, originId: String) = chat(id, originId).copy(
        relayHopCount = 1, ttl = 3, routePath = listOf("N#$originId", "R#R001")
    )

    @Test fun sosEventsSkipTheOriginGateSoForgeriesNeverChargeTheVictim() {
        fun sos(id: String, sosId: String) = MeshPayload(
            id = id, type = "SOS_EVENT", senderName = "N#A001", senderNodeId = "A001", sosId = sosId, ttl = 5
        )
        repeat(8) { send("r1", sos("e$it", "S$it")) } // forged flood claiming the victim
        assertEquals(8, cb.sosPackets.size)
        // The victim's real, signature-verified SOS bucket is untouched.
        assertTrue(gate.admitVerifiedSos("r1", "SOS-victim-", true))
        repeat(3) { assertTrue(gate.admitVerifiedSos("r1", "SOS-victim-", true)) }
        assertEquals(false, gate.admitVerifiedSos("r1", "SOS-victim-", true)) // burst of four new SOS
        assertTrue(gate.admitVerifiedSos("r1", "SOS-other-", true))
    }

    @Test fun directPayloadMustMatchTheLinksAdmittedNode() {
        cb.admitted["ep1"] = "A001"
        send("ep1", chat("spoof", "C001"))
        send("ep1", chat("real", "A001"))
        assertEquals(listOf("real"), cb.received)
    }

    @Test fun spoofedRelayedTrafficOnOneLinkDoesNotThrottleTheVictimOnAnother() {
        repeat(6) { send("linkX", relayed("x$it", "A001")) } // 2 allowed, then violations -> cooldown on linkX only
        send("linkX", relayed("x-later", "A001"))
        send("linkY", relayed("y0", "A001"))
        send("linkY", relayed("y1", "A001"))
        assertEquals(listOf("x0", "x1", "y0", "y1"), cb.received)
        // The relay itself is never put into cooldown: unrelated origins over linkX still pass.
        send("linkX", relayed("c0", "C001"))
        assertTrue("c0" in cb.received)
    }
}
