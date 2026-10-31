package edu.cit.resqmesh.feature.radar.viewmodel

import edu.cit.resqmesh.core.model.ConnectedDevice
import edu.cit.resqmesh.core.model.KnownNode
import edu.cit.resqmesh.core.ui.model.NodeKind
import edu.cit.resqmesh.core.ui.model.RadarUiState
import edu.cit.resqmesh.core.ui.peers.classifyRadarNodes
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentOfflineRouteTest {

    private val cai = "Cai [NODE]#C3"
    private val dee = "Dee [NODE]#D4"
    private val routedCai = KnownNode(cai, isDirect = false, lastSeen = 0L)
    private val routedDee = KnownNode(dee, isDirect = false, lastSeen = 0L)

    private fun names(devices: List<ConnectedDevice>) = devices.map { it.name }

    @Test
    fun routedPeerWithoutRouteBecomesOffline() {
        val offline = recentOfflineAfterRouteChange(
            emptyList(), listOf(routedCai, routedDee), listOf(routedDee), emptyList()
        )

        assertEquals(listOf(cai), names(offline))
        val kind = classifyRadarNodes(RadarUiState(recentOfflineDevices = offline)).single().kind
        assertEquals(NodeKind.OFFLINE, kind)
    }

    @Test
    fun losingFirstHopMarksEveryRoutedPeerBehindIt() {
        val offline = recentOfflineAfterRouteChange(
            emptyList(), listOf(routedCai, routedDee), emptyList(), emptyList()
        )

        assertEquals(listOf(cai, dee), names(offline))
    }

    @Test
    fun routedPeerThatBecomesDirectIsNotOffline() {
        val direct = KnownNode(cai, isDirect = true, lastSeen = 0L)

        assertEquals(
            emptyList<String>(),
            names(recentOfflineAfterRouteChange(emptyList(), listOf(routedCai), listOf(direct), emptyList()))
        )
    }

    @Test
    fun connectedPeerMissingFromRoutesIsNotOffline() {
        val link = ConnectedDevice(endpointId = "c", name = cai)

        assertEquals(
            emptyList<String>(),
            names(recentOfflineAfterRouteChange(emptyList(), listOf(routedCai), emptyList(), listOf(link)))
        )
    }

    @Test
    fun peerWithNewRouteLeavesOfflineList() {
        val offline = listOf(ConnectedDevice(endpointId = "", name = cai))

        assertEquals(
            emptyList<String>(),
            names(recentOfflineAfterRouteChange(offline, emptyList(), listOf(routedCai), emptyList()))
        )
    }

    @Test
    fun alreadyOfflinePeerIsNotDuplicated() {
        val offline = listOf(ConnectedDevice(endpointId = "", name = cai))

        assertEquals(
            listOf(cai),
            names(recentOfflineAfterRouteChange(offline, listOf(routedCai), emptyList(), emptyList()))
        )
    }
}
