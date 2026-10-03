package com.example.testresqmesh.core.network

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TransferJournalTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun record(size: Int = 1024, outgoing: Boolean = false, id: String = "transfer") =
        TransferRecord("peer", id, "digest", size, outgoing, "message", "message", false, 100_000)

    @Test fun committedPiecesAndManifestSurviveReopening() {
        val root = temporary.newFolder(); val journal = TransferJournal(root) { 1000 }
        val record = record(1025)
        assertTrue(journal.create(record))
        journal.putPiece(record, 1, byteArrayOf(42))
        journal.putPiece(record, 0, ByteArray(1024) { 1 })
        val reopened = TransferJournal(root) { 1000 }
        assertEquals(1, reopened.snapshot(false).size)
        assertEquals(42.toByte(), reopened.assemble(record).last())
    }

    @Test fun duplicatePieceNeverOverwritesCommittedBytes() {
        val journal = TransferJournal(temporary.newFolder()) { 1000 }; val record = record()
        journal.create(record)
        journal.putPiece(record, 0, ByteArray(1024) { 1 })
        journal.putPiece(record, 0, ByteArray(1024) { 2 })
        assertEquals(1.toByte(), journal.assemble(record).first())
    }

    @Test fun fullOrdinaryJournalStillAdmitsReservedControl() {
        val journal = TransferJournal(temporary.newFolder()) { 1000 }
        assertTrue(journal.create(record(TransferJournal.MAX_BYTES)))
        assertFalse(journal.create(record(1, id = "overflow")))
        assertTrue(journal.create(record(100, id = "receipt").copy(priority = true)))
    }

    @Test fun expiryReleasesPayloadAndReservation() {
        var time = 1000L; val journal = TransferJournal(temporary.newFolder()) { time }
        val record = record(outgoing = true).copy(expiresAt = 2000)
        journal.create(record, ByteArray(1024)); time = 2000; journal.prune()
        assertTrue(journal.snapshot(true).isEmpty())
        assertTrue(journal.create(record.copy(expiresAt = 3000), ByteArray(1024)))
    }

    @Test fun applicationCommitCompactsPiecesButRetainsDuplicateMarker() {
        val root = temporary.newFolder(); val journal = TransferJournal(root) { 1000 }
        val record = record(); journal.create(record); journal.putPiece(record, 0, ByteArray(1024))
        val complete = journal.complete(record); journal.stored(complete, byteArrayOf(1, 2))
        val reopened = TransferJournal(root) { 1000 }
        assertTrue(reopened.snapshot(false).single().stored)
        assertFalse(reopened.hasPiece(record, 0))
        assertArrayEquals(byteArrayOf(1, 2), reopened.snapshot(false).single().response)
    }

    @Test fun interruptedMetadataReplacementRecoversCommittedBackup() {
        val root = temporary.newFolder(); val journal = TransferJournal(root) { 1000 }
        journal.create(record())
        val file = java.io.File(root.listFiles()!!.single(), "record")
        assertTrue(file.renameTo(java.io.File(file.path + ".bak")))
        val reopened = TransferJournal(root) { 1000 }
        assertEquals(1, reopened.snapshot(false).size)
        assertEquals(0, reopened.recoveryFailures)
    }

    @Test fun corruptMetadataIsReportedAndDoesNotOwnCapacityForever() {
        val root = temporary.newFolder(); val journal = TransferJournal(root) { 1000 }
        journal.create(record())
        java.io.File(root.listFiles()!!.single(), "record").writeBytes(byteArrayOf(127))
        val reopened = TransferJournal(root) { 1000 }
        assertEquals(1, reopened.recoveryFailures)
        assertTrue(reopened.snapshot(false).isEmpty())
    }
}
