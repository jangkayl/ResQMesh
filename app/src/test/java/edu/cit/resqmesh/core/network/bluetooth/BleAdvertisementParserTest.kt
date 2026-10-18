package edu.cit.resqmesh.core.network.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BleAdvertisementParserTest {
    private fun parse(text: String) = BleAdvertisementParser.parse("AA:BB", text.toByteArray())

    @Test fun wellFormedAdvertisementIsParsed() {
        val ad = parse("0889F|2|A1B2|Bob")!!
        assertEquals("Bob#A1B2", ad.peerName)
        assertEquals("A1B2", ad.nodeId)
        assertEquals("0889F", ad.electionScore)
        assertEquals(2, ad.directConnections)
    }

    @Test fun legacyThreePartFormIsStillAccepted() {
        val ad = parse("0889F|1|Bob#A1B2")!!
        assertEquals("A1B2", ad.nodeId)
        assertEquals("Bob#A1B2", ad.peerName)
    }

    @Test fun malformedAdvertisementsAreRejectedWithoutThrowing() {
        listOf(
            "", "garbage", "0889F|2", "889F|2|A1B2|Bob", "0889F|x|A1B2|Bob", "0889F|-1|A1B2|Bob",
            "0889F|99|A1B2|Bob", "0889F|2|A1B|Bob", "0889F|2|A1B2C|Bob", "0889F|2|ZZZZ|Bob",
            "0889F|2|A1B2|", "0889F|2|A1B2|Bo\u0001b", "0889F|2|A1B2|Bo#b", "0889F|1|Bob#XYZ"
        ).forEach { assertNull(it, parse(it)) }
        assertNull(BleAdvertisementParser.parse("AA:BB", null))
        assertNull(BleAdvertisementParser.parse("AA:BB", ByteArray(27) { 'a'.code.toByte() }))
        assertNull(BleAdvertisementParser.parse("AA:BB", byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x7C)))
    }

    @Test fun nameTruncatedMidCharacterKeepsTheValidPrefix() {
        val bytes = "0889F|2|A1B2|Abé".toByteArray().let { it.copyOf(it.size - 1) }
        assertNotNull(BleAdvertisementParser.parse("AA:BB", bytes))
        assertEquals("Ab#A1B2", BleAdvertisementParser.parse("AA:BB", bytes)!!.peerName)
    }
}
