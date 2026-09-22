package com.example.testresqmesh.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.testresqmesh.MainActivity
import com.example.testresqmesh.R
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.utils.AppLogger
import com.example.testresqmesh.core.utils.TerminalLogCategory
import com.example.testresqmesh.data.repository.MeshRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MeshForegroundService : Service() {
    private val repository: MeshRepository by inject()
    private val sessionController: MeshSessionController by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var notificationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            sessionController.stopNode()
            stopSelf()
            return START_NOT_STICKY
        }

        if (!sessionController.backgroundMeshEnabled.value || !repository.isOnline.value) {
            stopSelf()
            return START_NOT_STICKY
        }

        startInForeground(buildNotification(readyPeerCount = 0, status = repository.connectionStatus.value))
        AppLogger.event(
            category = TerminalLogCategory.SYSTEM,
            event = "BACKGROUND_MESH_STARTED",
            message = "Foreground mesh service active"
        )
        observeMeshState()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        notificationJob?.cancel()
        serviceScope.cancel()
        if (sessionController.backgroundMeshEnabled.value && repository.isOnline.value) {
            repository.stopNode()
        }
        AppLogger.event(
            category = TerminalLogCategory.SYSTEM,
            event = "BACKGROUND_MESH_STOPPED",
            message = "Foreground mesh service stopped"
        )
        super.onDestroy()
    }

    private fun observeMeshState() {
        if (notificationJob?.isActive == true) return
        notificationJob = serviceScope.launch {
            combine(repository.isOnline, repository.connectedDevices, repository.connectionStatus) { online, devices, status ->
                val readyPeerCount = devices.asSequence()
                    .filter { it.isPayloadReady && !NodeIdentity.isPlaceholder(it.name) }
                    .map { it.nodeId.ifBlank { NodeIdentity.key(it.name) } }
                    .distinct()
                    .count()
                Triple(online, readyPeerCount, status)
            }.collect { (online, peerCount, status) ->
                if (!online) {
                    stopSelf()
                    return@collect
                }
                delay(NOTIFICATION_DEBOUNCE_MS)
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, buildNotification(peerCount, status))
            }
        }
    }

    private fun startInForeground(notification: Notification) {
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(readyPeerCount: Int, status: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MeshForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val statusText = when {
            status.contains("error", ignoreCase = true) || status.contains("unsupported", ignoreCase = true) ->
                "Mesh needs attention"
            readyPeerCount == 0 -> "Listening for nearby mesh devices"
            readyPeerCount == 1 -> "1 direct peer ready"
            else -> "$readyPeerCount direct peers ready"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.resqmesh_logo)
            .setContentTitle("ResQMesh is active")
            .setContentText(statusText)
            .setContentIntent(openIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(android.R.drawable.ic_menu_view, "Open ResQMesh", openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Go offline", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Background mesh status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when ResQMesh is available in the background"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.example.testresqmesh.action.START_BACKGROUND_MESH"
        const val ACTION_STOP = "com.example.testresqmesh.action.STOP_BACKGROUND_MESH"
        private const val CHANNEL_ID = "resqmesh_background_mesh_v1"
        private const val NOTIFICATION_ID = 41001
        private const val NOTIFICATION_DEBOUNCE_MS = 2_500L
    }
}
