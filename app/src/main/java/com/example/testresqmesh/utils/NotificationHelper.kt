package com.example.testresqmesh.utils

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class NotificationHelper(private val context: Context) {

    private val channelId = "resqmesh_private_messages"

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
        }
    }

    fun showPrivateMessageNotification(senderName: String, messageText: String) {
        // Optional: Make the notification open your app when tapped
        // val intent = Intent(context, MainActivity::class.java).apply {
        //     flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        // }
        // val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_dialog_alert) // TODO: Replace with your app's actual logo drawable
            .setContentTitle("Private Message from $senderName")
            .setContentText(messageText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true) // Dismisses the notification when clicked
        // .setContentIntent(pendingIntent) // Uncomment if using the intent above

        // Fire the notification!
        try {
            with(NotificationManagerCompat.from(context)) {
                // We use a unique ID (like current time) so multiple messages don't overwrite each other
                notify(System.currentTimeMillis().toInt(), builder.build())
            }
        } catch (e: SecurityException) {
            // This catches the error if the user denied the POST_NOTIFICATIONS permission
            e.printStackTrace()
        }
    }
}