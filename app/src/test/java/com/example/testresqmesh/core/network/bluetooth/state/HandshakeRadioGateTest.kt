package com.example.testresqmesh.core.network.bluetooth.state

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandshakeRadioGateTest {
    @Test fun onlyFirstOwnerPausesAndLastOwnerResumes() {
        val gate = HandshakeRadioGate()
        assertTrue(gate.begin("client-1"))
        assertFalse(gate.begin("server-2"))
        assertTrue(gate.isActive())
        assertFalse(gate.finish("client-1"))
        assertTrue(gate.isActive())
        assertTrue(gate.finish("server-2"))
        assertFalse(gate.isActive())
    }

    @Test fun staleOwnerCannotResumeReplacementHandshake() {
        val gate = HandshakeRadioGate()
        assertTrue(gate.begin("client-old"))
        assertFalse(gate.begin("client-new"))
        assertFalse(gate.finish("client-old"))
        assertFalse(gate.finish("client-old"))
        assertTrue(gate.isActive())
        assertTrue(gate.finish("client-new"))
    }
}
