package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.model.NodeIdentity

/** Structural guard, not cryptographic verification of the reader's claim. */
object RecipientReceiptPolicy {
    fun accepts(isMine: Boolean, targetName: String?, reader: String, isPrivate: Boolean): Boolean {
        if (!isMine || reader.isBlank() || reader.length > 256 || reader != reader.trim() ||
            reader.any { it == ',' || it.isISOControl() } ||
            reader.uppercase() in setOf("PENDING", "FAILED", "ME") || (targetName != null) != isPrivate) return false
        if (!isPrivate) return true
        val intended = NodeIdentity.idOf(targetName) ?: return false
        return intended == NodeIdentity.idOf(reader)
    }
}
