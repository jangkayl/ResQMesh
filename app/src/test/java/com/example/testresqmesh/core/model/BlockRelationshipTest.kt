package com.example.testresqmesh.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockRelationshipTest {
    @Test fun activeLocalAndRemoteRelationshipsDenyOnlyDirectLinks() {
        val local = BlockRelationship("B [NODE]#B001", "op", BlockRelationshipOrigin.LOCAL, BlockRelationshipStatus.PENDING_ACK, 1)
        val remote = local.copy(origin = BlockRelationshipOrigin.REMOTE, status = BlockRelationshipStatus.CONFIRMED)

        assertTrue(local.deniesDirectLink)
        assertTrue(remote.deniesDirectLink)
    }

    @Test fun localReleaseDoesNotContinueToDenyDirectLink() {
        val released = BlockRelationship("B [NODE]#B001", "op", BlockRelationshipOrigin.LOCAL, BlockRelationshipStatus.RELEASED_LOCALLY, 1)

        assertFalse(released.deniesDirectLink)
    }
}
