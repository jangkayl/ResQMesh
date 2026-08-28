    inner class BleDeviceNode(val macAddress: String) {
        private val outboundQueue = java.util.concurrent.ConcurrentLinkedQueue<ConnectionTask>()
        private val isConnecting = java.util.concurrent.atomic.AtomicBoolean(false)
        var gattRef: BluetoothGatt? = null
        var isConnected = false
        var currentTimeout: Runnable? = null
        
        var rfcommSocket: BluetoothSocket? = null
        var rfcommOut: java.io.OutputStream? = null
        var rfcommIn: java.io.InputStream? = null
        var readThread: Thread? = null

        fun setRfcommSocket(socket: BluetoothSocket) {
            rfcommSocket?.close()
            rfcommSocket = socket
            rfcommOut = socket.outputStream
            rfcommIn = socket.inputStream
            isConnected = true
            
            readThread = Thread {
                val buffer = ByteArray(4096)
                while (isConnected) {
                    try {
                        val bytes = rfcommIn?.read(buffer) ?: -1
                        if (bytes > 0) {
                            val payload = buffer.copyOf(bytes)
                            val jsonString = payload.toString(Charsets.UTF_8)
                            AppLogger.d("BLE_MESH", "Received via RFCOMM: ${jsonString.take(50)}")
                            
                            val jsonObject = JSONObject(jsonString)
                            if (jsonObject.has("id")) {
                                val msgId = jsonObject.getString("id")
                                if (seenMessageIds.contains(msgId)) continue
                                seenMessageIds.add(msgId)
                            }
                            
                            // Try CBOR decoding, fallback to string
                            val decodedString = com.example.testresqmesh.core.network.models.PayloadConverter.cborToJson(payload)
                                ?: jsonString
                            
                            handler.post {
                                onPayloadReceived?.invoke(macAddress, decodedString)
                            }
                        }
                    } catch (e: Exception) {
                        disconnectAndClear()
                        break
                    }
                }
            }
            readThread?.start()
            
            // Process queue if we had pending tasks!
            processQueue()
        }

        private val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    isConnected = true
                    gatt.requestMtu(512)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    currentTimeout?.let { handler.removeCallbacks(it) }
                    gatt.close()
                    gattRef = null
                    isConnected = false
                    markFinished() // Unblocks queue if it was waiting
                }
            }
            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) gatt.discoverServices() else gatt.disconnect()
            }
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                AppLogger.d("BLE_MESH", "Services discovered for $macAddress")
                if (!outboundQueue.isEmpty() && !isConnecting.get()) {
                    processQueue()
                } else if (isConnecting.get()) {
                    val service = gatt.getService(SERVICE_UUID)
                    val char = service?.getCharacteristic(RX_CHARACTERISTIC_UUID)
                    if (char != null) {
                        val task = outboundQueue.peek()
                        if (task != null) {
                            char.value = task.payloadBytes
                            if (!gatt.writeCharacteristic(char)) gatt.disconnect()
                        } else gatt.disconnect()
                    } else gatt.disconnect()
                }
            }
            override fun onCharacteristicWrite(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, status: Int) {
                currentTimeout?.let { handler.removeCallbacks(it) }
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    outboundQueue.poll()
                } else {
                    AppLogger.d("BLE_MESH", "GATT Write Failed: $status")
                }
                
                if (bleNodes.count { it.value.isConnected } > 3) {
                    gatt.disconnect()
                }
                markFinished()
            }
        }

        fun forceConnect() {
            if (rfcommSocket != null || gattRef != null || isConnected) return
            AppLogger.d("BLE_MESH", "Attempting RFCOMM Auto-Pairing to $macAddress")
            val device = bluetoothAdapter.getRemoteDevice(macAddress)
            
            Thread {
                try {
                    val socket = device.createInsecureRfcommSocketToServiceRecord(RFCOMM_UUID)
                    socket.connect()
                    handler.post {
                        AppLogger.d("BLE_MESH", "RFCOMM Socket Connected to $macAddress!")
                        setRfcommSocket(socket)
                    }
                } catch (e: Exception) {
                    AppLogger.d("BLE_MESH", "RFCOMM failed to $macAddress, falling back to GATT: ${e.message}")
                    handler.post { forceConnectGatt(device) }
                }
            }.start()
        }
        
        private fun forceConnectGatt(device: BluetoothDevice) {
            gattRef = device.connectGatt(context, false, gattCallback)
        }

        fun queueChunk(task: ConnectionTask) {
            outboundQueue.add(task)
            processQueue()
        }

        private fun processQueue() {
            if (outboundQueue.isEmpty() || isConnecting.get()) return
            
            val task = outboundQueue.peek()
            if (task == null) return

            if (task.msgHash.isNotEmpty() && endpointLastMsgHash[task.targetMacAddress] == task.msgHash) {
                AppLogger.d("BLE_MESH", "Crowd Control: Aborting task for ${task.targetMacAddress}")
                outboundQueue.poll()
                markFinished()
                return
            }

            if (rfcommSocket != null && rfcommOut != null && isConnected) {
                isConnecting.set(true)
                Thread {
                    try {
                        rfcommOut?.write(task.payloadBytes)
                        rfcommOut?.flush()
                        handler.post {
                            outboundQueue.poll() // Success
                            isConnecting.set(false)
                            processQueue()
                        }
                    } catch (e: Exception) {
                        handler.post { disconnectAndClear() }
                    }
                }.start()
                return
            }
            
            if (gattRef != null && isConnected) {
                isConnecting.set(true)
                val service = gattRef!!.getService(SERVICE_UUID)
                val char = service?.getCharacteristic(RX_CHARACTERISTIC_UUID)
                if (char != null) {
                    char.value = task.payloadBytes
                    val success = gattRef!!.writeCharacteristic(char)
                    if (success) return 
                }
                gattRef!!.disconnect()
                gattRef!!.close()
                gattRef = null
                isConnected = false
            }
            
            if (!isConnecting.get()) {
                isConnecting.set(true)
                forceConnect()
                val timeoutRunnable = Runnable {
                    disconnectAndClear()
                    markFinished()
                }
                currentTimeout = timeoutRunnable
                handler.postDelayed(timeoutRunnable, 5000)
            }
        }
        
        private fun markFinished() {
            handler.postDelayed({
                isConnecting.set(false)
                processQueue()
            }, 50)
        }
        
        fun disconnectAndClear() {
            try { rfcommSocket?.close() } catch (e: Exception) {}
            rfcommSocket = null
            rfcommOut = null
            rfcommIn = null
            gattRef?.disconnect()
            gattRef?.close()
            gattRef = null
            isConnected = false
            // DONT CLEAR QUEUE, RETRY LATER
            isConnecting.set(false)
        }
    }
