package com.example.testresqmesh.core.network.classic

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import com.example.testresqmesh.core.utils.AppLogger
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

@SuppressLint("MissingPermission")
class BluetoothClassicMeshManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    // The universal UUID that all ResQMesh devices will listen on
    private val APP_UUID: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
    private val APP_NAME = "ResQMesh_Classic"

    var onMessageReceived: ((String) -> Unit)? = null

    private var acceptThread: AcceptThread? = null
    private val discoveredDevices = mutableSetOf<BluetoothDevice>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                device?.let {
                    discoveredDevices.add(it)
                    AppLogger.d("ClassicMesh", "Discovered device: ${it.address}")
                }
            }
        }
    }

    fun start() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        
        // 1. Start Server to listen for incoming mesh relays
        startServer()

        // 2. Start discovering nearby devices to build our routing table
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        context.registerReceiver(receiver, filter)
        startDiscovery()
    }

    fun stop() {
        acceptThread?.cancel()
        acceptThread = null
        try { context.unregisterReceiver(receiver) } catch (e: Exception) {}
        bluetoothAdapter?.cancelDiscovery()
    }

    private fun startDiscovery() {
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter.cancelDiscovery()
        }
        bluetoothAdapter?.startDiscovery()
        
        // Loop discovery every 15 seconds to keep the mesh topology updated
        Handler(Looper.getMainLooper()).postDelayed({
            if (acceptThread != null) startDiscovery()
        }, 15000)
    }

    fun broadcastPayload(payload: String) {
        val payloadBytes = payload.toByteArray(StandardCharsets.UTF_8)
        
        // We act like a mesh: we attempt to open a socket to EVERY device we know about,
        // blast the payload, and close the socket.
        discoveredDevices.forEach { device ->
            Thread {
                var socket: BluetoothSocket? = null
                try {
                    // INSECURE RFCOMM: This is the Briar magic. It connects without asking the user for a PIN code!
                    socket = device.createInsecureRfcommSocketToServiceRecord(APP_UUID)
                    socket.connect()
                    
                    val outStream: OutputStream = socket.outputStream
                    outStream.write(payloadBytes)
                    outStream.flush()
                    AppLogger.d("ClassicMesh", "Successfully blasted payload to ${device.address}")
                } catch (e: Exception) {
                    AppLogger.d("ClassicMesh", "Failed to reach ${device.address}: ${e.message}")
                } finally {
                    try { socket?.close() } catch (e: Exception) {}
                }
            }.start()
        }
    }

    private fun startServer() {
        if (acceptThread != null) return
        acceptThread = AcceptThread()
        acceptThread?.start()
    }

    private inner class AcceptThread : Thread() {
        private val serverSocket = try {
            // INSECURE RFCOMM SERVER: Listens in the background forever without triggering pairing popups
            bluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord(APP_NAME, APP_UUID)
        } catch (e: Exception) {
            AppLogger.d("ClassicMesh", "Server Socket failed: ${e.message}")
            null
        }

        override fun run() {
            var socket: BluetoothSocket?
            while (true) {
                try {
                    socket = serverSocket?.accept() // Blocks until a device connects
                } catch (e: Exception) {
                    break // Socket closed
                }

                socket?.let {
                    // A device connected! Read the incoming payload
                    Thread {
                        try {
                            val inStream: InputStream = it.inputStream
                            val buffer = ByteArray(1024 * 10) // 10KB buffer for huge messages
                            val bytes = inStream.read(buffer)
                            if (bytes > 0) {
                                val receivedPayload = String(buffer, 0, bytes, StandardCharsets.UTF_8)
                                AppLogger.d("ClassicMesh", "Received payload from ${it.remoteDevice.address}")
                                
                                // Pass it up to MeshNetworkManager to route it!
                                Handler(Looper.getMainLooper()).post {
                                    onMessageReceived?.invoke(receivedPayload)
                                }
                            }
                        } catch (e: Exception) {
                            AppLogger.d("ClassicMesh", "Error reading stream: ${e.message}")
                        } finally {
                            try { it.close() } catch (e: Exception) {}
                        }
                    }.start()
                }
            }
        }

        fun cancel() {
            try { serverSocket?.close() } catch (e: Exception) {}
        }
    }
}
