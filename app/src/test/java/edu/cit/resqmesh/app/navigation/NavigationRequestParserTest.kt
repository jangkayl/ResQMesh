package edu.cit.resqmesh.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationRequestParserTest {
    private fun parse(extras: NavigationRequestParser.RawExtras = NavigationRequestParser.RawExtras()) =
        NavigationRequestParser.parse(extras)

    private fun extras(
        sos: Boolean = false,
        map: Boolean = false,
        text: String? = null,
        id: String? = null
    ) = NavigationRequestParser.RawExtras(triggerSos = sos, viewSosMap = map, text = text, sosId = id)

    private fun sosWith(chatNode: String? = null, incidentId: String? = null) =
        NavigationRequestParser.RawExtras(triggerSos = true, chatNode = chatNode, incidentId = incidentId)

    private fun chat(node: String) = NavigationRequestParser.RawExtras(chatNode = node)

    private fun incident(id: String) = NavigationRequestParser.RawExtras(incidentId = id)

    @Test fun noExtrasYieldsNoRequest() = assertNull(parse())

    @Test fun sosTextIsSanitizedAndCapped() {
        val r = parse(extras(sos = true, text = "help\u0007\n" + "x".repeat(5000))) as NavigationRequest.OpenSos
        assertEquals(NavigationRequestParser.MAX_SOS_TEXT, r.text!!.length)
        assertTrue(r.text!!.none { it.isISOControl() })
    }

    @Test fun badSosIdIsDropped() {
        val r = parse(extras(map = true, id = "a b;<x>")) as NavigationRequest.OpenSos
        assertNull(r.sosId)
        assertTrue(r.viewMap)
    }

    @Test fun chatRequiresNodeIdSuffix() {
        assertEquals(NavigationRequest.OpenChat("Ann [R]#AB12"), parse(chat("Ann [R]#AB12")))
        assertNull(parse(chat("Ann")))
        assertNull(parse(chat("Ann#")))
        assertNull(parse(chat("Ann#bad id!")))
        assertNull(parse(chat("  ")))
    }

    @Test fun sosTakesPrecedenceOverChat() {
        assertTrue(parse(sosWith(chatNode = "Ann#AB12")) is NavigationRequest.OpenSos)
    }

    @Test fun incidentRequiresSafeId() {
        assertEquals(NavigationRequest.OpenIncident("a1-B2_c.3:4"), parse(incident(" a1-B2_c.3:4 ")))
        assertNull(parse(incident("a b;<x>")))
        val capped = NavigationRequest.OpenIncident("x".repeat(NavigationRequestParser.MAX_SOS_ID))
        assertEquals(capped, parse(incident("x".repeat(200))))
        assertNull(parse(incident("  ")))
    }

    @Test fun sosTakesPrecedenceOverIncident() {
        assertTrue(parse(sosWith(incidentId = "inc-1")) is NavigationRequest.OpenSos)
    }
}
