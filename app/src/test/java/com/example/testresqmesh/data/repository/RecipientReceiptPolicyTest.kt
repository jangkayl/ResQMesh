package com.example.testresqmesh.data.repository

import org.junit.Assert.*
import org.junit.Test

class RecipientReceiptPolicyTest {
    @Test fun privateReceiptRequiresExactIntendedIdButAllowsRename() {
        assertTrue(RecipientReceiptPolicy.accepts(true, "Bob#B001", "Renamed#B001", true))
        assertFalse(RecipientReceiptPolicy.accepts(true, "Bob#B001", "Bob#C001", true))
        assertFalse(RecipientReceiptPolicy.accepts(true, "Bob#B001", "Bob#B00", true))
        assertFalse(RecipientReceiptPolicy.accepts(true, "Bob", "Bob", true))
    }
    @Test fun publicAndPrivateReceiptsCannotCrossMessageClasses() {
        assertFalse(RecipientReceiptPolicy.accepts(true, "Bob#B001", "Bob#B001", false))
        assertFalse(RecipientReceiptPolicy.accepts(true, null, "Bob#B001", true))
        assertTrue(RecipientReceiptPolicy.accepts(true, null, "Bob#B001", false))
    }
    @Test fun incomingRowsAndReaderStateInjectionAreRejected() {
        assertFalse(RecipientReceiptPolicy.accepts(false, "Bob#B001", "Bob#B001", true))
        for (reader in listOf("", " ", "Me", "PENDING", "FAILED", "Bob#B001,Other#C001", "Bob\n#B001")) {
            assertFalse(RecipientReceiptPolicy.accepts(true, null, reader, false))
        }
    }
}
