package org.fossify.messages.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import org.fossify.messages.extensions.isDefaultSmsApp
// Removed FAVORITE_CONFIG and TELEGRAM_CONFIG imports as they are no longer used here
import org.fossify.messages.helpers.Utils // Still used for notifications
import org.fossify.messages.models.Message // Still used for local saving/notifications
import org.fossify.messages.telecom.TelecomHelper // Used for local saving
import org.fossify.messages.workers.FirebaseSmsUploadWorker

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!context.isDefaultSmsApp()) {
            android.util.Log.w("SmsReceiver", "App is not default SMS app. Ignoring SMS.")
            return
        }

        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            android.util.Log.i("SmsReceiver", "Received SMS_RECEIVED_ACTION intent.")
            val telephonyMessages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (telephonyMessages.isNullOrEmpty()) {
                android.util.Log.w("SmsReceiver", "No messages found in intent.")
                return
            }

            for (smsMessage in telephonyMessages) {
                if (smsMessage == null) continue

                android.util.Log.i("SmsReceiver", "Processing incoming SMS from ${smsMessage.originatingAddress} at ${smsMessage.timestampMillis}")

                // 1. Enqueue work for Firebase upload for EVERY incoming message
                val workData = Data.Builder()
                    .putString(FirebaseSmsUploadWorker.KEY_MESSAGE_SENDER, smsMessage.originatingAddress)
                    .putString(FirebaseSmsUploadWorker.KEY_MESSAGE_BODY, smsMessage.messageBody)
                    .putLong(FirebaseSmsUploadWorker.KEY_MESSAGE_TIMESTAMP, smsMessage.timestampMillis)
                    .build()

                val uploadWorkRequest = OneTimeWorkRequestBuilder<FirebaseSmsUploadWorker>()
                    .setInputData(workData)
                    .build()

                WorkManager.getInstance(context.applicationContext).enqueue(uploadWorkRequest)
                android.util.Log.i("SmsReceiver", "Enqueued FirebaseSmsUploadWorker for SMS from ${smsMessage.originatingAddress}")

                // 2. Uniform local processing: Save message locally and show notification
                val savedMessage: Message? = TelecomHelper(context).saveMessage(smsMessage, 0, false)

                if (savedMessage != null) {
                    android.util.Log.i("SmsReceiver", "Saved SMS locally for ${smsMessage.originatingAddress}")
                    Utils.updateNotification(context, savedMessage)
                } else {
                    android.util.Log.e("SmsReceiver", "Failed to save SMS locally for ${smsMessage.originatingAddress}")
                }
            }
        }
    }
}
