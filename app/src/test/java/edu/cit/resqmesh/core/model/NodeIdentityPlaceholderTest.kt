package edu.cit.resqmesh.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeIdentityPlaceholderTest {
    @Test fun blankAndUnidentifiedPlaceholdersCarryNoIdentity() {
        listOf(null, "", "   ", "Unknown Node", " unknown node ", "Unknown").forEach {
            assertTrue("$it", NodeIdentity.isPlaceholder(it))
        }
    }

    @Test fun realNamesContainingUnknownAreNotPlaceholders() {
        listOf("Unknown Hero#AB12", "unknownboy#AB12", "Unknown Hero", "Mr Unknown", "Unknown Node#AB12").forEach {
            assertFalse(it, NodeIdentity.isPlaceholder(it))
        }
    }
}
