package com.example.testresqmesh.core.network

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
                require(record.size in 1..MAX_BYTES && record.id.length <= 128 && record.peer.length <= 128)
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
        val ordinary = pending.filterNot { it.priority }
        val control = pending.filter { it.priority }
        if (pending.size >= 128 || (!record.priority && ordinary.size >= 120) ||
            (!record.priority && ordinary.sumOf { it.size.toLong() } + record.size > MAX_BYTES) ||
            (record.priority && control.sumOf { it.size.toLong() } + record.size > 64 * 1024)) return false
        val directory = directory(record)
        directory.mkdirs()
        try {
            if (payload != null) atomic(File(directory, "payload"), payload)
            write(record)
        } catch (failure: IOException) { removeDirectory(directory); throw failure }
        return true
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
        write(record.copy(stored = true, response = response?.takeIf { it.size <= 4096 }))
        directory(record).listFiles().orEmpty().filter { it.name.startsWith("p") }.forEach { it.delete() }
        // Completed markers can be evicted; the application still deduplicates logical messages.
        records.values.filter { it.stored }.sortedBy { it.expiresAt }.dropLast(256).toList().forEach(::delete)
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
        fun pieceCount(size: Int) = (size + PIECE_BYTES - 1) / PIECE_BYTES
        fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
