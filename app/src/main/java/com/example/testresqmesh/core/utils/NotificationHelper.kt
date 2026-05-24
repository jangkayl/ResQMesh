package com.example.testresqmesh.core.utils

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class NotificationHelper(private val context: Context) {

    private val channelId = "resqmesh_private_messages"
    private val emergencyChannelId = "resqmesh_emergency_v3"

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        // Notification Channels are required for Android 8.0 (Oreo) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Private Messages"
            val descriptionText = "Alerts for incoming direct private messages."
            // IMPORTANCE_HIGH makes it pop up on the screen and make a sound
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)

            val emergencyName = "Emergency SOS Alerts"
            val emergencyDescriptionText = "Critical life-safety emergency alerts."
            val emergencyChannel = NotificationChannel(emergencyChannelId, emergencyName, NotificationManager.IMPORTANCE_HIGH).apply {
                description = emergencyDescriptionText
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                enableVibration(true)
                setBypassDnd(true)
                // Use default notification sound instead of ALARM
                val defaultSound = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                val audioAttributes = android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .build()
                setSound(defaultSound, audioAttributes)
            }
            notificationManager.createNotificationChannel(emergencyChannel)
        }
    }

    fun showPrivateMessageNotification(senderName: String, messageText: String) {
        val intent = android.content.Intent(context, com.example.testresqmesh.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, 
            0, 
            intent, 
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_dialog_alert) // TODO: Replace with your app's actual logo drawable
            .setContentTitle("Private Message from $senderName")
            .setContentText(messageText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true) // Dismisses the notification when clicked
            .setContentIntent(pendingIntent)

        // Fire the notification!
        try {
            with(NotificationManagerCompat.from(context)) {
                notify(System.currentTimeMillis().toInt(), builder.build())
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun showSosEmergencyNotification(senderName: String, messageText: String) {
        val intent = android.content.Intent(context, com.example.testresqmesh.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("EXTRA_TRIGGER_SOS", true)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, 
            0, 
            intent, 
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )


        val builder = NotificationCompat.Builder(context, emergencyChannelId)
            .setSmallIcon(R.drawable.ic_dialog_alert) // TODO: Replace with your app's actual logo drawable
            .setContentTitle("🚨 SOS FROM $senderName")
            .setContentText(messageText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            // Use standard notification sound
            .setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(System.currentTimeMillis().toInt(), builder.build())
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}