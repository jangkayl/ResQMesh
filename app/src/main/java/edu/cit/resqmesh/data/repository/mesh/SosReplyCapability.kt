package edu.cit.resqmesh.data.repository.mesh

/** Read the existing SOS reply policy without giving delivery owners another repository. */
internal class SosReplyCapability(
    private val lookup: suspend (String) -> Boolean,
    private val known: suspend (String) -> Boolean = lookup
) {
    suspend fun canReply(id: String): Boolean = lookup(id)
    /** True for any alert this phone holds, ended ones included: their history stays readable. */
    suspend fun isKnown(id: String): Boolean = known(id)
}
