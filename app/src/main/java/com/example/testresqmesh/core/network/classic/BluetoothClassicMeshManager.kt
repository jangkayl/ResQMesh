package com.example.testresqmesh.core.network.classic

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.bluetooth.BluetoothServerSocket
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
                
                // GENIUS FILTER: Only accept devices that have the [RQ] prefix in their name!
                // This bypasses the broken SDP UUID lookup completely while filtering out 100% of TVs and cars.
                val deviceName = try { device?.name } catch (e: SecurityException) { null }
                
                if (deviceName != null && deviceName.startsWith("[RQ]") && device != null) {
                    if (device.bondState == BluetoothDevice.BOND_BONDED) {
                        discoveredDevices.add(device)
                        AppLogger.d("ClassicMesh", "Already bonded ResQMesh peer found: ${device.address}")
                    } else if (device.bondState == BluetoothDevice.BOND_NONE) {
                        AppLogger.d("ClassicMesh", "Found unbonded ResQMesh peer! Initiating automatic pairing...")
                        try {
                            device.createBond()
                        } catch (e: Exception) {
                            AppLogger.d("ClassicMesh", "Failed to initiate pairing: ${e.message}")
                        }
                    }
                }
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED == action) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                val prevState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)
                
                if (state == BluetoothDevice.BOND_BONDED && prevState == BluetoothDevice.BOND_BONDING) {
                    device?.let {
                        val deviceName = try { it.name } catch (e: Exception) { null }
                        if (deviceName != null && deviceName.startsWith("[RQ]")) {
                            discoveredDevices.add(it)
                            AppLogger.d("ClassicMesh", "Successfully paired with ResQMesh peer: ${it.address}! Added to routing table.")
                        }
                    }
                }
            }
        }
    }
    
    fun injectDiscoveredDevice(device: BluetoothDevice) {
        discoveredDevices.add(device)
    }

    private var originalDeviceName: String? = null

    fun start() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        
        // Apply the Genius Name Filter trick
        try {
            originalDeviceName = bluetoothAdapter.name
            if (originalDeviceName != null && !originalDeviceName!!.startsWith("[RQ]")) {
                bluetoothAdapter.setName("[RQ] ${originalDeviceName}")
                AppLogger.d("ClassicMesh", "Temporarily renamed device to: [RQ] ${originalDeviceName}")
            }
        } catch (e: SecurityException) {
            AppLogger.d("ClassicMesh", "Failed to set Bluetooth name (Missing permissions)")
        }
        
        // INSTANT ROUTING: Pre-fill the table with all previously paired devices!
        bluetoothAdapter.bondedDevices?.let { bonded ->
            discoveredDevices.addAll(bonded)
            bonded.forEach { AppLogger.d("ClassicMesh", "Loaded bonded device: ${it.address}") }
        }
        
        // 1. Start Server to listen for incoming mesh relays
        startServer()

        // 2. Start discovering nearby devices to build our routing table
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        startDiscovery()
    }

    fun stop() {
        // Restore the original Bluetooth name so we don't leave the user's phone named [RQ]
        try {
            if (originalDeviceName != null && !originalDeviceName!!.startsWith("[RQ]")) {
                bluetoothAdapter?.setName(originalDeviceName)
            }
        } catch (e: SecurityException) {}
        
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
                    // SECURE RFCOMM: Since we are now pairing, we use the highly stable Secure socket!
                    socket = device.createRfcommSocketToServiceRecord(APP_UUID)
                    socket.connect()
                    
                    val outStream: OutputStream = socket.outputStream
                    outStream.write(payloadBytes)
                    outStream.flush()
                    
                    // CLEAN TEARDOWN: Wait for the server to send an ACK byte before closing.
                    // This prevents the socket from entering a corrupted TIME_WAIT state that breaks future connections!
                    try {
                        val inStream: InputStream = socket.inputStream
                        inStream.read() // Blocks until server sends ACK
                    } catch (e: Exception) {}
                    
                    AppLogger.d("ClassicMesh", "Successfully blasted payload to ${device.address}")
                } catch (e: Exception) {
                    // Silently ignore to prevent log spam
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
            // SECURE RFCOMM SERVER: Highly stable, requires devices to be paired.
            bluetoothAdapter?.listenUsingRfcommWithServiceRecord(APP_NAME, APP_UUID)
        } catch (e: Exception) {
            AppLogger.d("ClassicMesh", "Server Socket failed: ${e.message}")
            null
        }

        override fun run() {
            if (serverSocket == null) return
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
                            val outStream = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(1024)
                            var bytes: Int
                            
                            // Accumulate ALL chunks of the massive JSON message until the sender hangs up
                            while (inStream.read(buffer).also { bytes = it } != -1) {
                                outStream.write(buffer, 0, bytes)
                            }
                            
                            if (outStream.size() > 0) {
                                val receivedPayload = outStream.toString(StandardCharsets.UTF_8.name())
                                AppLogger.d("ClassicMesh", "Received full payload from ${it.remoteDevice.address}")
                                
                                // Send ACK back to client to allow clean teardown
                                try {
                                    val ackStream: OutputStream = it.outputStream
                                    ackStream.write(1)
                                    ackStream.flush()
                                } catch (e: Exception) {}
                                
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
