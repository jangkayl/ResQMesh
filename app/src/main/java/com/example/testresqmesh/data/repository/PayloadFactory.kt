package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.network.CryptoManager
import org.json.JSONArray
import org.json.JSONObject

object PayloadFactory {
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
    ): String {
        return JSONObject().apply {
            put("id", msgId)
            put("timestamp", timestamp)
            put("senderName", senderName)
            put("targetName", targetName)
            put("isPrivate", true)
            put("isSystem", false)
            
            if (targetPubKey != null) {
                val innerPayload = JSONObject().apply {
                    put("text", text)
                    if (imageBase64 != null) put("image", imageBase64)
                    if (audioBase64 != null) put("audio", audioBase64)
                }.toString()
                
                val encryptedPair = CryptoManager.encryptHybrid(innerPayload, targetPubKey)
                if (encryptedPair != null) {
                    put("isEncrypted", true)
                    put("encryptedData", encryptedPair.first)
                    put("encryptedKey", encryptedPair.second)
                } else {
                    put("isEncrypted", false)
                    put("text", text)
                    if (imageBase64 != null) put("image", imageBase64)
                    if (audioBase64 != null) put("audio", audioBase64)
                }
            } else {
                put("isEncrypted", false)
                put("text", text)
                if (imageBase64 != null) put("image", imageBase64)
                if (audioBase64 != null) put("audio", audioBase64)
            }

            if (locationLat != null) put("locationLat", locationLat)
            if (locationLng != null) put("locationLng", locationLng)
            put("routePath", JSONArray().apply { put(senderName) })
            
            if (directedRoute.isNotEmpty()) {
                put("directedRoute", JSONArray(directedRoute))
            }
        }.toString()
    }
    
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
    ): String {
        return JSONObject().apply {
            put("id", msgId)
            put("timestamp", timestamp)
            put("senderName", senderName)
            put("text", text)
            put("isPrivate", false)
            put("isSystem", false)
            if (imageBase64 != null) put("image", imageBase64)
            if (audioBase64 != null) put("audio", audioBase64)
            if (locationLat != null) put("locationLat", locationLat)
            if (locationLng != null) put("locationLng", locationLng)
            if (isSOS) put("isSOS", true)
            if (isSOSCancel) put("isSOSCancel", true)
            put("routePath", JSONArray().apply { put(senderName) })
        }.toString()
    }
}
