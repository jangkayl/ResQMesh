package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.network.MeshPayload
import com.example.testresqmesh.core.network.CryptoManager
import com.example.testresqmesh.core.model.NodeIdentity
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf

import android.util.Base64

import com.example.testresqmesh.core.utils.BinaryCompressor

object PayloadFactory {

    fun buildPublicPayload(
        msgId: String,
        timestamp: Long,
        senderName: String,
        text: String,
        imageBase64: String?,
        audioBase64: String?,
        locationLat: Double?,
        locationLng: Double?,
        isSOS: Boolean,
        isSOSCancel: Boolean,
        channelId: String,
        ttl: Int = 0
    ): ByteArray {
        val payload = MeshPayload(
            id = msgId,
            type = "MESSAGE",
            senderName = senderName,
            text = text,
            imageBytes = imageBase64?.let { BinaryCompressor.compress(Base64.decode(it, Base64.DEFAULT)) },
            audioBytes = audioBase64?.let { BinaryCompressor.compress(Base64.decode(it, Base64.DEFAULT)) },
            locationLat = locationLat,
            locationLng = locationLng,
            isPrivate = false,
            isEncrypted = false,
            isSOS = isSOS,
            isSOSCancel = isSOSCancel,
            routePath = listOf(senderName),
            channelId = channelId,
            ttl = ttl
        )
        return ProtoBuf.encodeToByteArray(payload)
    }

    fun buildPrivatePayload(
        msgId: String,
        timestamp: Long,
        senderName: String,
        targetName: String,
        text: String,
        imageBase64: String?,
        audioBase64: String?,
        locationLat: Double?,
        locationLng: Double?,
        directedRoute: List<String>,
        targetPubKey: String?,
        channelId: String,
        ttl: Int = 0
    ): ByteArray {
        require(!targetPubKey.isNullOrBlank()) { "Recipient public key is unavailable" }
        val innerPayloadJson = org.json.JSONObject().apply {
            put("text", text)
            if (imageBase64 != null) put("image", imageBase64)
            if (audioBase64 != null) put("audio", audioBase64)
            if (locationLat != null) put("locationLat", locationLat)
            if (locationLng != null) put("locationLng", locationLng)
        }.toString()

        val encrypted = CryptoManager.encryptHybrid(innerPayloadJson, targetPubKey)
            ?: throw IllegalStateException("Private payload encryption failed")

        val payload = MeshPayload(
            id = msgId,
            type = "MESSAGE",
            senderName = senderName,
            targetName = targetName,
            senderNodeId = NodeIdentity.idOf(senderName).orEmpty(),
            targetNodeId = NodeIdentity.idOf(targetName).orEmpty(),
            isPrivate = true,
            isEncrypted = true,
            encryptedData = encrypted.first,
            encryptedKey = encrypted.second,
            routePath = listOf(senderName),
            directedRoute = directedRoute,
            directedRouteNodeIds = directedRoute.mapNotNull(NodeIdentity::idOf),
            channelId = channelId,
            ttl = ttl
        )
        return ProtoBuf.encodeToByteArray(payload)
    }

    fun buildBlockControlPayload(
        transmissionId: String,
        payloadType: String,
        operationId: String,
        senderName: String,
        targetName: String,
        directedRoute: List<String>,
        targetPublicKey: String,
        envelopeJson: String
    ): ByteArray {
        val encrypted = CryptoManager.encryptHybrid(envelopeJson, targetPublicKey)
            ?: throw IllegalStateException("Block control encryption failed")
        val payload = MeshPayload(
            id = transmissionId,
            type = payloadType,
            senderName = senderName,
            targetName = targetName,
            senderNodeId = NodeIdentity.idOf(senderName).orEmpty(),
            targetNodeId = NodeIdentity.idOf(targetName).orEmpty(),
            isPrivate = true,
            isEncrypted = true,
            encryptedData = encrypted.first,
            encryptedKey = encrypted.second,
            routePath = listOf(senderName),
            directedRoute = directedRoute,
            directedRouteNodeIds = directedRoute.mapNotNull(NodeIdentity::idOf),
            targetMessageId = operationId
        )
        return ProtoBuf.encodeToByteArray(payload)
    }

    fun buildSystemPulse(
        msgId: String,
        senderName: String,
        publicKey: String,
        connectedNodes: List<String>
    ): ByteArray {
        val payload = MeshPayload(
            id = msgId,
            type = "SYSTEM",
            senderName = senderName,
            senderNodeId = NodeIdentity.idOf(senderName).orEmpty(),
            publicKey = publicKey,
            connectedNodes = connectedNodes,
            connectedNodeIds = connectedNodes.mapNotNull(NodeIdentity::idOf)
        )
        return ProtoBuf.encodeToByteArray(payload)
    }
}
