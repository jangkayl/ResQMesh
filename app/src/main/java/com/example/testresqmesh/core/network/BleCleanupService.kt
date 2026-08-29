package com.example.testresqmesh.core.network

import android.app.Service
import android.content.Intent
import android.os.IBinder

class BleCleanupService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        
        // This is triggered exactly when the user swipes the app away from the Recent Apps list.
        // We must cleanly stop the BLE advertisement and disconnect GATT connections so peers update correctly.
        NativeBleManager.instance?.stopMeshNode()
        
        stopSelf()
    }
}
