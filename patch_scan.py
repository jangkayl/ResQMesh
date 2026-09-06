import re

with open(r'C:\Users\Kyle\Downloads\test_resqmesh\app\src\main\java\com\example\testresqmesh\core\network\NativeBleManager.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Replace the connection logic inside scanCallback
old_scan = '''                if (!activeConnections.containsKey(macAddress) && activeConnections.size < MAX_CONNECTIONS) {
                    AppLogger.d("BLE_MESH", "Discovered unhandled peer: . Attempting connection...")
                    connectToPersistentGatt(macAddress, peerName)
                }'''

new_scan = '''                val isConnectedAsClient = activeConnections.containsKey(macAddress)
                val isConnectedAsServer = activeServerConnections.containsKey(macAddress)
                
                if (!isConnectedAsClient && !isConnectedAsServer) {
                    val totalConnections = activeConnections.size + activeServerConnections.size
                    if (totalConnections < MAX_TOTAL_CONNECTIONS) {
                        val sharedPrefs = context.getSharedPreferences("mesh_prefs", android.content.Context.MODE_PRIVATE)
                        val myDeviceName = sharedPrefs.getString("device_name", android.os.Build.MODEL) ?: android.os.Build.MODEL
                        
                        if (myDeviceName > peerName) {
                            AppLogger.d("BLE_MESH", "Alphabetical Mesh Rule:  > . Initiating connection.")
                            connectToPersistentGatt(macAddress, peerName)
                        } else {
                            AppLogger.d("BLE_MESH", "Alphabetical Mesh Rule:  <= . Waiting for peer to connect.")
                        }
                    }
                }'''

content = content.replace(old_scan, new_scan)

with open(r'C:\Users\Kyle\Downloads\test_resqmesh\app\src\main\java\com\example\testresqmesh\core\network\NativeBleManager.kt', 'w', encoding='utf-8') as f:
    f.write(content)
