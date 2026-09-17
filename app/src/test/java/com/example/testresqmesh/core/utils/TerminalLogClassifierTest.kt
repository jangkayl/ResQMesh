package com.example.testresqmesh.core.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalLogClassifierTest {
    @Test fun classifiesActiveBleLifecycleAsConnection() {
        assertEquals(
            TerminalLogCategory.CONNECTION,
            TerminalLogClassifier.categoryFor("BLE_MESH", "Link 4 CLIENT AA:BB:CC:DD:EE:FF: READY")
        )
    }

    @Test fun classifiesCurrentSyncTransportRoutingAndSecurityTags() {
        assertEquals(
            TerminalLogCategory.SYNC,
            TerminalLogClassifier.categoryFor("BLE_MESH", "Sending full SYSTEM pulse to 2 GATT endpoints")
        )
        assertEquals(
            TerminalLogCategory.TRANSPORT,
            TerminalLogClassifier.categoryFor("BLE_MESH", "L2CAP Sent 200 bytes directly")
        )
        assertEquals(
            TerminalLogCategory.ROUTING,
            TerminalLogClassifier.categoryFor("PayloadDispatcher", "ROUTE (Relay): forwarding")
        )
        assertEquals(
            TerminalLogCategory.SECURITY,
            TerminalLogClassifier.categoryFor("MeshNetwork_E2EE", "E2EE SUCCESS")
        )
    }

    @Test fun classifiesFailuresWithoutLegacyErrorTags() {
        assertEquals(TerminalLogLevel.ERROR, TerminalLogClassifier.levelFor("Error parsing Protobuf payload"))
        assertEquals(TerminalLogLevel.WARN, TerminalLogClassifier.levelFor("Handshake watchdog timed out"))
    }

    @Test fun shortensEndpointForTerminalDisplay() {
        assertEquals("…EE:FF", TerminalLogEntry.shortEndpoint("AA:BB:CC:DD:EE:FF"))
    }
}
