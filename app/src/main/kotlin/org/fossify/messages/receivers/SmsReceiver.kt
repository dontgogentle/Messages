package org.fossify.messages.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.isNumberBlocked
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.SimpleContact
import org.fossify.messages.extensions.*
import org.fossify.messages.helpers.FirebaseSyncState
import org.fossify.messages.helpers.ReceiverUtils.isMessageFilteredOut
import org.fossify.messages.helpers.refreshMessages
import org.fossify.messages.models.Message
import org.fossify.messages.ui.TransactionInfo
import org.fossify.messages.utils.TransactionProcessor

class SmsReceiver : BroadcastReceiver() {
    private val TAG = "SmsReceiverTrace" // Added TAG for logging

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive triggered! Action: ${intent.action}")

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages == null) {
            Log.e(TAG, "getMessagesFromIntent returned null. Cannot process SMS.")
            return
        }
        Log.d(TAG, "Extracted ${messages.size} message parts from intent.")

        var address = ""
        var body = ""
        var subject = ""
        var date = 0L
        var threadId = 0L
        var status = Telephony.Sms.STATUS_NONE
        val type = Telephony.Sms.MESSAGE_TYPE_INBOX
        val read = 0
        val subscriptionId = intent.getIntExtra("subscription", -1)
        Log.d(TAG, "Initial subscriptionId from intent: $subscriptionId")


        val privateCursor = context.getMyContactsCursor(false, true)
        Log.d(TAG, "Before ensureBackgroundThread block.")
        ensureBackgroundThread {
            Log.d(TAG, "Inside ensureBackgroundThread block.")
            messages.forEachIndexed { index, smsMessage ->
                address = smsMessage.originatingAddress ?: ""
                subject = smsMessage.pseudoSubject
                status = smsMessage.status
                body += smsMessage.messageBody
                // Note: 'date' is set to System.currentTimeMillis() after the loop, might want to use smsMessage.timestampMillis if available and desired
                Log.d(TAG, "Processing message part $index: address=$address, subject=$subject, status=$status, bodyPartLength=${smsMessage.messageBody?.length}, timestampMillis=${smsMessage.timestampMillis}")
            }
            date = System.currentTimeMillis() // Date for the aggregated message
            threadId = context.getThreadId(address)
            Log.d(TAG, "Aggregated message: address=$address, bodyLength=${body.length}, date=$date, threadId=$threadId")

            if (context.baseConfig.blockUnknownNumbers) {
                Log.d(TAG, "Block unknown numbers is ON. Checking if sender '$address' exists.")
                val simpleContactsHelper = SimpleContactsHelper(context)
                simpleContactsHelper.exists(address, privateCursor) { exists ->
                    Log.d(TAG, "Sender '$address' exists: $exists")
                    if (exists) {
                        Log.d(TAG, "Sender exists or not blocked. Calling handleMessage.")
                        handleMessage(context, address, subject, body, date, read, threadId, type, subscriptionId, status)
                    } else {
                        Log.d(TAG, "Sender does not exist and unknown numbers are blocked. Message from '$address' dropped.")
                    }
                }
            } else {
                Log.d(TAG, "Block unknown numbers is OFF. Calling handleMessage for '$address'.")
                handleMessage(context, address, subject, body, date, read, threadId, type, subscriptionId, status)
            }
            Log.d(TAG, "ensureBackgroundThread block finished.")
        }
        Log.d(TAG, "onReceive completed.")
    }

    private fun handleMessage(
        context: Context,
        address: String,
        subject: String,
        body: String,
        date: Long,
        read: Int,
        threadId: Long,
        type: Int,
        subscriptionId: Int,
        status: Int
    ) {
        Log.d(TAG, "handleMessage called. Address: $address, Subject: $subject, Body snippet: ${body.take(50)}, Date: $date, ThreadId: $threadId, Type: $type, SubId: $subscriptionId, Status: $status")

        val isFiltered = isMessageFilteredOut(context, body)
        Log.d(TAG, "isMessageFilteredOut returned: $isFiltered for body: ${body.take(50)}")
        if (isFiltered) {
            Log.d(TAG, "Message is filtered out. Returning from handleMessage.")
            return
        }

        val photoUri = SimpleContactsHelper(context).getPhotoUriFromPhoneNumber(address)
        Log.d(TAG, "Photo URI for $address: $photoUri")
        val bitmap = context.getNotificationBitmap(photoUri) // Assuming this is light and okay on this thread for now
        
        Log.d(TAG, "Before Handler.post block for UI-related work and DB operations.")
        Handler(Looper.getMainLooper()).post { // This posts to the main thread
            Log.d(TAG, "Inside Handler.post (MainThread). Checking if number '$address' is blocked.")
            if (!context.isNumberBlocked(address)) {
                Log.d(TAG, "Number '$address' is NOT blocked. Proceeding with DB operations and notification via ensureBackgroundThread.")
                // It's generally better to do DB operations off the main thread.
                val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
                ensureBackgroundThread {
                    Log.d(TAG, "Inside ensureBackgroundThread (for DB ops) within Handler.post.")
                    Log.d(TAG, "Before insertNewSMS. Address: $address, Body: ${body.take(30)}")
                    val newMessageId = context.insertNewSMS(address, subject, body, date, read, threadId, type, subscriptionId)
                    Log.d(TAG, "After insertNewSMS. New Message ID: $newMessageId")

                    val conversation = context.getConversations(threadId).firstOrNull()
                    if (conversation == null) {
                        Log.e(TAG, "Conversation not found for threadId: $threadId. Cannot update.")
                        // Potentially skip further conversation/message specific updates if no conversation
                    } else {
                        Log.d(TAG, "Conversation found for threadId: $threadId. Attempting to insert/update.")
                        try {
                            context.insertOrUpdateConversation(conversation)
                            Log.d(TAG, "insertOrUpdateConversation successful for threadId: $threadId")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in insertOrUpdateConversation for threadId: $threadId", e)
                        }
                    }

                    val senderName = context.getNameFromAddress(address, privateCursor)
                    Log.d(TAG, "Sender name for $address: $senderName")
                    privateCursor?.close() // Close cursor when done

                    val phoneNumber = PhoneNumber(address, 0, "", address)
                    val participant = SimpleContact(0, 0, senderName, photoUri, arrayListOf(phoneNumber), ArrayList(), ArrayList())
                    val participants = arrayListOf(participant)
                    val messageDateSeconds = (date / 1000).toInt()

                    val message = Message(newMessageId, body, type, status, participants, messageDateSeconds, false, threadId, false, null, address, senderName, photoUri, subscriptionId)
                    Log.d(TAG, "Before messagesDB.insertOrUpdate. Message ID: $newMessageId")
                    context.messagesDB.insertOrUpdate(message)
                    Log.d(TAG, "After messagesDB.insertOrUpdate. Message ID: $newMessageId")

                    if (context.config.isArchiveAvailable) {
                        Log.d(TAG, "Archive is available. Updating conversation archived status for threadId: $threadId to false.")
                        context.updateConversationArchivedStatus(threadId, false)
                    }
                    Log.d(TAG, "Before refreshMessages()")
                    refreshMessages()
                    Log.d(TAG, "After refreshMessages()")

                    Log.d(TAG, "Before showReceivedMessageNotification. Message ID: $newMessageId, Address: $address, Body: ${body.take(30)}")
                    context.showReceivedMessageNotification(newMessageId, address, body, threadId, bitmap)
                    Log.d(TAG, "After showReceivedMessageNotification.")

                    // Firebase push logic
                    Log.d(TAG, "--- Firebase Push Logic Start ---")
                    Log.d(TAG, "Body to parse for Firebase: ${body.take(100)}")
                    val parsed: TransactionInfo? = try {
                        TransactionProcessor.parseMessage(body)
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception during TransactionProcessor.parseMessage", e)
                        null
                    }
                    Log.d(TAG, "Parsed result: $parsed")

                    if (parsed != null) {
                        Log.d(TAG, "Parsed result is not null. Proceeding to push to Firebase.")
                        try {
                            Log.d(TAG, "Before calling pushSingleTransactionNoCheck. Parsed: $parsed, Source: J5")
                            TransactionProcessor.pushSingleTransactionNoCheck(parsed, "J5")
                            Log.d(TAG, "After calling pushSingleTransactionNoCheck successfully.")
                        } catch (e: Exception) {
                            Log.e(TAG, "Exception during TransactionProcessor.pushSingleTransactionNoCheck", e)
                        }
                    } else {
                        Log.d(TAG, "Parsed result is null or error occurred. Skipping Firebase push.")
                    }
                    Log.d(TAG, "--- Firebase Push Logic End ---")
                    Log.d(TAG, "ensureBackgroundThread (for DB ops) within Handler.post finished.")
                }
            } else {
                Log.d(TAG, "Number '$address' IS blocked. Message dropped.")
            }
            Log.d(TAG, "Handler.post (MainThread) finished.")
        }
        Log.d(TAG, "handleMessage completed for address: $address")
    }
}
