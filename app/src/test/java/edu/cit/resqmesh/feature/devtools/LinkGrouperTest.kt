package edu.cit.resqmesh.feature.devtools

import edu.cit.resqmesh.core.network.LinkDiagnostic
import edu.cit.resqmesh.feature.devtools.model.LinkGrouper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkGrouperTest {
    @Suppress("LongParameterList")
    private fun link(
        endpoint: String,
        nodeId: String? = null,
        name: String? = null,
        role: String = "CLIENT",
        state: String = "READY",
        generation: Long = 1,
        transport: String = "GATT"
    ) = LinkDiagnostic(endpoint, role, generation, state, nodeId, name, false, transport, 0L)

    @Test
    fun sameNodeOnTwoMacsIsOnePeer() {
        val groups = LinkGrouper.group(
            listOf(
                link("AA:AA:AA:AA:AA:01", nodeId = "ABCD1234", name = "Bob#ABCD1234"),
                link("AA:AA:AA:AA:AA:02", nodeId = "abcd1234", role = "SERVER", generation = 2)
            )
        )
        assertEquals(1, groups.size)
        assertEquals(2, groups[0].paths.size)
        assertEquals("Bob (ABCD1234)", groups[0].title)
    }

    @Test
    fun placeholderNamesNeverGroup() {
        val groups = LinkGrouper.group(
            listOf(
                link("AA:AA:AA:AA:AA:01", name = "Unknown Node"),
                link("AA:AA:AA:AA:AA:02", name = "Unknown Node")
            )
        )
        assertEquals(2, groups.size)
        assertTrue(groups.none { it.title.contains("Unknown") })
    }

    @Test
    fun unidentifiedPeerShowsIdentifyingNeverUnknown() {
        val group = LinkGrouper.group(listOf(link("AA:BB:CC:DD:EE:FF", name = "Unknown Node"))).single()
        assertEquals("Identifying… …EE:FF", group.title)
        assertFalse(group.title.contains("Unknown", ignoreCase = true))
    }

    @Test
    fun nameFallsBackToPeerNamesThenShortId() {
        val noName = link("AA:AA:AA:AA:AA:01", nodeId = "ABCD1234", name = "Unknown Node")
        val carol = mapOf("ABCD1234" to "Carol#ABCD1234")
        assertEquals("Carol (ABCD1234)", LinkGrouper.group(listOf(noName), carol).single().title)
        assertEquals("ABCD1234", LinkGrouper.group(listOf(noName), mapOf("ABCD1234" to "Unknown Node")).single().title)
        assertEquals("ABCD1234", LinkGrouper.group(listOf(noName)).single().title)
    }

    @Test
    fun linkNameWinsOverPeerNames() {
        val l = link("AA:AA:AA:AA:AA:01", nodeId = "ABCD1234", name = "Bob#ABCD1234")
        val other = mapOf("ABCD1234" to "Other#ABCD1234")
        assertEquals("Bob (ABCD1234)", LinkGrouper.group(listOf(l), other).single().title)
    }

    @Test
    fun readyPathComesFirst() {
        val group = LinkGrouper.group(
            listOf(
                link("AA:AA:AA:AA:AA:01", nodeId = "ABCD1234", state = "CONFIGURING", generation = 1),
                link("AA:AA:AA:AA:AA:02", nodeId = "ABCD1234", state = "READY", role = "SERVER", generation = 2)
            )
        ).single()
        assertEquals("READY", group.paths.first().state)
        assertEquals(1, group.readyCount)
    }

    @Test
    fun keyIsStablePerPeerAcrossPaths() {
        val a = LinkGrouper.group(listOf(link("AA:AA:AA:AA:AA:01", nodeId = "ABCD1234"))).single().key
        val b = LinkGrouper.group(
            listOf(link("AA:AA:AA:AA:AA:09", nodeId = "ABCD1234"), link("AA:AA:AA:AA:AA:01", nodeId = "ABCD1234"))
        ).single().key
        assertEquals(a, b)
    }

    @Test
    fun differentNodesStaySeparate() {
        val groups = LinkGrouper.group(
            listOf(link("AA:AA:AA:AA:AA:01", nodeId = "AAAA0001"), link("AA:AA:AA:AA:AA:02", nodeId = "BBBB0002"))
        )
        assertEquals(2, groups.size)
    }
}
