package edu.cit.resqmesh.feature.radar.ui

import edu.cit.resqmesh.core.ui.components.feedback.ResQStatusTone
import edu.cit.resqmesh.core.ui.model.NodeItemData
import edu.cit.resqmesh.core.ui.model.NodeKind
import edu.cit.resqmesh.feature.radar.ui.components.blockDialogMessage
import edu.cit.resqmesh.feature.radar.ui.components.blockNotice
import edu.cit.resqmesh.feature.radar.ui.components.messageActionLabel
import edu.cit.resqmesh.feature.radar.ui.components.networkStatus
import edu.cit.resqmesh.feature.radar.ui.components.networkStatusTone
import edu.cit.resqmesh.feature.radar.ui.components.networkSummaryLabel
import edu.cit.resqmesh.feature.radar.ui.components.showsBlockAction
import edu.cit.resqmesh.feature.radar.ui.components.showsConnectAction
import edu.cit.resqmesh.feature.radar.ui.components.showsDisconnectAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkPresentationTest {
    private fun node(
        kind: NodeKind,
        blocked: Boolean = false,
        name: String = "Bea [NODE]#BBBB",
        blockedByThem: Boolean = false,
        releasedLocally: Boolean = false
    ) = NodeItemData(
        endpointId = "ep", name = name, label = "Bea", status = "", kind = kind,
        isBlocked = blocked, blockedByThem = blockedByThem, releasedLocally = releasedLocally
    )

    @Test fun blockedChipsReadWhereThePersonIs() {
        assertEquals("Blocked · Nearby", networkStatus(node(NodeKind.DISCOVERED, blocked = true)))
        assertEquals("Blocked · Nearby", networkStatus(node(NodeKind.SYNCING, blocked = true)))
        assertEquals("Blocked · Relayed", networkStatus(node(NodeKind.RELAY, blocked = true)))
        assertEquals("Blocked · Relayed", networkStatus(node(NodeKind.HOPPED, blocked = true)))
        assertEquals("Blocked · Offline", networkStatus(node(NodeKind.OFFLINE, blocked = true)))
        assertEquals("Blocked · Offline", networkStatus(node(NodeKind.BLOCKED_OFFLINE, blocked = true)))
    }

    @Test fun blockedLinkStillPresentIsNotCalledDirectOrConnecting() {
        assertEquals("Blocked · Closing link", networkStatus(node(NodeKind.DIRECT, blocked = true)))
        assertEquals("Blocked · Closing link", networkStatus(node(NodeKind.UNRESPONSIVE, blocked = true)))
        assertEquals("Blocked · Closing link", networkStatus(node(NodeKind.HANDSHAKING, blocked = true)))
    }

    @Test fun unblockedChipsAndTonesAreUnchanged() {
        assertEquals("Direct", networkStatus(node(NodeKind.DIRECT)))
        assertEquals(ResQStatusTone.Success, networkStatusTone(node(NodeKind.DIRECT)))
        assertEquals(ResQStatusTone.Critical, networkStatusTone(node(NodeKind.DISCOVERED, blocked = true)))
    }

    @Test fun summaryLabelNeverCountsBlockedPeers() {
        assertEquals("No people nearby", networkSummaryLabel(listOf(node(NodeKind.DIRECT, blocked = true))))
        assertEquals(
            "1 relayed",
            networkSummaryLabel(listOf(node(NodeKind.DIRECT, blocked = true), node(NodeKind.RELAY)))
        )
        val nodes = listOf(node(NodeKind.DIRECT), node(NodeKind.DIRECT, blocked = true))
        assertEquals("1 direct", networkSummaryLabel(nodes))
    }

    @Test fun noticeNamesWhoBlockedAndWarnsAfterOneSidedUnblock() {
        assertEquals("They blocked you", blockNotice(node(NodeKind.DISCOVERED, blocked = true, blockedByThem = true)))
        assertEquals("Your direct link is blocked", blockNotice(node(NodeKind.DISCOVERED, blocked = true)))
        assertTrue(blockNotice(node(NodeKind.DISCOVERED, releasedLocally = true))!!.contains("until they unblock"))
        assertNull(blockNotice(node(NodeKind.DISCOVERED)))
    }

    @Test fun blockedPeersOfferNoConnectOrDisconnect() {
        assertFalse(showsConnectAction(node(NodeKind.DISCOVERED, blocked = true)))
        assertTrue(showsConnectAction(node(NodeKind.DISCOVERED)))
        assertFalse(showsDisconnectAction(node(NodeKind.DIRECT, blocked = true)))
        assertTrue(showsDisconnectAction(node(NodeKind.DIRECT)))
    }

    @Test fun blockIsHiddenOnHandshakingAndPlaceholderRowsButUnblockStays() {
        assertFalse(showsBlockAction(node(NodeKind.HANDSHAKING, name = "Bea [NODE]#BBBB")))
        assertFalse(showsBlockAction(node(NodeKind.DISCOVERED, name = "Unknown Node")))
        assertTrue(showsBlockAction(node(NodeKind.DISCOVERED)))
        assertTrue(showsBlockAction(node(NodeKind.HANDSHAKING, blocked = true)))
    }

    @Test fun messageButtonWaitsForRelayWhenBlockedWithoutRoute() {
        assertEquals("SEND MESSAGE", messageActionLabel(node(NodeKind.DISCOVERED)))
        assertEquals("MESSAGE · WAITING FOR A RELAY", messageActionLabel(node(NodeKind.DISCOVERED, blocked = true)))
        assertEquals(
            "MESSAGE VIA MESH HOP",
            messageActionLabel(node(NodeKind.HOPPED, blocked = true).copy(route = listOf("Me", "Cai", "Bea")))
        )
    }

    @Test fun blockDialogWarnsWithMayWhenNoAlternateRoute() {
        assertFalse(blockDialogMessage(hasAlternateRoute = true).contains("No other route"))
        assertTrue(blockDialogMessage(hasAlternateRoute = false).contains("No other route to them may exist"))
    }
}
