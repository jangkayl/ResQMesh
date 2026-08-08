package com.example.testresqmesh.core.network

import com.example.testresqmesh.core.utils.AppLogger
import org.json.JSONObject

enum class TransportMode {
    STRICT_NEARBY,
    STRICT_CONNECTIONLESS,
    HYBRID_AUTO
}

class PayloadDispatcher(private val callback: PayloadDispatcherCallback) {
    var transportMode: TransportMode = TransportMode.STRICT_CONNECTIONLESS

    private fun routeBroadcast(payload: String, excludeEndpointId: String?, isLargePayload: Boolean) {
        when (transportMode) {
            TransportMode.STRICT_NEARBY -> {
                callback.broadcastPayload(payload, excludeEndpointId)
            }
            TransportMode.STRICT_CONNECTIONLESS -> {
                if (excludeEndpointId != "BLE_CONNECTIONLESS") {
                    callback.broadcastConnectionless(payload)
                }
            }
            TransportMode.HYBRID_AUTO -> {
                if (isLargePayload) {
                    callback.broadcastPayload(payload, excludeEndpointId)
                } else {
                    if (excludeEndpointId != "BLE_CONNECTIONLESS") {
                        callback.broadcastConnectionless(payload)
                    }
                    callback.broadcastPayload(payload, excludeEndpointId)
                }
            }
        }
    }

    fun dispatch(endpointId: String, jsonString: String) {
        if (jsonString.startsWith("RAW:")) {
            val parts = jsonString.split(":", limit = 3)
            if (parts.size >= 3) {
                val sender = parts[1]
                val text = parts[2]
                
                if (sender == callback.getMyDeviceName()) return
                
                val msgId = java.util.UUID.randomUUID().toString()
                callback.onMessageReceived(
                    endpointId = endpointId,
                    msgId = msgId,
                    senderName = sender,
                    text = "📡 RAW BLE RX: $text",
                    isPrivate = false,
                    isSystem = false,
                    imageBase64 = null,
                    audioBase64 = null,
                    locationLat = null,
                    locationLng = null,
                    medium = "BLE Connectionless",
                    routePath = emptyList()
                )
                
                routeBroadcast(jsonString, endpointId, false)
            }
            return
        }
        
        try {
            val jsonObject = JSONObject(jsonString)
            
            // 1. DUPLICATE MESSAGE CHECK (THE BOUNCER)
            if (!jsonObject.has("id")) return
            val msgId = jsonObject.getString("id")
            val seenMessageIds = callback.getSeenMessageIds()

            if (seenMessageIds.contains(msgId)) return
            seenMessageIds.add(msgId)
            if (seenMessageIds.size > 500) {
                seenMessageIds.remove(seenMessageIds.first())
            }

            // 2. GOSSIP PROTOCOL SEEN/DELIVERED RECEIPTS
            if (jsonObject.has("type") && (jsonObject.getString("type") == "SEEN" || jsonObject.getString("type") == "DELIVERED")) {
                handleReceipt(endpointId, jsonObject)
                return
            }

            val sender = jsonObject.getString("senderName")
            if (sender == callback.getMyDeviceName()) return

            val isSystem = jsonObject.optBoolean("isSystem", false)
            if (isSystem) {
                handleSystemPulse(endpointId, msgId, sender, jsonString, jsonObject)
                return
            }

            // 3. NORMAL OR PRIVATE MESSAGES
            handleStandardMessage(endpointId, msgId, sender, jsonObject)

        } catch (e: Exception) {
            AppLogger.d("PayloadDispatcher", "Failed to parse payload: ${e.message}")
        }
    }

    private fun handleReceipt(endpointId: String, jsonObject: JSONObject) {
        val payloadType = jsonObject.getString("type")
        val targetMessageId = jsonObject.getString("targetMessageId")
        val reader = jsonObject.getString("reader")
        
        val returnRoute = mutableListOf<String>()
        if (jsonObject.has("returnRoute")) {
            val arr = jsonObject.getJSONArray("returnRoute")
            for (i in 0 until arr.length()) returnRoute.add(arr.getString(i))
        }

        val directedRoute = mutableListOf<String>()
        if (jsonObject.has("directedRoute")) {
            val arr = jsonObject.getJSONArray("directedRoute")
            for (i in 0 until arr.length()) directedRoute.add(arr.getString(i))
        }
        
        if (payloadType == "SEEN") {
            callback.onMessageSeen(targetMessageId, reader)
        } else if (payloadType == "DELIVERED") {
            callback.onMessageDelivered(targetMessageId, reader, returnRoute)
        }
        
        returnRoute.add(callback.getMyDeviceName())
        jsonObject.put("returnRoute", org.json.JSONArray(returnRoute))
        
        if (directedRoute.isNotEmpty()) {
            val myIndex = directedRoute.indexOf(callback.getMyDeviceName())
            if (myIndex != -1 && myIndex + 1 < directedRoute.size) {
                val nextHopName = directedRoute[myIndex + 1]
                val nextHopEndpointId = callback.getConnectedEndpointIdByName(nextHopName)
                if (nextHopEndpointId != null) {
                    callback.sendDirectPayload(nextHopEndpointId, jsonObject.toString())
                    return
                }
            }
        }
        
        routeBroadcast(jsonObject.toString(), endpointId, false)
    }

    private fun handleSystemPulse(endpointId: String, msgId: String, sender: String, jsonString: String, jsonObject: JSONObject) {
        if (jsonObject.has("publicKey")) {
            callback.onPublicKeyReceived(sender, jsonObject.getString("publicKey"))
        }
        if (jsonObject.has("connectedNodes")) {
            val nodesArray = jsonObject.getJSONArray("connectedNodes")
            val connectedList = mutableListOf<String>()
            for (i in 0 until nodesArray.length()) {
                connectedList.add(nodesArray.getString(i))
            }
            callback.onRoutingTableReceived(sender, connectedList)
        }
        
        callback.onMessageReceived(endpointId, msgId, sender, "", false, true, null, null, null, null, "LOCAL", emptyList())
        routeBroadcast(jsonString, endpointId, false)
    }

    private fun handleStandardMessage(endpointId: String, msgId: String, sender: String, jsonObject: JSONObject) {
        val targetName = jsonObject.optString("targetName", "")
        val isPrivate = jsonObject.optBoolean("isPrivate", false)
        val isEncrypted = jsonObject.optBoolean("isEncrypted", false)

        var text = jsonObject.optString("text", "")
        var imageBase64 = if (jsonObject.has("image")) jsonObject.getString("image") else null
        var audioBase64 = if (jsonObject.has("audio")) jsonObject.getString("audio") else null
        
        // PROOF OF ENCRYPTION LOGGING
        if (isEncrypted) {
            val encryptedData = jsonObject.optString("encryptedData", "MISSING")
            val previewData = if (encryptedData.length > 20) encryptedData.substring(0, 20) + "..." else encryptedData
            AppLogger.d("MeshNetwork_E2EE", "INBOUND ENCRYPTED PAYLOAD DETECTED from $sender. Raw Ciphertext preview: [$previewData]")
        } else {
            AppLogger.d("MeshNetwork_E2EE", "INBOUND PUBLIC PAYLOAD DETECTED from $sender. No encryption applied.")
        }

        if (isEncrypted && targetName == callback.getMyDeviceName()) {
            val encryptedData = jsonObject.getString("encryptedData")
            val encryptedKey = jsonObject.getString("encryptedKey")
            
            AppLogger.d("MeshNetwork_E2EE", "Message is addressed to ME. Attempting RSA+AES Hybrid Decryption...")
            val decryptedJsonString = CryptoManager.decryptHybrid(encryptedData, encryptedKey)
            
            if (decryptedJsonString != null) {
                val innerPayload = JSONObject(decryptedJsonString)
                text = innerPayload.optString("text", text)
                if (innerPayload.has("image")) imageBase64 = innerPayload.getString("image")
                if (innerPayload.has("audio")) audioBase64 = innerPayload.getString("audio")
                AppLogger.d("MeshNetwork_E2EE", "E2EE SUCCESS: Decrypted private payload! Plaintext: [$text]")
            } else {
                text = "[ENCRYPTED CONTENT: Decryption Failed]"
                AppLogger.d("MeshNetwork_E2EE", "E2EE FAILED: Could not decrypt payload. Keys do not match.")
            }
        } else if (isEncrypted) {
            text = "[ENCRYPTED CONTENT: Routing...]"
            imageBase64 = null
            audioBase64 = null
            AppLogger.d("MeshNetwork_E2EE", "E2EE ROUTING: Message is for $targetName. I cannot read it. Acting as a blind encrypted relay.")
        }

        val medium = callback.getEndpointMedium(endpointId)
        AppLogger.d("PayloadDispatcher", "ROUTE (Received): Message from [$sender] arrived physically via endpoint [$endpointId] using $medium")

        val locationLat = if (jsonObject.has("locationLat")) jsonObject.getDouble("locationLat") else null
        val locationLng = if (jsonObject.has("locationLng")) jsonObject.getDouble("locationLng") else null

        val routePath = mutableListOf<String>()
        if (jsonObject.has("routePath")) {
            val arr = jsonObject.getJSONArray("routePath")
            for (i in 0 until arr.length()) routePath.add(arr.getString(i))
        }

        val directedRoute = mutableListOf<String>()
        if (jsonObject.has("directedRoute")) {
            val arr = jsonObject.getJSONArray("directedRoute")
            for (i in 0 until arr.length()) directedRoute.add(arr.getString(i))
        }

        val isSOS = jsonObject.optBoolean("isSOS", false)
        val isSOSCancel = jsonObject.optBoolean("isSOSCancel", false)

        if (isPrivate) {
            if (targetName == callback.getMyDeviceName()) {
                callback.showNotification(sender, text)
                callback.onMessageReceived(endpointId, msgId, sender, text, isPrivate, false, imageBase64, audioBase64, locationLat, locationLng, medium, routePath)
            } else {
                AppLogger.d("PayloadDispatcher", "ROUTE (Relay): Forwarding Private message meant for [$targetName] securely across the mesh.")
                routePath.add(callback.getMyDeviceName())
                jsonObject.put("routePath", org.json.JSONArray(routePath))
                
                if (directedRoute.isNotEmpty()) {
                    val myIndex = directedRoute.indexOf(callback.getMyDeviceName())
                    if (myIndex != -1 && myIndex + 1 < directedRoute.size) {
                        val nextHopName = directedRoute[myIndex + 1]
                        val nextHopEndpointId = callback.getConnectedEndpointIdByName(nextHopName)
                        if (nextHopEndpointId != null) {
                            callback.sendDirectPayload(nextHopEndpointId, jsonObject.toString())
                            return
                        }
                    }
                }
                
                routeBroadcast(jsonObject.toString(), endpointId, imageBase64 != null || audioBase64 != null)
            }
        } else {
            if (isSOSCancel) {
                callback.onSosCancelled()
            }
            if (isSOS && sender != callback.getMyDeviceName()) {
                // We use a callback so the dispatcher doesn't need to know about UI lifecycle
                callback.showSosEmergencyNotification(sender, text)
            }
            
            routePath.add(callback.getMyDeviceName())
            jsonObject.put("routePath", org.json.JSONArray(routePath))
            callback.onMessageReceived(endpointId, msgId, sender, text, isPrivate, false, imageBase64, audioBase64, locationLat, locationLng, medium, routePath)
            routeBroadcast(jsonObject.toString(), endpointId, imageBase64 != null || audioBase64 != null)
        }
    }
}
