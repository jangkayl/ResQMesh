package edu.cit.resqmesh.feature.home.ui

import edu.cit.resqmesh.core.ui.model.NodeItemData
import edu.cit.resqmesh.core.ui.model.NodeKind
import edu.cit.resqmesh.feature.home.ui.components.homeStoryPeers
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeStoryPeersTest {

    private fun node(name: String, kind: NodeKind, isBlocked: Boolean = false) = NodeItemData(
        endpointId = "",
        name = name,
        label = name,
        status = "",
        kind = kind,
        isBlocked = isBlocked
    )

    @Test
    fun offlinePeersStayVisibleAfterActivePeers() {
        val peers = homeStoryPeers(
            listOf(
                node("Ari", NodeKind.OFFLINE),
                node("Bea", NodeKind.DISCOVERED),
                node("Cai", NodeKind.DIRECT)
            )
        )

        assertEquals(listOf("Bea", "Cai", "Ari"), peers.map { it.name })
    }

    @Test
    fun blockedPeersStayHidden() {
        val peers = homeStoryPeers(
            listOf(
                node("Ari", NodeKind.BLOCKED_OFFLINE, isBlocked = true),
                node("Bea", NodeKind.OFFLINE, isBlocked = true),
                node("Cai", NodeKind.DIRECT, isBlocked = true)
            )
        )

        assertEquals(emptyList<String>(), peers.map { it.name })
    }
}
