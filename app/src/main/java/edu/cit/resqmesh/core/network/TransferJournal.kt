package edu.cit.resqmesh.core.network

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf

@Serializable
internal data class TransferRecord(
    val peer: String, val id: String, val digest: String, val size: Int,
    val outgoing: Boolean, val payloadId: String, val messageId: String,
    val priority: Boolean, val expiresAt: Long,
    val durableRelay: Boolean = false, val complete: Boolean = false,
    val stored: Boolean = false, val response: ByteArray? = null,
    val relayTargetsSet: Boolean = false, val relayTargets: List<String> = emptyList(),
    val forwardedPeers: List<String> = emptyList(),
    val applicationStored: Boolean = false,
    val requiresRelayRoster: Boolean = false
)

/** App-private, backup-excluded custody. Caller serializes IO on one worker, never the UI thread. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
internal class TransferJournal(private val root: File, private val now: () -> Long) {
    private val records = linkedMapOf<String, TransferRecord>()
    var recoveryFailures: Int = 0
        private set

    init {
        root.mkdirs()
        root.listFiles().orEmpty().filter { it.isDirectory }.forEach { directory ->
            runCatching {
                val file = recover(File(directory, "record"))
                val record = ProtoBuf.decodeFromByteArray<TransferRecord>(file.readBytes())
                require(record.size in 1..MAX_BYTES && record.id.length <= MAX_KEY_FIELD_LENGTH && record.peer.length <= MAX_KEY_FIELD_LENGTH)
                require(directory.name == key(record))
                records[key(record)] = record
            }.onFailure { recoveryFailures++; removeDirectory(directory) }
        }
        prune()
    }

    fun snapshot(outgoing: Boolean): List<TransferRecord> = records.values.filter { it.outgoing == outgoing }
    fun find(peer: String, id: String, outgoing: Boolean): TransferRecord? =
        records.values.firstOrNull { it.peer == peer && it.id == id && it.outgoing == outgoing }

    fun create(record: TransferRecord, payload: ByteArray? = null): Boolean {
        prune()
        if (find(record.peer, record.id, record.outgoing) != null) return true
        val pending = records.values.filter { it.outgoing == record.outgoing && !it.stored }
        if (poolFull(record, pending) || peerOverQuota(record, pending)) return false
        val directory = directory(record)
        directory.mkdirs()
        try {
            if (payload != null) atomic(File(directory, "payload"), payload)
            write(record)
        } catch (failure: IOException) { removeDirectory(directory); throw failure }
        return true
    }

    /**
     * One remote peer may hold at most [PEER_SHARE_PERCENT]% of the inbound pool, in entries and bytes, so a
     * single neighbor cannot starve the rest. Our own outbound transfers are exempt. A peer with nothing
     * pending is always allowed one transfer so a single large frame can still arrive.
     */
    fun peerOverQuota(record: TransferRecord): Boolean = peerOverQuota(record, records.values.filter {
        it.outgoing == record.outgoing && !it.stored
    })

    private fun poolFull(record: TransferRecord, pending: List<TransferRecord>): Boolean {
        val ordinary = pending.filterNot { it.priority }
        val lane = if (record.priority) pending.filter { it.priority } else ordinary
        val laneBytes = lane.sumOf { it.size.toLong() } + record.size
        val laneFull = if (record.priority) laneBytes > CONTROL_BYTES else ordinary.size >= MAX_ORDINARY_PENDING || laneBytes > MAX_BYTES
        return pending.size >= MAX_PENDING || laneFull
    }

    private fun peerOverQuota(record: TransferRecord, pending: List<TransferRecord>): Boolean {
        val own = if (record.outgoing) emptyList() else pending.filter { it.peer == record.peer }
        val lane = own.filter { it.priority == record.priority }
        val laneLimit = (if (record.priority) CONTROL_BYTES else MAX_BYTES).toLong() * PEER_SHARE_PERCENT / PERCENT
        // A peer with nothing pending always gets one transfer so a single large frame can still arrive.
        return own.isNotEmpty() && (own.size >= MAX_PENDING * PEER_SHARE_PERCENT / PERCENT ||
            lane.sumOf { it.size.toLong() } + record.size > laneLimit)
    }

    fun payload(record: TransferRecord): ByteArray = File(directory(record), "payload").readBytes()
    fun hasPiece(record: TransferRecord, index: Int) = File(directory(record), "p$index").isFile
    fun putPiece(record: TransferRecord, index: Int, bytes: ByteArray) {
        val file = File(directory(record), "p$index")
        if (!file.exists()) atomic(file, bytes)
    }
    fun assemble(record: TransferRecord): ByteArray {
        val result = ByteArray(record.size)
        for (index in 0 until pieceCount(record.size)) {
            val bytes = File(directory(record), "p$index").readBytes()
            require(bytes.size == minOf(PIECE_BYTES, record.size - index * PIECE_BYTES))
            bytes.copyInto(result, index * PIECE_BYTES)
        }
        return result
    }
    fun complete(record: TransferRecord): TransferRecord = record.copy(complete = true).also(::write)
    fun targets(record: TransferRecord, peers: Set<String>) = record.copy(
        relayTargetsSet = true, relayTargets = peers.toList()).also(::write)
    fun forwarded(record: TransferRecord, peer: String) = record.copy(
        forwardedPeers = (record.forwardedPeers + peer).distinct()).also(::write)
    fun applicationStored(record: TransferRecord, response: ByteArray?) = record.copy(
        applicationStored = true, response = response ?: record.response).also(::write)
    fun stored(record: TransferRecord, response: ByteArray? = null) {
        write(record.copy(stored = true, response = response?.takeIf { it.size <= MAX_RESPONSE_BYTES }))
        directory(record).listFiles().orEmpty().filter { it.name.startsWith("p") }.forEach { it.delete() }
        // Completed markers can be evicted; the application still deduplicates logical messages.
        records.values.filter { it.stored }.sortedBy { it.expiresAt }.dropLast(MAX_STORED_MARKERS).toList().forEach(::delete)
    }
    fun delete(record: TransferRecord) { records.remove(key(record)); removeDirectory(directory(record)) }
    fun prune() = records.values.filter { it.expiresAt <= now() }.toList().forEach(::delete)
    private fun write(record: TransferRecord) {
        atomic(File(directory(record), "record"), ProtoBuf.encodeToByteArray(record))
        records[key(record)] = record
    }
    private fun key(record: TransferRecord) = hash("${record.outgoing}|${record.peer}|${record.id}".toByteArray())
    private fun directory(record: TransferRecord) = File(root, key(record))
    private fun removeDirectory(directory: File) {
        require(directory.canonicalFile.parentFile == root.canonicalFile)
        directory.listFiles().orEmpty().filter { it.isFile }.forEach { it.delete() }
        directory.delete()
    }
    private fun recover(file: File): File {
        val backup = File(file.path + ".bak")
        if (!file.exists() && backup.exists()) backup.renameTo(file)
        return file
    }
    private fun atomic(file: File, bytes: ByteArray) {
        val temp = File(file.path + ".tmp")
        val backup = File(file.path + ".bak")
        FileOutputStream(temp).use { it.write(bytes); it.fd.sync() }
        if (backup.exists() && !backup.delete()) throw IOException("Cannot retire custody backup")
        if (file.exists() && !file.renameTo(backup)) throw IOException("Cannot back up custody record")
        if (!temp.renameTo(file)) { backup.renameTo(file); throw IOException("Cannot commit custody record") }
        backup.delete()
    }

    companion object {
        const val PIECE_BYTES = 1024
        const val MAX_BYTES = 2 * 1024 * 1024
        const val MAX_PENDING = 128
        /** Keeps room in the pool for priority transfers. */
        private const val MAX_ORDINARY_PENDING = 120
        private const val MAX_KEY_FIELD_LENGTH = 128
        private const val MAX_RESPONSE_BYTES = 4096
        private const val MAX_STORED_MARKERS = 256
        const val CONTROL_BYTES = 64 * 1024
        const val PEER_SHARE_PERCENT = 25
        private const val PERCENT = 100
        fun pieceCount(size: Int) = (size + PIECE_BYTES - 1) / PIECE_BYTES
        fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
