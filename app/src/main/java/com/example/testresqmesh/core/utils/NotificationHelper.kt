package com.example.testresqmesh.core.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.example.testresqmesh.MainActivity
import com.example.testresqmesh.R
import java.util.concurrent.ConcurrentHashMap

class NotificationHelper(private val context: Context) {

    private val channelId = "resqmesh_private_messages_v2"
    private val emergencyChannelId = "resqmesh_emergency_v4"

    // Bounded message history per sender (max 7 messages) for Android MessagingStyle notifications
    private data class NotificationMessage(
        val text: String,
        val timestamp: Long
    )
    private val conversationHistories = ConcurrentHashMap<String, ArrayDeque<NotificationMessage>>()

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. Private Chat Channel with HIGH importance for heads-up chat cards
            val chatChannel = NotificationChannel(
                channelId,
                "Private Communications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Direct peer-to-peer encrypted communications"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 150, 80, 150)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(chatChannel)

            // 2. Critical Emergency SOS Channel with MAX/HIGH importance, heads-up and override
            val emergencyChannel = NotificationChannel(
                emergencyChannelId,
                "Emergency SOS Distress Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Life-safety SOS distress signals broadcast across mesh"
                vibrationPattern = longArrayOf(0, 600, 200, 600, 200, 800)
                enableVibration(true)
                enableLights(true)
                lightColor = Color.RED
                setBypassDnd(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .build()
                setSound(alarmSound, audioAttributes)
            }
            notificationManager.createNotificationChannel(emergencyChannel)
        }
    }

    /**
     * Shows an expandable, non-duplicate MessagingStyle notification for incoming private messages.
     * Keeps up to 7 recent messages from this sender, preventing notification spam.
     */
    fun showPrivateMessageNotification(senderName: String, messageText: String) {
        val history = conversationHistories.getOrPut(senderName) { ArrayDeque() }
        synchronized(history) {
            history.addLast(NotificationMessage(messageText, System.currentTimeMillis()))
            while (history.size > 7) {
                history.removeFirst()
            }
        }

        // Deep link intent to directly open the specific active chat screen
        val openChatIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("EXTRA_OPEN_CHAT_NODE", senderName)
        }
        val notificationId = ("chat_$senderName").hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openChatIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Build Person objects for Android MessagingStyle
        val you = Person.Builder().setName("You").build()
        val senderPerson = Person.Builder().setName(senderName).build()

        val messagingStyle = NotificationCompat.MessagingStyle(you)
            .setConversationTitle(senderName)
            .setGroupConversation(false)

        synchronized(history) {
            for (msg in history) {
                messagingStyle.addMessage(
                    NotificationCompat.MessagingStyle.Message(
                        msg.text,
                        msg.timestamp,
                        senderPerson
                    )
                )
            }
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.resqmesh_logo)
            .setStyle(messagingStyle)
            .setContentTitle(senderName)
            .setContentText(messageText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(0xFF00E5FF.toInt()) // Cyan accent
            .setOnlyAlertOnce(false)

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(notificationId, builder.build())
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    /**
     * Shows a high-priority Heads-Up Emergency SOS alert notification.
     */
    fun showSosEmergencyNotification(senderName: String, messageText: String) {
        // Deep link into SOS alarm / map
        val mainAlarmIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("EXTRA_TRIGGER_SOS", true)
            putExtra("EXTRA_SOS_SENDER", senderName)
            putExtra("EXTRA_SOS_TEXT", messageText)
        }
        val mainPendingIntent = PendingIntent.getActivity(
            context,
            2001,
            mainAlarmIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Direct action to jump straight to the tactical SOS map
        val viewMapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("EXTRA_VIEW_SOS_MAP", true)
            putExtra("EXTRA_SOS_SENDER", senderName)
            putExtra("EXTRA_SOS_TEXT", messageText)
        }
        val viewMapPendingIntent = PendingIntent.getActivity(
            context,
            2002,
            viewMapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, emergencyChannelId)
            .setSmallIcon(R.drawable.resqmesh_logo)
            .setContentTitle("🚨 SOS DISTRESS BEACON: $senderName")
            .setContentText(messageText)
            .setSubText("CRITICAL LIFE-SAFETY")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("🚨 CRITICAL SOS ALERT\n\nBeacon Sender: $senderName\nPayload: $messageText\n\nTap to inspect tactical location coordinates.")
                    .setSummaryText("EMERGENCY DISTRESS BEACON")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(longArrayOf(0, 600, 200, 600, 200, 800))
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setColor(0xFFFF1744.toInt()) // Vibrant Red Alert
            .setColorized(true)
            .setAutoCancel(true)
            .setContentIntent(mainPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_mapmode,
                "VIEW TACTICAL MAP",
                viewMapPendingIntent
            )

        try {
            with(NotificationManagerCompat.from(context)) {
                // Fixed ID for SOS to update/alert rather than duplicating
                notify(99999, builder.build())
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    /**
     * Clears cached notification history and removes notification when the user opens the chat.
     */
    fun clearPrivateMessagesFor(senderName: String) {
        conversationHistories.remove(senderName)
        try {
            val notificationId = ("chat_$senderName").hashCode()
            NotificationManagerCompat.from(context).cancel(notificationId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}