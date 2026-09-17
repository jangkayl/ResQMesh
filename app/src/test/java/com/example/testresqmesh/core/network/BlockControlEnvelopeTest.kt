package com.example.testresqmesh.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlockControlEnvelopeTest {
    @Test fun requestEnvelopeRoundTripsAllRoutingIndependentFields() {
        val envelope = BlockControlEnvelope(
            kind = BlockControlKind.REQUEST,
            operationId = "operation-1",
            initiatorName = "A [NODE]#A001",
            targetName = "B [NODE]#B001",
            replyPublicKey = "public-key"
        )

        assertEquals(envelope, BlockControlEnvelope.decode(envelope.encode()))
    }

    @Test fun malformedOrIncompleteEnvelopeIsRejected() {
        assertNull(BlockControlEnvelope.decode("not-json"))
        assertNull(BlockControlEnvelope.decode("{\"kind\":\"REQUEST\"}"))
    }
}
