package edu.cit.resqmesh.core.logging

import org.junit.Assert.assertEquals
import org.junit.Test

class LogFieldsTest {
    @Test fun typeKeepsOnlyUpperAsciiDigitsAndUnderscore() {
        assertEquals("SOS_EVENT", LogFields.type("SOS_EVENT"))
        assertEquals("A?B?C", LogFields.type("A\nB C"))
        assertEquals("????", LogFields.type("évil"))
    }

    @Test fun typeIsCappedAt24Chars() {
        assertEquals(24, LogFields.type("A".repeat(100)).length)
    }
}
