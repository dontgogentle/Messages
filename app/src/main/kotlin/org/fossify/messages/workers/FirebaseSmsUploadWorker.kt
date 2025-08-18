package org.fossify.messages.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.fossify.messages.models.Message // Your app's Message model
import org.fossify.messages.utils.TransactionProcessor // The object
// Import for TransactionInfo
import org.fossify.messages.ui.TransactionInfo
// Assuming SimpleContact and PhoneNumber are correctly imported
import org.fossify.commons.models.SimpleContact
import org.fossify.commons.models.PhoneNumber


class FirebaseSmsUploadWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_MESSAGE_SENDER = "key_message_sender"
        const val KEY_MESSAGE_BODY = "key_message_body"
        const val KEY_MESSAGE_TIMESTAMP = "key_message_timestamp"
        // Add other keys if needed, e.g., for thread ID, type, etc.
    }

    override suspend fun doWork(): Result {
        val sender = inputData.getString(KEY_MESSAGE_SENDER)
        val body = inputData.getString(KEY_MESSAGE_BODY)
        val timestampMillis = inputData.getLong(KEY_MESSAGE_TIMESTAMP, 0L)

        if (sender.isNullOrBlank() || body.isNullOrBlank() || timestampMillis == 0L) {
            Log.e("FirebaseSmsUploadWorker", "Missing input data for worker.")
            showFallbackNotification("SMS upload failed: missing input data.")
            return Result.failure()
        }

        // The 'message' object below is constructed based on your app's Message data class.
        // While not all its fields are directly used by TransactionProcessor's current methods,
        // its 'body' field is crucial for parsing.
        // It's kept here in case future versions of TransactionProcessor might consume more Message details
        // or if other parts of your app expect this worker to construct a full Message object.
        val appContext = applicationContext // Renaming for clarity if needed, or use applicationContext directly
        val constructedMessage = Message(
            id = 0L,
            body = body,
            type = 1, // Assuming 1 represents TYPE_INBOX or similar. Define constants.
            status = 0, // Assuming 0 for a received/new status. Define constants.
            participants = arrayListOf(
                SimpleContact(
                    rawId = 0, // Placeholder
                    contactId = 0, // Placeholder
                    name = sender, // Use sender as name placeholder
                    photoUri = "", // Placeholder
                    phoneNumbers = arrayListOf(
                        PhoneNumber( // Assuming PhoneNumber structure
                            value = sender,
                            type = 0, // Placeholder for phone type
                            label = "", // Placeholder
                            normalizedNumber = sender
                        )
                    ),
                    birthdays = arrayListOf(),
                    anniversaries = arrayListOf()
                )
            ),
            date = timestampMillis.toInt(), // Convert Long to Int. Consider changing Message.date to Long.
            read = false, // New messages are typically unread.
            threadId = 0L, // Placeholder. This needs a proper value or generation strategy.
            isMMS = false, // This worker seems to be for SMS.
            attachment = null, // No attachment for standard SMS.
            senderPhoneNumber = sender,
            senderName = sender, // Placeholder, ideally look up contact name.
            senderPhotoUri = "", // Placeholder, ideally look up contact photo.
            subscriptionId = -1, // Placeholder. If you have subId for the worker, pass it.
            isScheduled = false
        )

        return try {
            // 1. Use TransactionProcessor.parseMessage to get TransactionInfo from the message body
            val transactionInfo: TransactionInfo? = TransactionProcessor.parseMessage(constructedMessage.body)

            if (transactionInfo != null) {
                // 2. If parsing is successful, push it to Firebase
                // pushToFirebase expects a List<TransactionInfo>
                TransactionProcessor.pushToFirebase(listOf(transactionInfo))
                Log.d("FirebaseSmsUploadWorker", "Successfully parsed and initiated push for message from $sender, parsed transactionInfo:"+transactionInfo)
                showNormalNotification("SMS from $sender uploaded to Firebase.")
                Result.success()
            } else {
                Log.w("FirebaseSmsUploadWorker", "Message from $sender with body '${constructedMessage.body.take(50)}...' did not parse into a transaction. Not uploading.")
                // Decide if not parsing should be a worker failure or success.
                // If it's common for messages not to be transactions, success might be appropriate.
                Result.success() // Or Result.failure()
            }
        } catch (e: Exception) {
            Log.e("FirebaseSmsUploadWorker", "Error processing/uploading message for $sender. Body: '${constructedMessage.body.take(50)}...'", e)
            showFallbackNotification("SMS upload failed for $sender: ${e.localizedMessage}")
            Result.failure()
        }
    }

    private fun showFallbackNotification(message: String) {
        try {
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val builder = android.app.Notification.Builder(applicationContext)
                .setContentTitle("SMS Upload Error")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setAutoCancel(true)
            notificationManager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), builder.build())
        } catch (e: Exception) {
            Log.e("FirebaseSmsUploadWorker", "Failed to show fallback notification: ${e.localizedMessage}", e)
        }
    }

    private fun showNormalNotification(message: String) {
        try {
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val builder = android.app.Notification.Builder(applicationContext)
                .setContentTitle("SMS Uploaded")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setAutoCancel(true)
            notificationManager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), builder.build())
        } catch (e: Exception) {
            Log.e("FirebaseSmsUploadWorker", "Failed to show normal notification: ${e.localizedMessage}", e)
        }
    }
}
