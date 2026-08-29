import re

with open("C:/Users/Kyle/Downloads/test_resqmesh/app/src/main/java/com/example/testresqmesh/core/network/NativeBleManager.kt", "r", encoding="utf-8") as f:
    content = f.read()

# Change addManufacturerData(1024, ...) to addServiceData(ParcelUuid(SERVICE_UUID), nameBytes)
content = content.replace(".addManufacturerData(1024, nameBytes)", ".addServiceData(ParcelUuid(SERVICE_UUID), nameBytes)")

# Change scanCallback to extract from Service Data instead of Manufacturer Data
def scan_replacer(match):
    return """            val device = result.device
            val serviceData = result.scanRecord?.getServiceData(ParcelUuid(SERVICE_UUID))
            val peerName = serviceData?.let { String(it, Charsets.UTF_8) } ?: return"""

content = re.sub(r'            val device = result\.device\s*val manufacturerData = result\.scanRecord\?\.getManufacturerSpecificData\(1024\)\s*val peerName = manufacturerData\?\.let \{ String\(it, Charsets\.UTF_8\) \} \?: return', scan_replacer, content)

with open("C:/Users/Kyle/Downloads/test_resqmesh/app/src/main/java/com/example/testresqmesh/core/network/NativeBleManager.kt", "w", encoding="utf-8") as f:
    f.write(content)
