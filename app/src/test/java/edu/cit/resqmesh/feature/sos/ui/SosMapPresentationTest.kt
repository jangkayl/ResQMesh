package edu.cit.resqmesh.feature.sos.ui

import edu.cit.resqmesh.core.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SosMapPresentationTest {

    private fun message(id: String = "msg-1", isSOS: Boolean = false) = ChatMessage(
        id = id,
        senderName = "Ana",
        text = "",
        imageBase64 = null,
        audioBase64 = null,
        locationLat = 10.3,
        locationLng = 123.9,
        isMine = false,
        isSOS = isSOS
    )

    @Test
    fun realSosReadsAsSosWithSentTime() {
        val sos = message(isSOS = true)
        assertEquals("SOS location", mapLocationTitle(sos))
        assertEquals("SOS", mapPinLabel(sos))
        assertNotNull(mapSentTime(sos))
    }

    @Test
    fun sharedLocationNeverReadsAsSos() {
        val shared = message(id = "view_map_1")
        assertEquals("Shared location", mapLocationTitle(shared))
        assertEquals("PIN", mapPinLabel(shared))
        assertNull(mapSentTime(shared))
    }

    @Test
    fun incidentPreviewHasItsOwnTitle() {
        assertEquals("Incident location", mapLocationTitle(message(id = "${INCIDENT_MAP_ID_PREFIX}1")))
    }

    @Test
    fun directionNamesCoverEveryQuadrantAndWrapAtNorth() {
        assertEquals("North", bearingToDirectionName(0.0))
        assertEquals("North", bearingToDirectionName(350.0))
        assertEquals("Northeast", bearingToDirectionName(45.0))
        assertEquals("South", bearingToDirectionName(180.0))
        assertEquals("Northwest", bearingToDirectionName(315.0))
    }

    @Test
    fun distanceUsesMetersThenKilometersAndNamesTheStraightLine() {
        val (near, detail) = describeDistance(350f, 45f)
        assertEquals("About 350 m away", near)
        assertEquals("Northeast of you. In a straight line, not by road.", detail)
        assertEquals("About 1.2 km away", describeDistance(1234f, 0f).first)
    }

    @Test
    fun copiedCoordinatesAreDecimalDegrees() {
        assertEquals("10.315696, 123.885437", copyableCoordinates(10.315696, 123.885437))
        assertEquals("-12.046374, -77.042793", copyableCoordinates(-12.046374, -77.042793))
    }
}
