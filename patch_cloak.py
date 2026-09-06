import re

with open(r'C:\Users\Kyle\Downloads\test_resqmesh\app\src\main\java\com\example\testresqmesh\core\network\NativeBleManager.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Add updateInvisibilityCloak
content = content.replace(
    'private val advertiseCallback = object : AdvertiseCallback() {}',
    '''private val advertiseCallback = object : AdvertiseCallback() {}

    private fun updateInvisibilityCloak() {
        if (!isNodeActive.get()) return
        val totalConnections = activeConnections.size + activeServerConnections.size
        
        if (totalConnections >= MAX_TOTAL_CONNECTIONS && !isCloaked) {
            AppLogger.d("BLE_MESH", "Max connections reached (). Engaging Invisibility Cloak (Stopping Advertiser).")
            try { bleAdvertiser?.stopAdvertising(advertiseCallback) } catch (e: Exception) {}
            isCloaked = true
        } else if (totalConnections < MAX_TOTAL_CONNECTIONS && isCloaked) {
            AppLogger.d("BLE_MESH", "Connections dropped to . Dropping Cloak (Restarting Advertiser).")
            if (currentTeamKey.isNotEmpty()) {
                startAdvertising(currentTeamKey)
            }
            isCloaked = false
        }
    }'''
)

# 2. Add calls to updateInvisibilityCloak inside connectToPersistentGatt
content = content.replace(
    'connectionInteractionTimes.putIfAbsent(macAddress, System.currentTimeMillis())',
    'connectionInteractionTimes.putIfAbsent(macAddress, System.currentTimeMillis())\n                    updateInvisibilityCloak()'
)

content = content.replace(
    'chunkBuffers.remove(macAddress)\n                    \n                    handler.post',
    'chunkBuffers.remove(macAddress)\n                    updateInvisibilityCloak()\n                    \n                    handler.post'
)

# 3. Add to startMeshNode
content = content.replace(
    'fun startMeshNode(teamKey: String) {\n        if',
    'fun startMeshNode(teamKey: String) {\n        currentTeamKey = teamKey\n        isCloaked = false\n        if'
)

# 4. Make sure connectToPersistentGatt queues connection properly for Alphabetical mesh
# Wait, did the previous agent add the alphabetical mesh logic? Let's check transcript step 5824.
# I will just write the file out first.
with open(r'C:\Users\Kyle\Downloads\test_resqmesh\app\src\main\java\com\example\testresqmesh\core\network\NativeBleManager.kt', 'w', encoding='utf-8') as f:
    f.write(content)
