import re

with open("C:/Users/Kyle/Downloads/test_resqmesh/app/src/main/java/com/example/testresqmesh/core/network/NativeBleManager.kt", "r", encoding="utf-8") as f:
    content = f.read()

def replacer(match):
    return """    fun sendDirectPayload(targetMacAddress: String, payloadBytes: ByteArray) {
        if (!BluetoothAdapter.checkBluetoothAddress(targetMacAddress)) return
        
        cacheOutgoingMessageId(payloadBytes)"""

content = re.sub(r'    fun sendDirectPayload\(targetMacAddress: String, payloadBytes: ByteArray\) \{\s*cacheOutgoingMessageId\(payloadBytes\)', replacer, content)

with open("C:/Users/Kyle/Downloads/test_resqmesh/app/src/main/java/com/example/testresqmesh/core/network/NativeBleManager.kt", "w", encoding="utf-8") as f:
    f.write(content)
