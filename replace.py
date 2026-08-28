import re

with open("C:/Users/Kyle/Downloads/test_resqmesh/app/src/main/java/com/example/testresqmesh/core/network/MeshNetworkManager.kt", "r", encoding="utf-8") as f:
    content = f.read()

pattern = re.compile(r'    fun broadcastSystemPulse\(\).*?    fun broadcastDeliveredReceipt.*?    }', re.DOTALL)

replacement = """    fun broadcastSystemPulse() {
        if (!isNodeActive.get()) return
        val pulseId = java.util.UUID.randomUUID().toString()
        val payload = com.example.testresqmesh.core.network.MeshPayload(
            id = pulseId,
            type = "SYSTEM",
            senderName = myDeviceName,
            connectedNodes = connectedEndpointNames.values.toList(),
            publicKey = com.example.testresqmesh.core.security.CryptoManager.getMyPublicKeyBase64()
        )
        val payloadBytes = kotlinx.serialization.protobuf.ProtoBuf.encodeToByteArray(com.example.testresqmesh.core.network.MeshPayload.serializer(), payload)
        broadcastPayload(payloadBytes)
    }

    fun broadcastSeenReceipt(targetMessageId: String, isPrivate: Boolean, targetId: String? = null) {
        val payload = com.example.testresqmesh.core.network.MeshPayload(
            id = java.util.UUID.randomUUID().toString(),
            type = "SEEN",
            targetMessageId = targetMessageId,
            reader = myDeviceName,
            isPrivate = isPrivate
        )
        val payloadBytes = kotlinx.serialization.protobuf.ProtoBuf.encodeToByteArray(com.example.testresqmesh.core.network.MeshPayload.serializer(), payload)
        broadcastPayload(payloadBytes)
    }

    fun broadcastDeliveredReceipt(targetMessageId: String, isPrivate: Boolean, targetId: String? = null, directedReturnRoute: List<String> = emptyList()) {
        val payload = com.example.testresqmesh.core.network.MeshPayload(
            id = java.util.UUID.randomUUID().toString(),
            type = "DELIVERED",
            targetMessageId = targetMessageId,
            reader = myDeviceName,
            isPrivate = isPrivate,
            returnRoute = listOf(myDeviceName),
            directedRoute = directedReturnRoute
        )
        val payloadBytes = kotlinx.serialization.protobuf.ProtoBuf.encodeToByteArray(com.example.testresqmesh.core.network.MeshPayload.serializer(), payload)

        if (isPrivate && directedReturnRoute.size > 1) {
            val nextHopName = directedReturnRoute[1]
            val nextHopEndpointId = connectedEndpointNames.entries.find { it.value == nextHopName }?.key
            if (nextHopEndpointId != null) {
                sendDirectPayload(nextHopEndpointId, payloadBytes)
            } else {
                broadcastPayload(payloadBytes)
            }
        } else {
            broadcastPayload(payloadBytes)
        }
    }"""

new_content = pattern.sub(replacement, content)
with open("C:/Users/Kyle/Downloads/test_resqmesh/app/src/main/java/com/example/testresqmesh/core/network/MeshNetworkManager.kt", "w", encoding="utf-8") as f:
    f.write(new_content)
