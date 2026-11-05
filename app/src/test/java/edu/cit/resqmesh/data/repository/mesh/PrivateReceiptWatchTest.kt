package edu.cit.resqmesh.data.repository.mesh

import edu.cit.resqmesh.core.network.MeshNetworkGateway
import edu.cit.resqmesh.core.network.OutboundFrameEvent
import edu.cit.resqmesh.data.repository.MessageStore
import java.lang.reflect.Proxy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrivateReceiptWatchTest {
    private val sent = listOf("Me#A", "Relay#B", "Peer#C")
    private fun frame(tx: String, stage: OutboundFrameEvent.Stage) =
        OutboundFrameEvent(tx, "m", stage, "TRANSFER", 1, 1, 8192)

    private class Fixture(scope: TestScope, routeNow: List<String>) {
        var releases = 0
        var retries = 0
        val pending = mutableListOf<String>()
        var sentMarks = 0
        private val network = Proxy.newProxyInstance(MeshNetworkGateway::class.java.classLoader,
            arrayOf(MeshNetworkGateway::class.java)) { _, method, _ ->
            when (method.name) {
                "getReportsOutboundProgress", "hasPendingTransfer" -> true
                "releaseStrandedTransfer" -> { releases++; true }
                else -> null
            }
        } as MeshNetworkGateway
        private val store = Proxy.newProxyInstance(MessageStore::class.java.classLoader,
            arrayOf(MessageStore::class.java)) { _, method, args ->
            when (method.name) {
                "isUnacknowledgedPrivateSend", "isOutstandingPrivateSend" -> true
                "markSent" -> { sentMarks++; Unit }
                "markPending" -> { pending += args!![0] as String; Unit }
                else -> Unit
            }
        } as MessageStore
        val watch = PrivateReceiptWatch(network, store, scope.backgroundScope, { retries++ }, { routeNow })
    }

    @Test fun strandedFirstHopWithAChangedRouteIsReleasedAndRetried() = runTest {
        val f = Fixture(this, listOf("Me#A", "Other#D", "Peer#C"))
        f.watch.beginTransfer("m", "tx", sent, media = true)
        f.watch.awaitReceipt("m")
        advanceTimeBy(MeshDeliveryConstants.PRIVATE_RECEIPT_POLL_MS + 1)
        assertEquals(1, f.releases)
        assertEquals(1, f.retries)
        assertEquals(listOf("m"), f.pending)
    }

    @Test fun strandedFirstHopOnTheSameRouteKeepsWaiting() = runTest {
        val f = Fixture(this, sent)
        f.watch.beginTransfer("m", "tx", sent, media = true)
        f.watch.awaitReceipt("m")
        advanceTimeBy(MeshDeliveryConstants.PRIVATE_RECEIPT_POLL_MS * 5)
        assertEquals(0, f.releases)
        assertEquals(0, f.retries)
    }

    @Test fun lateFailureOfAReleasedTransmissionDoesNotReviveTheMessage() = runTest {
        val f = Fixture(this, listOf("Me#A", "Other#D", "Peer#C"))
        f.watch.beginTransfer("m", "tx", sent, media = true)
        f.watch.awaitReceipt("m")
        advanceTimeBy(MeshDeliveryConstants.PRIVATE_RECEIPT_POLL_MS + 1)
        f.watch.onOutboundFrame(frame("tx", OutboundFrameEvent.Stage.FAILED))
        assertEquals(0, f.sentMarks)
        f.watch.onOutboundFrame(frame("resumed", OutboundFrameEvent.Stage.QUEUED))
        assertEquals(1, f.sentMarks)
    }
}
