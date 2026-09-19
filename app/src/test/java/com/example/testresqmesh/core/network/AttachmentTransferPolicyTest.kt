package com.example.testresqmesh.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class AttachmentTransferPolicyTest {
    @Test fun `image bounds produce at most 320 one kib chunks`() {
        assertEquals(320, AttachmentTransferPolicy.chunkCount(AttachmentTransferPolicy.MAX_IMAGE_BYTES))
        assertEquals(0, AttachmentTransferPolicy.chunkCount(AttachmentTransferPolicy.MAX_IMAGE_BYTES + 1))
    }

    @Test fun `only expected chunk advances a recipient checkpoint`() {
        assertEquals(AttachmentTransferPolicy.SequenceResult.ACCEPT, AttachmentTransferPolicy.sequenceResult(4, 4, 10))
        assertEquals(AttachmentTransferPolicy.SequenceResult.REPEAT_CHECKPOINT, AttachmentTransferPolicy.sequenceResult(4, 3, 10))
        assertEquals(AttachmentTransferPolicy.SequenceResult.REJECT, AttachmentTransferPolicy.sequenceResult(10, 10, 10))
    }

    @Test fun `attachment chunks stay below controls in transport priority`() {
        assertEquals(AttachmentTransferPolicy.Traffic.LOW_ATTACHMENT, AttachmentTransferPolicy.trafficFor("ATTACHMENT_CHUNK"))
        assertEquals(AttachmentTransferPolicy.Traffic.PRIORITY_CONTROL, AttachmentTransferPolicy.trafficFor("ATTACHMENT_CHECKPOINT"))
    }
}
