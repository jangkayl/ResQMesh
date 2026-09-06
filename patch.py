import sys

with open(r'C:\Users\Kyle\Downloads\test_resqmesh\app\src\main\java\com\example\testresqmesh\core\network\NativeBleManager.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Add fields
content = content.replace(
    'private var isCloaked = false\n    private val MAX_TOTAL_CONNECTIONS = 3\n    private val activeConnections = ConcurrentHashMap<String, BluetoothGatt>()',
    'private var isCloaked = false\n    private val MAX_TOTAL_CONNECTIONS = 3\n    private val isConnecting = AtomicBoolean(false)\n    private val activeConnections = ConcurrentHashMap<String, BluetoothGatt>()'
)

content = content.replace(
    'private val chunkBuffers = ConcurrentHashMap<String, ByteArray>()',
    'private val chunkBuffers = ConcurrentHashMap<String, ByteArray>()\n    private val connectionMtu = ConcurrentHashMap<String, Int>()'
)

# 2. connectToPersistentGatt start
content = content.replace(
    'private fun connectToPersistentGatt(macAddress: String, peerName: String) {\n        val device = bluetoothAdapter.getRemoteDevice(macAddress)',
    'private fun connectToPersistentGatt(macAddress: String, peerName: String) {\n        if (!isConnecting.compareAndSet(false, true)) {\n            AppLogger.d("BLE_MESH", "Already connecting to another device. Queuing connection to  for later.")\n            return\n        }\n\n        val device = bluetoothAdapter.getRemoteDevice(macAddress)'
)

# 3. Disconnect release
content = content.replace(
    '} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {\n                    AppLogger.d("BLE_MESH", "GATT Socket disconnected from .")\n                    activeConnections.remove(macAddress)',
    '} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {\n                    AppLogger.d("BLE_MESH", "GATT Socket disconnected from .")\n                    isConnecting.set(false)\n                    activeConnections.remove(macAddress)'
)

# 4. onMtuChanged replace
old_mtu = '''            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    gatt.discoverServices()
                } else {
                    gatt.disconnect()
                }
            }'''
new_mtu = '''            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                val mac = gatt.device.address
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    AppLogger.d("BLE_MESH", "MTU Expanded to .")
                    connectionMtu[mac] = mtu - 3
                    gatt.discoverServices()
                } else {
                    AppLogger.d("BLE_MESH", "MTU Expansion failed. Samsung Fallback to 23 bytes.")
                    connectionMtu[mac] = 20
                    gatt.discoverServices()
                }
            }'''
content = content.replace(old_mtu, new_mtu)

# 5. onServicesDiscovered replace
old_services = '''            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    AppLogger.d("BLE_MESH", "GATT Services discovered for . Ready to transmit.")
                    
                    val service = gatt.getService(SERVICE_UUID)
                    val txChar = service?.getCharacteristic(TX_CHARACTERISTIC_UUID)
                    var descriptorWritePending = false
                    if (txChar != null) {
                        gatt.setCharacteristicNotification(txChar, true)
                        val descriptor = txChar.getDescriptor(CCC_DESCRIPTOR_UUID)
                        if (descriptor != null) {
                            descriptorWritePending = true
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                            } else {
                                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                gatt.writeDescriptor(descriptor)
                            }
                        }
                    }
                    
                    if (!descriptorWritePending) {
                        handler.postDelayed({
                            sendSystemPulse()
                            processNextPayload(macAddress)
                        }, 500)
                    }
                } else {
                    gatt.disconnect()
                }
            }'''
new_services = '''            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    AppLogger.d("BLE_MESH", "GATT Services discovered for . Ready to transmit.")
                    
                    val service = gatt.getService(SERVICE_UUID)
                    val txChar = service?.getCharacteristic(TX_CHARACTERISTIC_UUID)
                    var descriptorWritePending = false
                    if (txChar != null) {
                        gatt.setCharacteristicNotification(txChar, true)
                        val descriptor = txChar.getDescriptor(CCC_DESCRIPTOR_UUID)
                        if (descriptor != null) {
                            descriptorWritePending = true
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                            } else {
                                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                gatt.writeDescriptor(descriptor)
                            }
                        }
                    }
                    
                    if (!descriptorWritePending) {
                        handler.postDelayed({
                            isConnecting.set(false)
                            sendSystemPulse()
                            processNextPayload(macAddress)
                        }, 500)
                    }
                } else {
                    isConnecting.set(false)
                    gatt.disconnect()
                }
            }'''
content = content.replace(old_services, new_services)

# 6. onDescriptorWrite replace
old_descriptor = '''            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    handler.postDelayed({
                        sendSystemPulse()
                        processNextPayload(gatt.device.address)
                    }, 500)
                } else {
                    gatt.disconnect()
                }
            }'''
new_descriptor = '''            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    handler.postDelayed({
                        isConnecting.set(false)
                        sendSystemPulse()
                        processNextPayload(gatt.device.address)
                    }, 500)
                } else {
                    isConnecting.set(false)
                    gatt.disconnect()
                }
            }'''
content = content.replace(old_descriptor, new_descriptor)

# 7. chunk size
content = content.replace(
    'val length = Math.min(500, fullData.size - offset)',
    'val chunkSize = connectionMtu[targetMacAddress] ?: 20\n            val length = Math.min(chunkSize, fullData.size - offset)'
)

with open(r'C:\Users\Kyle\Downloads\test_resqmesh\app\src\main\java\com\example\testresqmesh\core\network\NativeBleManager.kt', 'w', encoding='utf-8') as f:
    f.write(content)
