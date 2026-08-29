import re

with open("C:/Users/Kyle/Downloads/test_resqmesh/app/src/main/java/com/example/testresqmesh/core/network/NativeBleManager.kt", "r", encoding="utf-8") as f:
    content = f.read()

# 1. Bring back Unknown Node in scanCallback
def scan_replacer(match):
    return """            val device = result.device
            val serviceData = result.scanRecord?.getServiceData(ParcelUuid(SERVICE_UUID))
            val peerName = serviceData?.let { String(it, Charsets.UTF_8) } ?: device.name ?: "Unknown Node"
"""

content = re.sub(r'            val device = result\.device\s*val serviceData = result\.scanRecord\?\.getServiceData\(ParcelUuid\(SERVICE_UUID\)\)\s*val peerName = serviceData\?\.let \{ String\(it, Charsets\.UTF_8\) \} \?: return\s*', scan_replacer, content)

# 2. Update processBinaryPayload
def process_replacer(match):
    return """    private fun processBinaryPayload(endpointId: String, payloadBytes: ByteArray) {
        try {
            val payload = kotlinx.serialization.protobuf.ProtoBuf.decodeFromByteArray(com.example.testresqmesh.core.network.MeshPayload.serializer(), payloadBytes)
            val currentName = connectedEndpointNames[endpointId]
            if (payload.senderName.isNotEmpty() && currentName != payload.senderName) {
                connectedEndpointNames[endpointId] = payload.senderName
                handler.post {
                    onDeviceConnected?.invoke(com.example.testresqmesh.core.model.ConnectedDevice(endpointId, payload.senderName, true))
                }
            }
        } catch (e: Exception) {
            AppLogger.e("BLE_MESH", "Failed to decode payload for auto-rename", e)
        }
        
        payloadDispatcher.dispatch(endpointId, payloadBytes)
    }"""

content = re.sub(r'    private fun processBinaryPayload\(endpointId: String, payloadBytes: ByteArray\) \{\s*payloadDispatcher\.dispatch\(endpointId, payloadBytes\)\s*\}', process_replacer, content)

with open("C:/Users/Kyle/Downloads/test_resqmesh/app/src/main/java/com/example/testresqmesh/core/network/NativeBleManager.kt", "w", encoding="utf-8") as f:
    f.write(content)
