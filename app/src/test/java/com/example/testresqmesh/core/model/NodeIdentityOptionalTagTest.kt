package com.example.testresqmesh.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeIdentityOptionalTagTest {
    @Test fun blankAndLegacyDefaultTagsDoNotAppearInNewIdentity() {
        assertEquals("Ari#A1B2", NodeIdentity.qualifiedName("Ari", "", "A1B2"))
        assertEquals("Ari#A1B2", NodeIdentity.qualifiedName("Ari", "NODE", "A1B2"))
        assertEquals("", NodeIdentity.optionalTag("node"))
        assertEquals("Ari", NodeIdentity.displayNameOf("Ari [NODE]#A1B2"))
    }

    @Test fun customTagRemainsOptionalAndVisible() {
        assertEquals("Ari [TEAM1]#A1B2", NodeIdentity.qualifiedName("Ari", "TEAM1", "A1B2"))
        assertEquals("Ari [TEAM1]", NodeIdentity.displayNameOf("Ari [TEAM1]#A1B2"))
    }

    @Test fun stableIdsSeparateMatchingDisplayNamesAndResolveTruncatedAdvertisement() {
        assertFalse(NodeIdentity.matches("Ari#A1B2", "Ari#C3D4"))
        assertTrue(NodeIdentity.matches("Ar#A1B2", "Ari#A1B2"))
        assertEquals("Ari#A1B2", NodeIdentity.preferredName(listOf("Ar#A1B2", "Ari#A1B2")))
        assertEquals("Ari#A1B2", NodeIdentity.preferredName(listOf("Ari [NODE]#A1B2", "Ari#A1B2")))
    }

    @Test fun currentNameNeverCrossesStableIdentity() {
        assertEquals("Alex#A1", NodeIdentity.currentName("Alexander#A1", mapOf("A1" to "Alex#A1")))
        assertEquals("Alexander#A1", NodeIdentity.currentName("Alexander#A1", mapOf("A1" to "Alex#B2")))
        assertEquals("Alexander#A1", NodeIdentity.currentName("Alexander#A1", emptyMap()))
    }
}
