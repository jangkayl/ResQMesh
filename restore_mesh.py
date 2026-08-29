import re

with open("C:/Users/Kyle/Downloads/test_resqmesh/app/src/main/java/com/example/testresqmesh/core/network/MeshNetworkManager.kt", "r", encoding="utf-8") as f:
    content = f.read()

dummy_client = """    // private val connectionsClient = Nearby.getConnectionsClient(context)
    private val connectionsClient = object {
        fun stopAllEndpoints() {}
        fun stopAdvertising() {}
        fun stopDiscovery() {}
        fun startAdvertising(a:Any,b:Any,c:Any,d:Any) = com.google.android.gms.tasks.Tasks.forResult<Void>(null)
        fun startDiscovery(a:Any,b:Any,c:Any) = com.google.android.gms.tasks.Tasks.forResult<Void>(null)
        fun disconnectFromEndpoint(a:Any) {}
        fun sendPayload(a:Any,b:Any) {}
        fun requestConnection(a:Any,b:Any,c:Any) = com.google.android.gms.tasks.Tasks.forResult<Void>(null)
        fun rejectConnection(a:Any) {}
        fun acceptConnection(a:Any,b:Any) {}
    }"""

content = content.replace("    private val connectionsClient = Nearby.getConnectionsClient(context)", dummy_client)

with open("C:/Users/Kyle/Downloads/test_resqmesh/app/src/main/java/com/example/testresqmesh/core/network/MeshNetworkManager.kt", "w", encoding="utf-8") as f:
    f.write(content)
