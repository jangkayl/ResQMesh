import re

with open("C:/Users/Kyle/Downloads/test_resqmesh/app/src/main/java/com/example/testresqmesh/core/network/NativeBleManager.kt", "r", encoding="utf-8") as f:
    content = f.read()

# Dynamic getters
content = content.replace(
    "private val bleAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser",
    "private val bleAdvertiser get() = bluetoothAdapter?.bluetoothLeAdvertiser"
)
content = content.replace(
    "private val bleScanner = bluetoothAdapter?.bluetoothLeScanner",
    "private val bleScanner get() = bluetoothAdapter?.bluetoothLeScanner"
)

# Pulse code
pulse_code = """
    private fun sendSystemPulse() {
        if (!isNodeActive.get()) return
        try {
            val pulseId = java.util.UUID.randomUUID().toString()
            val payload = com.example.testresqmesh.core.network.MeshPayload(
                id = pulseId,
                type = "SYSTEM",
                senderName = myDeviceName,
                connectedNodes = connectedEndpointNames.values.toList(),
                publicKey = com.example.testresqmesh.core.network.CryptoManager.getMyPublicKeyBase64()
            )
            val payloadBytes = kotlinx.serialization.protobuf.ProtoBuf.encodeToByteArray(com.example.testresqmesh.core.network.MeshPayload.serializer(), payload)
            broadcastPayload(payloadBytes)
        } catch (e: Exception) {
            AppLogger.d("BLE_MESH", "Failed to send system pulse: ${e.message}")
        }
    }
"""

if "private fun sendSystemPulse" not in content:
    content = content.replace("fun stopMeshNode() {", pulse_code + "\n    fun stopMeshNode() {")

# Inject call in onServicesDiscovered
content = content.replace(
    'AppLogger.d("BLE_MESH", "GATT Services discovered for ${macAddress}. Ready to transmit.")',
    'AppLogger.d("BLE_MESH", "GATT Services discovered for ${macAddress}. Ready to transmit.")\n                    sendSystemPulse()'
)

with open("C:/Users/Kyle/Downloads/test_resqmesh/app/src/main/java/com/example/testresqmesh/core/network/NativeBleManager.kt", "w", encoding="utf-8") as f:
    f.write(content)
