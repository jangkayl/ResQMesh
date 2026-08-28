package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.network.MeshPayload
import com.example.testresqmesh.core.network.CryptoManager
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf

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
        isSOSCancel: Boolean
    ): ByteArray {
        val payload = MeshPayload(
            id = msgId,
            type = "MESSAGE",
            senderName = senderName,
            text = text,
            image = imageBase64,
            audio = audioBase64,
            locationLat = locationLat,
            locationLng = locationLng,
            isPrivate = false,
            isEncrypted = false,
            isSOS = isSOS,
            isSOSCancel = isSOSCancel,
            routePath = listOf(senderName)
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
        targetPubKey: String?
    ): ByteArray {
        if (targetPubKey != null) {
            val innerPayloadJson = org.json.JSONObject().apply {
                put("text", text)
                if (imageBase64 != null) put("image", imageBase64)
                if (audioBase64 != null) put("audio", audioBase64)
            }.toString()
            
            val encrypted = CryptoManager.encryptHybrid(innerPayloadJson, targetPubKey)
            val encryptedDataStr = encrypted?.first
            val encryptedKeyStr = encrypted?.second
            
            val payload = MeshPayload(
                id = msgId,
                type = "MESSAGE",
                senderName = senderName,
                targetName = targetName,
                isPrivate = true,
                isEncrypted = true,
                encryptedData = encryptedDataStr,
                encryptedKey = encryptedKeyStr,
                locationLat = locationLat,
                locationLng = locationLng,
                directedRoute = directedRoute,
                routePath = listOf(senderName)
            )
            return ProtoBuf.encodeToByteArray(payload)
        } else {
            val payload = MeshPayload(
                id = msgId,
                type = "MESSAGE",
                senderName = senderName,
                targetName = targetName,
                text = text,
                image = imageBase64,
                audio = audioBase64,
                isPrivate = true,
                isEncrypted = false,
                locationLat = locationLat,
                locationLng = locationLng,
                directedRoute = directedRoute,
                routePath = listOf(senderName)
            )
            return ProtoBuf.encodeToByteArray(payload)
        }
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
            publicKey = publicKey,
            connectedNodes = connectedNodes
        )
        return ProtoBuf.encodeToByteArray(payload)
    }
}
