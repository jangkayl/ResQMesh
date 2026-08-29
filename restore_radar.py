import re

with open("C:/Users/Kyle/Downloads/test_resqmesh/app/src/main/java/com/example/testresqmesh/feature/radar/ui/RadarScreen.kt", "r", encoding="utf-8") as f:
    content = f.read()

content = content.replace(
    "val connectedNames = uiState.connectedDevices.map { it.name }.toSet()",
    "val connectedIds = uiState.connectedDevices.map { it.endpointId }.toSet()"
)
content = content.replace(
    ".filter { it.name !in connectedNames }",
    ".filter { it.endpointId !in connectedIds }"
)
content = content.replace(
    "val scannedAndConnectedNames = connectedNames + scannedNodes.map { it.name }",
    "val scannedAndConnectedNames = uiState.connectedDevices.map { it.name }.toSet() + scannedNodes.map { it.name }"
)

with open("C:/Users/Kyle/Downloads/test_resqmesh/app/src/main/java/com/example/testresqmesh/feature/radar/ui/RadarScreen.kt", "w", encoding="utf-8") as f:
    f.write(content)
