package edu.cit.resqmesh.feature.devtools

import edu.cit.resqmesh.core.logging.trace.LogTrace
import edu.cit.resqmesh.core.logging.trace.PacketTrace
import edu.cit.resqmesh.core.logging.trace.TraceDir
import edu.cit.resqmesh.core.logging.trace.TraceDropReason
import edu.cit.resqmesh.core.logging.trace.TraceLevel
import edu.cit.resqmesh.core.logging.trace.TraceScope
import edu.cit.resqmesh.core.logging.trace.TraceStage
import edu.cit.resqmesh.core.logging.trace.TraceType
import edu.cit.resqmesh.feature.devtools.export.LogSanitizer
import edu.cit.resqmesh.feature.devtools.export.TraceExporter
import edu.cit.resqmesh.feature.devtools.model.TraceCollapser
import edu.cit.resqmesh.feature.devtools.model.TraceFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevToolsModelTest {
    private var n = 0L
    @Suppress("LongParameterList")
    private fun pkt(
        type: TraceType = TraceType.PONG, dir: TraceDir = TraceDir.IN, stage: TraceStage = TraceStage.RECEIVED,
        src: String? = "AAAA1111", scope: TraceScope = TraceScope.PUBLIC, drop: TraceDropReason = TraceDropReason.NONE,
        transport: String? = "GATT", key: String = "k1"
    ) = PacketTrace(++n, n, stage, dir, scope, type, key, src, null, null, null, null, null, 10, transport, drop)

    private fun log(msg: String, tag: String = "BLE_MESH") =
        LogTrace(1, 1, TraceLevel.ERROR, 0, "ev", msg, tag, "Alice", "AA:BB:CC:DD:EE:FF", null, null, false)

    @Test fun collapsesAdjacentHeartbeatsOnly() {
        val rows = TraceCollapser.collapse(listOf(pkt(), pkt(), pkt(), pkt(TraceType.MESSAGE), pkt(TraceType.MESSAGE)))
        assertEquals(listOf(3, 1, 1), rows.map { it.count })
    }

    @Test fun differentPeerOrDirectionBreaksCollapse() {
        val rows = TraceCollapser.collapse(listOf(pkt(), pkt(src = "BBBB2222"), pkt(dir = TraceDir.OUT)))
        assertEquals(3, rows.size)
    }

    @Test fun failuresAndDropsNeverCollapse() {
        val rows = TraceCollapser.collapse(
            listOf(
                pkt(), pkt(stage = TraceStage.FAILED), pkt(stage = TraceStage.FAILED),
                pkt(drop = TraceDropReason.UNKNOWN), pkt()
            )
        )
        assertEquals(5, rows.size)
    }

    @Test fun filterMatchesFieldsAndNames() {
        val a = pkt(type = TraceType.MESSAGE, scope = TraceScope.PRIVATE, transport = "L2CAP", key = "abc123")
        val b = pkt(type = TraceType.PONG, dir = TraceDir.OUT)
        val all = listOf(a, b)
        assertEquals(listOf(a), TraceFilter(scope = TraceScope.PRIVATE).apply(all))
        assertEquals(listOf(b), TraceFilter(dir = TraceDir.OUT).apply(all))
        assertEquals(listOf(a), TraceFilter(type = TraceType.MESSAGE).apply(all))
        assertEquals(listOf(a), TraceFilter(transport = "l2cap").apply(all))
        assertEquals(listOf(a), TraceFilter(query = "ABC1").apply(all))
        assertEquals(all, TraceFilter(query = "carol").apply(all) { "Carol" })
        assertTrue(TraceFilter(query = "zzz").apply(all).isEmpty())
        assertEquals(all, TraceFilter().apply(all))
    }

    @Test fun sanitizerKeepsStrictMarkerLines() {
        val out = LogSanitizer.sanitize("FRAME_COMPLETED transmission=ab12 bytes=120 transport=GATT")
        assertEquals("FRAME_COMPLETED transmission=ab12 bytes=120 transport=GATT", out)
    }

    @Test fun sanitizerOmitsFreeTextAndHostileNames() {
        listOf(
            "Alice says hello", "lowercase x=1", "MARK name=Bob Smith", "MARK x=<script>", "MARK a=b\nSECOND c=d",
            "MARK x=" + "a".repeat(100), "\u0000MARK a=b", "Auto-rename: AA:BB:CC:DD:EE:FF is now Alice", ""
        ).forEach { assertEquals(it, LogSanitizer.OMITTED, LogSanitizer.sanitize(it)) }
    }

    @Test fun unknownKeysOmitTheWholeLine() {
        assertEquals(LogSanitizer.OMITTED, LogSanitizer.sanitize("PEER_UP alias=Bob id=ok"))
        assertEquals(LogSanitizer.OMITTED, LogSanitizer.sanitize("PEER_UP peerName=Bob id=ok"))
    }

    @Test fun sanitizerShortensMacs() {
        val mac = LogSanitizer.sanitize("LINK_UP endpoint=AA:BB:CC:DD:EE:FF")
        assertFalse(mac.contains("AA:BB:CC"))
        assertTrue(mac.contains("EE:FF"))
    }

    @Test fun idValuesAreShortened() {
        val out = LogSanitizer.sanitize(
            "SYNC node=0123456789ABCDEF message=FEDCBA9876543210 incident=abcdef0123456789 count=123456789012"
        )
        assertEquals("SYNC node=01234567 message=FEDCBA98 incident=abcdef01 count=123456789012", out)
    }

    @Test fun csvNeutralisesFormulaTabAndCr() {
        listOf("=1+1", "\r=1+1", "\t=1+1", "@x", "+1", "-1x").forEach {
            val cell = TraceExporter.csvCell(it)
            assertTrue(it, cell.startsWith("'") || cell.startsWith("\"'"))
        }
    }

    @Test fun forgedLinesAndHostileIdsStayOnOneLine() {
        val hostile = pkt(key = "k1\nFAKE_LINE a=b", src = "x\r=1+1")
        val text = TraceExporter.text(listOf(hostile), listOf(log("ok\nFORGED x=1")), emptyList())
        assertFalse(text.lines().any { it.startsWith("FAKE_LINE") || it.startsWith("FORGED") })
        assertFalse(text.contains("\r"))
        val csv = TraceExporter.csv(listOf(hostile), emptyList(), emptyList())
        assertFalse(csv.contains("=1+1"))
    }

    @Test fun exportContainsNoNamesOrFullMacs() {
        val logs = listOf(
            log("Alice joined at AA:BB:CC:DD:EE:FF"),
            log("LINK_UP endpoint=AA:BB:CC:DD:EE:FF peerName=Alice"),
            log("ok", tag = "Alice Smith")
        )
        val packets = listOf(pkt(), pkt(type = TraceType.MESSAGE, scope = TraceScope.PRIVATE))
        listOf(TraceExporter.text(packets, logs, logs), TraceExporter.csv(packets, logs, logs)).forEach { out ->
            assertFalse(out.contains("Alice"))
            assertFalse(out.contains("AA:BB:CC:DD:EE:FF"))
            assertTrue(out.contains(LogSanitizer.OMITTED))
            assertTrue(out.contains("AAAA1111"))
        }
    }
}
