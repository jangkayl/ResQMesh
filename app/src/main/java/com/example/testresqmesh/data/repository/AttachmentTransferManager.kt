package com.example.testresqmesh.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import com.example.testresqmesh.core.model.AttachmentStatus
import com.example.testresqmesh.core.model.AttachmentUiState
import com.example.testresqmesh.core.network.CryptoManager
import com.example.testresqmesh.core.network.AttachmentTransferPolicy
import com.example.testresqmesh.core.utils.AppLogger
import com.example.testresqmesh.core.network.MeshPayload
import com.example.testresqmesh.data.local.dao.AttachmentDao
import com.example.testresqmesh.data.local.entity.AttachmentEntity
import com.example.testresqmesh.data.local.entity.AttachmentTransferEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/**
 * Owns local image files, durable checkpoints, and end-to-end attachment cryptography.
 * Routing remains in [AttachmentPayloadHandler], and Bluetooth remains behind MeshNetworkGateway.
 */
class AttachmentTransferManager(
    context: Context,
    private val dao: AttachmentDao
) {
    data class CreatedOffer(val messageId: String, val attachment: AttachmentEntity, val payload: MeshPayload, val caption: String)
    data class InboundResult(val reply: MeshPayload? = null, val messageId: String? = null, val caption: String? = null, val attachmentId: String? = null)

    private val contentResolver = context.contentResolver
    private val root = File(context.filesDir, "private_attachments").apply { mkdirs() }

    fun observeUiStates(): Flow<Map<String, AttachmentUiState>> = dao.observeAll().map { rows ->
        rows.associate { it.attachmentId to toUiState(it) }
    }

    suspend fun createOffer(
        uri: Uri,
        caption: String,
        senderName: String,
        senderNodeId: String,
        targetName: String,
        targetNodeId: String,
        directedRouteNodeIds: List<String>,
        targetPublicKey: String
    ): CreatedOffer? = withContext(Dispatchers.IO) {
        val prepared = prepareJpeg(uri) ?: return@withContext null
        val attachmentId = UUID.randomUUID().toString()
        val messageId = UUID.randomUUID().toString()
        val source = File(root, "$attachmentId.jpg")
        source.writeBytes(prepared.jpeg)
        val preview = File(root, "$attachmentId.preview.jpg").also { it.writeBytes(prepared.preview) }
        val key = ByteArray(32).also(SecureRandom()::nextBytes)
        val totalChunks = (prepared.jpeg.size + CHUNK_BYTES - 1) / CHUNK_BYTES
        val entity = AttachmentEntity(
            attachmentId = attachmentId,
            messageId = messageId,
            peerName = targetName,
            directedRouteNodeIds = directedRouteNodeIds.joinToString(","),
            isOutgoing = true,
            status = AttachmentStatus.QUEUED_OFFER.name,
            byteSize = prepared.jpeg.size,
            width = prepared.width,
            height = prepared.height,
            sha256 = sha256(prepared.jpeg),
            previewPath = preview.absolutePath,
            localPath = source.absolutePath,
            tempPath = null,
            keyMaterialBase64 = Base64.encodeToString(key, Base64.NO_WRAP),
            totalChunks = totalChunks,
            sourceExpiresAt = System.currentTimeMillis() + SOURCE_RETENTION_MS
        )
        val manifest = JSONObject().apply {
            put("caption", caption)
            put("attachmentId", attachmentId)
            put("byteSize", entity.byteSize)
            put("width", entity.width)
            put("height", entity.height)
            put("sha256", entity.sha256)
            put("preview", Base64.encodeToString(prepared.preview, Base64.NO_WRAP))
            put("key", entity.keyMaterialBase64)
            put("totalChunks", totalChunks)
        }
        val encrypted = CryptoManager.encryptHybrid(manifest.toString(), targetPublicKey) ?: run {
            source.delete(); preview.delete(); return@withContext null
        }
        dao.upsert(entity)
        dao.upsertTransfer(AttachmentTransferEntity(attachmentId, targetNodeId, 0, entity.status))
        AppLogger.d("AttachmentTransfer", "Prepared private attachment ${attachmentId.take(8)} size=${entity.byteSize} chunks=$totalChunks")
        CreatedOffer(
            messageId,
            entity,
            MeshPayload(
                id = messageId,
                type = TYPE_OFFER,
                senderName = senderName,
                targetName = targetName,
                senderNodeId = senderNodeId,
                targetNodeId = targetNodeId,
                isPrivate = true,
                isEncrypted = true,
                encryptedData = encrypted.first,
                encryptedKey = encrypted.second,
                attachmentId = attachmentId,
                directedRouteNodeIds = directedRouteNodeIds
            ),
            caption
        )
    }

    suspend fun requestDownload(attachmentId: String, myName: String, myNodeId: String): MeshPayload? = withContext(Dispatchers.IO) {
        val attachment = dao.find(attachmentId) ?: return@withContext null
        if (attachment.isOutgoing) return@withContext null
        val route = attachment.directedRouteNodeIds.split(',').filter(String::isNotBlank).asReversed()
        if (route.isEmpty()) return@withContext null
        update(attachment, AttachmentStatus.DOWNLOAD_REQUESTED)
        payloadForPeer(attachment, TYPE_REQUEST, myName, myNodeId, route, attachmentSequence = attachment.receivedBytes / CHUNK_BYTES)
    }

    suspend fun cancel(attachmentId: String, myName: String, myNodeId: String): MeshPayload? = withContext(Dispatchers.IO) {
        val attachment = dao.find(attachmentId) ?: return@withContext null
        update(attachment, AttachmentStatus.CANCELLED)
        val route = if (attachment.isOutgoing) routeOf(attachment) else routeOf(attachment).asReversed()
        payloadForPeer(attachment, TYPE_CANCEL, myName, myNodeId, route)
    }

    suspend fun markRouteUnavailable(attachmentId: String) = withContext(Dispatchers.IO) {
        dao.find(attachmentId)?.let {
            update(it, AttachmentStatus.PAUSED_ROUTE_UNAVAILABLE, "Mesh route is not currently usable.")
            AppLogger.d("AttachmentTransfer", "Paused private attachment ${attachmentId.take(8)}: route unavailable")
        }
    }

    /** Called only after the dispatcher has selected the stable directed target. */
    suspend fun handleAtTarget(payload: MeshPayload, myName: String, myNodeId: String): InboundResult = withContext(Dispatchers.IO) {
        when (payload.type) {
            TYPE_OFFER -> receiveOffer(payload, myName, myNodeId)
            TYPE_OFFER_RECEIPT -> markOffered(payload)
            TYPE_REQUEST -> sendChunk(payload, myName, myNodeId, payload.attachmentSequence)
            TYPE_CHUNK -> receiveChunk(payload, myName, myNodeId)
            TYPE_CHECKPOINT -> sendChunk(payload, myName, myNodeId, payload.attachmentSequence)
            TYPE_COMPLETE -> markComplete(payload)
            TYPE_CANCEL -> markCancelled(payload)
            TYPE_UNAVAILABLE -> markUnavailable(payload)
            TYPE_BUSY -> markBusy(payload)
            else -> InboundResult()
        }
    }

    private suspend fun receiveOffer(payload: MeshPayload, myName: String, myNodeId: String): InboundResult {
        val json = CryptoManager.decryptHybrid(payload.encryptedData.orEmpty(), payload.encryptedKey.orEmpty())
            ?.let(::JSONObject) ?: return InboundResult()
        val attachmentId = json.optString("attachmentId")
        val key = json.optString("key")
        val byteSize = json.optInt("byteSize", -1)
        val totalChunks = json.optInt("totalChunks", -1)
        val preview = runCatching { Base64.decode(json.getString("preview"), Base64.NO_WRAP) }.getOrNull()
        if (attachmentId.isBlank() || attachmentId != payload.attachmentId || key.isBlank() || byteSize !in 1..MAX_IMAGE_BYTES ||
            totalChunks !in 1..MAX_CHUNKS || preview == null || preview.size > MAX_PREVIEW_BYTES) return InboundResult()
        if (dao.find(attachmentId) != null) return InboundResult(reply = reply(payload, TYPE_OFFER_RECEIPT, myName, myNodeId))
        val previewFile = File(root, "$attachmentId.preview.jpg").also { it.writeBytes(preview) }
        val temp = File(root, "$attachmentId.part")
        val entity = AttachmentEntity(
            attachmentId = attachmentId,
            messageId = payload.id,
            peerName = payload.senderName,
            directedRouteNodeIds = payload.directedRouteNodeIds.joinToString(","),
            isOutgoing = false,
            status = AttachmentStatus.OFFERED.name,
            byteSize = byteSize,
            width = json.optInt("width"),
            height = json.optInt("height"),
            sha256 = json.optString("sha256"),
            previewPath = previewFile.absolutePath,
            localPath = null,
            tempPath = temp.absolutePath,
            keyMaterialBase64 = key,
            totalChunks = totalChunks
        )
        dao.upsert(entity)
        dao.upsertTransfer(AttachmentTransferEntity(attachmentId, payload.senderNodeId, 0, entity.status))
        AppLogger.d("AttachmentTransfer", "Received private attachment offer ${attachmentId.take(8)} size=$byteSize")
        return InboundResult(
            reply = reply(payload, TYPE_OFFER_RECEIPT, myName, myNodeId),
            messageId = payload.id,
            caption = json.optString("caption"),
            attachmentId = attachmentId
        )
    }

    private suspend fun markOffered(payload: MeshPayload): InboundResult {
        dao.find(payload.attachmentId)?.takeIf { it.isOutgoing }?.let { update(it, AttachmentStatus.OFFERED) }
        return InboundResult()
    }

    private suspend fun sendChunk(payload: MeshPayload, myName: String, myNodeId: String, sequence: Int): InboundResult {
        val attachment = dao.find(payload.attachmentId) ?: return InboundResult(reply = reply(payload, TYPE_UNAVAILABLE, myName, myNodeId))
        if (!attachment.isOutgoing || attachment.sourceExpiresAt < System.currentTimeMillis() || attachment.localPath.isNullOrBlank() || !File(attachment.localPath).exists()) {
            update(attachment, AttachmentStatus.SOURCE_UNAVAILABLE)
            return InboundResult(reply = reply(payload, TYPE_UNAVAILABLE, myName, myNodeId))
        }
        if (sequence !in 0 until attachment.totalChunks) return InboundResult(reply = reply(payload, TYPE_UNAVAILABLE, myName, myNodeId))
        if (dao.activeOutgoingCountExcept(attachment.attachmentId) > 0) {
            update(attachment, AttachmentStatus.PAUSED_ROUTE_UNAVAILABLE, "Another private image transfer is active.")
            return InboundResult(reply = reply(payload, TYPE_BUSY, myName, myNodeId))
        }
        val key = attachment.keyMaterialBase64?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return InboundResult()
        val plain = readChunk(File(attachment.localPath), sequence)
        val encrypted = CryptoManager.encryptAttachmentChunk(key, attachment.attachmentId, sequence, plain) ?: return InboundResult()
        update(attachment, AttachmentStatus.TRANSFERRING)
        val route = routeOf(attachment)
        return InboundResult(reply = MeshPayload(
            id = "$attachment.attachmentId:$sequence",
            type = TYPE_CHUNK,
            senderName = myName,
            targetName = attachment.peerName,
            senderNodeId = myNodeId,
            targetNodeId = route.lastOrNull().orEmpty(),
            isPrivate = true,
            isEncrypted = true,
            attachmentId = attachment.attachmentId,
            attachmentSequence = sequence,
            attachmentTotalChunks = attachment.totalChunks,
            attachmentNonce = encrypted.first,
            attachmentCiphertext = encrypted.second,
            directedRouteNodeIds = route
        ))
    }

    private suspend fun receiveChunk(payload: MeshPayload, myName: String, myNodeId: String): InboundResult {
        val attachment = dao.find(payload.attachmentId) ?: return InboundResult(reply = reply(payload, TYPE_UNAVAILABLE, myName, myNodeId))
        if (attachment.isOutgoing || payload.attachmentTotalChunks != attachment.totalChunks) return InboundResult()
        val expected = attachment.receivedBytes / CHUNK_BYTES
        if (payload.attachmentSequence != expected) return InboundResult(reply = reply(payload, TYPE_CHECKPOINT, myName, myNodeId, expected))
        val key = attachment.keyMaterialBase64?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return InboundResult()
        val plain = CryptoManager.decryptAttachmentChunk(key, attachment.attachmentId, expected, payload.attachmentNonce ?: return InboundResult(), payload.attachmentCiphertext ?: return InboundResult())
            ?: return InboundResult(reply = reply(payload, TYPE_CHECKPOINT, myName, myNodeId, expected))
        if (plain.isEmpty() || plain.size > CHUNK_BYTES || attachment.receivedBytes + plain.size > attachment.byteSize) return InboundResult()
        FileOutputStream(File(attachment.tempPath ?: return InboundResult()), true).use { it.write(plain) }
        val received = attachment.receivedBytes + plain.size
        val next = expected + 1
        val updated = attachment.copy(receivedBytes = received, status = AttachmentStatus.TRANSFERRING.name, updatedAt = System.currentTimeMillis())
        dao.upsert(updated)
        dao.upsertTransfer(AttachmentTransferEntity(attachment.attachmentId, payload.senderNodeId, next, updated.status))
        if (next < attachment.totalChunks) return InboundResult(reply = reply(payload, TYPE_CHECKPOINT, myName, myNodeId, next))
        val temp = File(updated.tempPath ?: return InboundResult())
        if (received != attachment.byteSize || sha256(temp.readBytes()) != attachment.sha256) {
            temp.delete()
            update(updated.copy(receivedBytes = 0), AttachmentStatus.CORRUPT_RETRY, "Image verification failed; retry download.")
            return InboundResult(reply = reply(payload, TYPE_CHECKPOINT, myName, myNodeId, 0))
        }
        val final = File(root, "$attachment.attachmentId.jpg")
        if (!temp.renameTo(final)) return InboundResult()
        update(updated.copy(localPath = final.absolutePath, tempPath = null), AttachmentStatus.COMPLETE)
        AppLogger.d("AttachmentTransfer", "Verified private attachment ${attachment.attachmentId.take(8)}")
        return InboundResult(reply = reply(payload, TYPE_COMPLETE, myName, myNodeId, next))
    }

    private suspend fun markComplete(payload: MeshPayload): InboundResult {
        dao.find(payload.attachmentId)?.takeIf { it.isOutgoing }?.let { update(it, AttachmentStatus.COMPLETE) }
        return InboundResult()
    }

    private suspend fun markCancelled(payload: MeshPayload): InboundResult {
        dao.find(payload.attachmentId)?.let { update(it, AttachmentStatus.CANCELLED) }
        return InboundResult()
    }

    private suspend fun markUnavailable(payload: MeshPayload): InboundResult {
        dao.find(payload.attachmentId)?.let { update(it, AttachmentStatus.SOURCE_UNAVAILABLE) }
        return InboundResult()
    }

    private suspend fun markBusy(payload: MeshPayload): InboundResult {
        dao.find(payload.attachmentId)?.let { update(it, AttachmentStatus.PAUSED_ROUTE_UNAVAILABLE, "Sender is handling another private image; retry later.") }
        return InboundResult()
    }

    private fun reply(payload: MeshPayload, type: String, myName: String, myNodeId: String, sequence: Int = 0): MeshPayload = MeshPayload(
        id = "$type:${UUID.randomUUID()}", type = type, senderName = myName, targetName = payload.senderName,
        senderNodeId = myNodeId, targetNodeId = payload.senderNodeId, isPrivate = true, isEncrypted = true,
        attachmentId = payload.attachmentId, attachmentSequence = sequence,
        directedRouteNodeIds = payload.directedRouteNodeIds.asReversed()
    )

    private fun payloadForPeer(attachment: AttachmentEntity, type: String, myName: String, myNodeId: String, route: List<String>, attachmentSequence: Int = 0) = MeshPayload(
        id = "$type:${UUID.randomUUID()}", type = type, senderName = myName, targetName = attachment.peerName,
        senderNodeId = myNodeId, targetNodeId = route.lastOrNull().orEmpty(), isPrivate = true, isEncrypted = true,
        attachmentId = attachment.attachmentId, attachmentSequence = attachmentSequence, directedRouteNodeIds = route
    )

    private fun routeOf(attachment: AttachmentEntity) = attachment.directedRouteNodeIds.split(',').filter(String::isNotBlank)

    private suspend fun update(entity: AttachmentEntity, status: AttachmentStatus, reason: String? = null) {
        dao.upsert(entity.copy(status = status.name, failureReason = reason, updatedAt = System.currentTimeMillis()))
    }

    private fun readChunk(file: File, sequence: Int): ByteArray = RandomAccessFile(file, "r").use { input ->
        input.seek(sequence.toLong() * CHUNK_BYTES)
        ByteArray(minOf(CHUNK_BYTES, (input.length() - input.filePointer).toInt())).also(input::readFully)
    }

    private data class PreparedImage(val jpeg: ByteArray, val preview: ByteArray, val width: Int, val height: Int)

    private fun prepareJpeg(uri: Uri): PreparedImage? {
        val raw = decodeSampled(uri) ?: return null
        val oriented = rotate(raw, orientation(uri))
        var bitmap = scale(oriented, MAX_EDGE)
        var encoded = encodeWithinCap(bitmap, MAX_IMAGE_BYTES, MIN_QUALITY)
        while (encoded == null && maxOf(bitmap.width, bitmap.height) > MIN_EDGE) {
            bitmap = scale(bitmap, (maxOf(bitmap.width, bitmap.height) * 9) / 10)
            encoded = encodeWithinCap(bitmap, MAX_IMAGE_BYTES, MIN_QUALITY)
        }
        val jpeg = encoded ?: return null
        val preview = encodePreview(scale(bitmap, PREVIEW_EDGE)) ?: return null
        return PreparedImage(jpeg, preview, bitmap.width, bitmap.height)
    }

    private fun decodeSampled(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > MAX_EDGE * 2 || bounds.outHeight / sample > MAX_EDGE * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun orientation(uri: Uri): Int = runCatching {
        contentResolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            ?: ExifInterface.ORIENTATION_NORMAL
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun rotate(bitmap: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        return if (degrees == 0f) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
    }

    private fun scale(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val edge = maxOf(bitmap.width, bitmap.height)
        if (edge <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / edge
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
    }

    private fun encodeWithinCap(bitmap: Bitmap, cap: Int, minQuality: Int): ByteArray? {
        for (quality in 90 downTo minQuality step 5) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (out.size() <= cap) return out.toByteArray()
        }
        return null
    }

    private fun encodePreview(bitmap: Bitmap): ByteArray? = encodeWithinCap(bitmap, MAX_PREVIEW_BYTES, 35)

    fun toUiState(entity: AttachmentEntity) = AttachmentUiState(
        attachmentId = entity.attachmentId, messageId = entity.messageId,
        status = runCatching { AttachmentStatus.valueOf(entity.status) }.getOrDefault(AttachmentStatus.CORRUPT_RETRY),
        previewPath = entity.previewPath, localPath = entity.localPath, byteSize = entity.byteSize,
        receivedBytes = entity.receivedBytes, width = entity.width, height = entity.height, failureReason = entity.failureReason
    )

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        const val TYPE_OFFER = "ATTACHMENT_OFFER"
        const val TYPE_OFFER_RECEIPT = "ATTACHMENT_OFFER_RECEIPT"
        const val TYPE_REQUEST = "ATTACHMENT_REQUEST"
        const val TYPE_CHUNK = "ATTACHMENT_CHUNK"
        const val TYPE_CHECKPOINT = "ATTACHMENT_CHECKPOINT"
        const val TYPE_COMPLETE = "ATTACHMENT_COMPLETE"
        const val TYPE_CANCEL = "ATTACHMENT_CANCEL"
        const val TYPE_UNAVAILABLE = "ATTACHMENT_UNAVAILABLE"
        const val TYPE_BUSY = "ATTACHMENT_BUSY"
        const val CHUNK_BYTES = AttachmentTransferPolicy.CHUNK_BYTES
        const val MAX_IMAGE_BYTES = AttachmentTransferPolicy.MAX_IMAGE_BYTES
        const val MAX_PREVIEW_BYTES = AttachmentTransferPolicy.MAX_PREVIEW_BYTES
        const val MAX_CHUNKS = AttachmentTransferPolicy.MAX_CHUNKS
        private const val MAX_EDGE = 1280
        private const val PREVIEW_EDGE = 160
        private const val MIN_EDGE = 960
        private const val MIN_QUALITY = 55
        private const val SOURCE_RETENTION_MS = 7L * 24 * 60 * 60 * 1000
    }
}
