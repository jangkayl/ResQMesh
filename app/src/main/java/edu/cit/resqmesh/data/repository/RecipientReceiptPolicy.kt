package edu.cit.resqmesh.data.repository

import edu.cit.resqmesh.core.model.NodeIdentity

/** Structural guard, not cryptographic verification of the reader's claim. */
object RecipientReceiptPolicy {
    private const val MAX_READER_LENGTH = 256

    /** Receipt status words that must never be accepted as a reader name. */
    private val RESERVED_READER_NAMES = setOf("PENDING", "FAILED", "ME")

    fun accepts(isMine: Boolean, targetName: String?, reader: String, isPrivate: Boolean): Boolean {
        if (!isMine || !isWellFormedReader(reader) || (targetName != null) != isPrivate) return false
        if (!isPrivate) return true
        val intended = NodeIdentity.idOf(targetName) ?: return false
        return intended == NodeIdentity.idOf(reader)
    }

    /** Non-blank, bounded, already trimmed, no separators or control characters, not a reserved word. */
    private fun isWellFormedReader(reader: String): Boolean =
        reader.isNotBlank() && reader.length <= MAX_READER_LENGTH && reader == reader.trim() &&
            reader.none { it == ',' || it.isISOControl() } &&
            reader.uppercase() !in RESERVED_READER_NAMES
}
