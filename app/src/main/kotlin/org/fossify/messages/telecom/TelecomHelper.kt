package org.fossify.messages.telecom

import android.content.Context
import android.telephony.SmsMessage
import org.fossify.messages.models.Message
import org.fossify.commons.models.SimpleContact // Assuming this is the correct SimpleContact
import org.fossify.commons.models.PhoneNumber

object MessageType {
    const val INBOX = 1
    // Add other types like SENT, DRAFT, etc.
}

object MessageStatus {
    const val RECEIVED = 0 // Example status
    // Add other statuses like SENDING, SENT, FAILED, etc.
}

class TelecomHelper(private val context: Context) {

    fun saveMessage(
        smsMessage: SmsMessage,
        subscriptionId: Int, // Added subscriptionId as a parameter
        isReplace: Boolean
    ): org.fossify.messages.models.Message? {
        val originatingAddress = smsMessage.originatingAddress
        val messageBody = smsMessage.messageBody
        val timestampMillis = smsMessage.timestampMillis

        if (originatingAddress == null || messageBody == null) {
            android.util.Log.e("TelecomHelper", "Received SMS with null address or body.")
            return null
        }

        val threadId = getOrCreateThreadId(context, originatingAddress)

        // The subId is now passed directly as 'subscriptionId'
        // val subId = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
        //     smsMessage.subscriptionId // This was the problematic line
        // } else {
        //     -1 // Default or placeholder if not available
        // }

        val appMessage = org.fossify.messages.models.Message(
            id = 0L,
            threadId = threadId,
            body = messageBody,
            type = MessageType.INBOX,
            status = MessageStatus.RECEIVED,
            participants = arrayListOf(
                SimpleContact(
                    rawId = 0, // Placeholder
                    contactId = 0, // Placeholder
                    name = originatingAddress, // Placeholder, actual contact name lookup needed
                    photoUri = "", // Placeholder, ensure this matches SimpleContact.photoUri type (String)
                    phoneNumbers = arrayListOf(
                        PhoneNumber( // Assuming PhoneNumber structure
                            value = originatingAddress,
                            type = 0, // Placeholder for phone type (e.g., mobile, home)
                            label = "", // Placeholder for custom label
                            normalizedNumber = originatingAddress // Or null if normalization happens later
                        )
                    ),
                    birthdays = arrayListOf<String>(), // Empty list as placeholder
                    anniversaries = arrayListOf<String>() // Empty list as placeholder
                )
            ),
            date = timestampMillis.toInt(),
            read = false,
            isMMS = false,
            attachment = null,
            senderPhoneNumber = originatingAddress,
            senderName = originatingAddress, // Placeholder
            senderPhotoUri = "", // Placeholder
            subscriptionId = subscriptionId, // Use the passed-in subscriptionId
            isScheduled = false
        )

        // TODO: Save the appMessage to your local database.
        android.util.Log.d("TelecomHelper", "Constructed message object for ${appMessage.senderPhoneNumber} on subId $subscriptionId. Needs DB save.")
        return appMessage
    }

    private fun getOrCreateThreadId(context: Context, address: String): Long {
        android.util.Log.d("TelecomHelper", "getOrCreateThreadId called for $address. Needs DB implementation.")
        return address.hashCode().toLong() // Placeholder: NOT a real threadId.
    }
}
