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
        id: String? = null,
        chat: String? = null
    ) = NavigationRequestParser.RawExtras(
        triggerSos = sos, viewSosMap = map, text = text, sosId = id, chatNode = chat
    )

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
        assertEquals(NavigationRequest.OpenChat("Ann [R]#AB12"), parse(extras(chat = "Ann [R]#AB12")))
        assertNull(parse(extras(chat = "Ann")))
        assertNull(parse(extras(chat = "Ann#")))
        assertNull(parse(extras(chat = "Ann#bad id!")))
        assertNull(parse(extras(chat = "  ")))
    }

    @Test fun sosTakesPrecedenceOverChat() {
        assertTrue(parse(extras(sos = true, chat = "Ann#AB12")) is NavigationRequest.OpenSos)
    }
}
