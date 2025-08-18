package org.fossify.messages.helpers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import org.fossify.messages.R // Assuming you have R.drawable.ic_notification and R.string.app_name
import org.fossify.messages.activities.MainActivity // Or your target activity for notification click
import org.fossify.messages.models.Message // Ensure this import is correct
import kotlin.text.toIntOrNull

object Utils {

    private const val NOTIFICATION_CHANNEL_ID = "fossify_messages_channel"
    private const val NOTIFICATION_ID_PREFIX = "message_notification_" // Used for dynamic notification IDs

    fun updateNotification(context: Context, message: Message) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = context.getString(R.string.notification_channel_name) // Example: Get from strings.xml
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, importance)
            channel.description = context.getString(R.string.notification_channel_description) // Example: Get from strings.xml
            notificationManager.createNotificationChannel(channel)
        }

        // Intent to launch when notification is clicked
        // This should typically open the specific conversation.
        val intent = Intent(context, MainActivity::class.java).apply {
            // TODO: Add extras to identify the conversation, e.g., threadId or address
            // For example:
            // putExtra("thread_id", message.threadId)
            // putExtra("address", message.address)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        // Ensure a unique request code for PendingIntent if you have multiple notifications
        // that need to be distinct or if their extras change.
        // Using message.threadId (if consistently available and numeric) or message.address.hashCode()
        // can help create unique request codes.
        val requestCode = message.threadId?.toString()?.toIntOrNull() ?: message.senderPhoneNumber.hashCode()

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder =
            NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher) // TODO: Replace with your actual notification icon
                .setContentTitle(if (message.senderName.isNotBlank()) message.senderName else message.senderPhoneNumber) // Or contact name if available from your Message model
                .setContentText(message.body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true) // Dismiss notification when clicked
                .setGroup("new_messages_group") // Optional: Group notifications by conversation or app

        // Use a dynamic notification ID, e.g., based on threadId, to update existing notifications
        // or show separate ones for different conversations.
        val notificationId = (NOTIFICATION_ID_PREFIX + (message.threadId ?: message.senderPhoneNumber.hashCode())).hashCode()

        try {
            notificationManager.notify(notificationId, notificationBuilder.build())
        } catch (e: SecurityException) {
            // Handle cases where POST_NOTIFICATIONS permission might be missing (Android 13+).
            // Your app should request this permission if targeting Android 13 or higher.
            android.util.Log.e("Utils", "Failed to show notification due to SecurityException. " +
                "Ensure POST_NOTIFICATIONS permission is granted.", e)
        }
    }
}

