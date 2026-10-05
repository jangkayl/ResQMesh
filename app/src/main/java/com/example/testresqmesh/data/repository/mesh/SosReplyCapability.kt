package com.example.testresqmesh.data.repository.mesh

/** Read the existing SOS reply policy without giving delivery owners another repository. */
internal class SosReplyCapability(private val lookup: suspend (String) -> Boolean) {
    suspend fun canReply(id: String): Boolean = lookup(id)
}
