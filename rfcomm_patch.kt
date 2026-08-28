    private var rfcommServerSocket: BluetoothServerSocket? = null
    private var acceptThread: Thread? = null
    private val RFCOMM_UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")

    private fun startRfcommServer() {
        try {
            rfcommServerSocket = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("ResQMesh", RFCOMM_UUID)
            acceptThread = Thread {
                while (isNodeActive.get()) {
                    try {
                        val socket = rfcommServerSocket?.accept()
                        if (socket != null) {
                            val macAddress = socket.remoteDevice.address
                            AppLogger.d("BLE_MESH", "Incoming RFCOMM connection from $macAddress")
                            getNode(macAddress).setRfcommSocket(socket)
                        }
                    } catch (e: Exception) {
                        break
                    }
                }
            }
            acceptThread?.start()
        } catch (e: Exception) {
            AppLogger.d("BLE_MESH", "Failed to start RFCOMM server: ${e.message}")
        }
    }
