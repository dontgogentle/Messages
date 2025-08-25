package org.fossify.messages.utils

import android.content.Context // Keep if used, otherwise consider removing
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import org.fossify.messages.helpers.FirebaseConstants // Added import
import org.fossify.messages.models.Message
import org.fossify.messages.ui.TransactionInfo // Ensure this is the correct TransactionInfo
import java.util.UUID
import java.security.MessageDigest

object TransactionProcessor {

    // Using the 4 new regexes you specified
    private val regexes = listOf(
        // Regex 1: ICICI Bank Account credited:Rs. XXX on DD-MMM-YY. Info XXXXX. Available Balance is Rs. XXX
        Regex("""^ICICI Bank Account\s+(\w+)\s+credited:Rs\.\s*([\d,]+\.?\d{0,2})\s+on\s+(\d{2}-\w{3}-\d{2})\.\s*Info\s*([^.]+?)\.?\s*Available Balance is Rs\.\s*([\d,]+\.?\d{0,2})""", RegexOption.IGNORE_CASE),
        // Regex 2: Dear Customer, Acct XXXXX is credited with Rs XXX on DD-MMM-YY from XXXXX. UPI:XXXXX
        Regex("""^Dear Customer, Acct\s+(\w+)\s+is credited with Rs\s*([\d,]+\.?\d{0,2})\s+on\s+(\d{2}-\w{3}-\d{2})\s+from\s+(.+?)\.\s*UPI:(\S+)""", RegexOption.IGNORE_CASE),
        // Regex 3: ICICI Bank Acct XXXXX debited for Rs XXX on DD-MMM-YY; XXXXX credited. UPI:XXXXX
        Regex("""^ICICI Bank Acct\s+(\w+)\s+debited for Rs\s*([\d,]+\.?\d{0,2})\s+on\s+(\d{2}-\w{3}-\d{2});\s*(.+?)\s+credited\.\s*UPI:(\S+)""", RegexOption.IGNORE_CASE),
        // Regex 4: Acct XXXXX is credited with Rs. XXX on DD-MMM-YY from XXXXX UPI:XXXXX (variation of Regex 2, no "Dear Customer")
        Regex("""Acct\s+(\w+)\s+is credited with Rs\.?\s*([\d,]+\.?\d{0,2})\s+on\s+(\d{2}-\w{3}-\d{2})\s+from\s+(.+?)\s+UPI:(\S+)""", RegexOption.IGNORE_CASE)
    )

    private fun cleanAmount(amount: String): String {
        return amount.replace(",", "")
    }

    fun parseMessage(message: Message): TransactionInfo? {
        val body = message.body
        for (regex in regexes) {
            val matchResult = regex.find(body)
            if (matchResult != null) {
                val it = matchResult
                return when (regex) {
                    regexes[0] -> TransactionInfo( // Regex 1 (ICICI Credit)
                        account = it.groupValues[1],           // (\w+) - Account
                        transactionType = "CREDIT",
                        amount = cleanAmount(it.groupValues[2]), // ([\d,]+\.?\d{0,2}) - Amount
                        strDateInMessage = it.groupValues[3],    // (\d{2}-\w{3}-\d{2}) - Date
                        date = message.date,
                        transactionReference = it.groupValues[4].trim(), // ([^.]+?) - Info
                        accountBalance = cleanAmount(it.groupValues[5]), // ([\d,]+\.?\d{0,2}) - Available Balance
                        raw = body
                        // Other fields default or null
                    )
                    regexes[1] -> TransactionInfo( // Regex 2 (Dear Customer Credit UPI)
                        account = it.groupValues[1],           // (\w+) - Account
                        transactionType = "CREDIT",
                        amount = cleanAmount(it.groupValues[2]), // ([\d,]+\.?\d{0,2}) - Amount
                        strDateInMessage = it.groupValues[3],    // (\d{2}-\w{3}-\d{2}) - Date
                        date = message.date,
                        receivedFrom = it.groupValues[4].trim(), // (.+?) - From/Sender Name
                        name = it.groupValues[4].trim(),         // Also use for 'name' field
                        upi = it.groupValues[5].trim(),          // (\S+) - UPI Reference
                        transactionReference = "UPI:" + it.groupValues[5].trim(), // Construct a ref
                        raw = body
                        // Other fields default or null
                    )
                    regexes[2] -> TransactionInfo( // Regex 3 (ICICI Debit UPI)
                        account = it.groupValues[1],           // (\w+) - Account
                        transactionType = "DEBIT",
                        amount = cleanAmount(it.groupValues[2]), // ([\d,]+\.?\d{0,2}) - Amount
                        strDateInMessage = it.groupValues[3],    // (\d{2}-\w{3}-\d{2}) - Date
                        date = message.date,
                        transferredTo = it.groupValues[4].trim(),// (.+?) - Transferred To (before " credited.")
                        name = it.groupValues[4].trim(),         // Also use for 'name' field
                        upi = it.groupValues[5].trim(),          // (\S+) - UPI Reference
                        transactionReference = "UPI:" + it.groupValues[5].trim(), // Construct a ref
                        raw = body
                        // Other fields default or null
                    )
                    regexes[3] -> TransactionInfo( // Regex 4 (Generic Acct Credit UPI)
                        account = it.groupValues[1],           // (\w+) - Account
                        transactionType = "CREDIT",
                        amount = cleanAmount(it.groupValues[2]), // ([\d,]+\.?\d{0,2}) - Amount
                        strDateInMessage = it.groupValues[3],    // (\d{2}-\w{3}-\d{2}) - Date
                        date = message.date,
                        receivedFrom = it.groupValues[4].trim(), // (.+?) - From/Sender Name
                        name = it.groupValues[4].trim(),         // Also use for 'name' field
                        upi = it.groupValues[5].trim(),          // (\S+) - UPI Reference
                        transactionReference = "UPI:" + it.groupValues[5].trim(), // Construct a ref
                        raw = body
                        // Other fields default or null
                    )
                    else -> null // Should not be reached
                }
            }
        }
        return null // No regex matched
    }

    private fun generateDeterministicId(rawMessage: String): String {
        val bytes = rawMessage.toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun pushToFirebase(transactions: List<TransactionInfo>, site: String = "J5") {
        Log.d("TransactionProcessor", "Batch pushToFirebase called with ${transactions.size} transactions for site $site.")
        transactions.forEach { transactionInfoToPush ->
            pushSingleTransactionNoCheck(transactionInfoToPush, site)
        }
    }

    public fun pushSingleTransactionNoCheck(transactionInfoToPushOriginal: TransactionInfo, site: String) {
        val transactionInfoToPush = transactionInfoToPushOriginal.copy() // Make a copy
        val deterministicId = generateDeterministicId(transactionInfoToPush.raw)

        transactionInfoToPush.id = deterministicId // Set the ID on the copy
//        transactionInfoToPush.date = System.currentTimeMillis() // Set current timestamp on the copy

        val dateForPath = transactionInfoToPush.strDateInMessage ?: "unknown-date"
        val databaseReference = FirebaseDatabase.getInstance().getReference("$site/${FirebaseConstants.SMS_NODES_PATH}/$dateForPath/$deterministicId")

        databaseReference.setValue(transactionInfoToPush)
            .addOnSuccessListener {
                // Log.d("TransactionProcessor", "Transaction successfully written to Firebase with ID: $deterministicId at $site/${FirebaseConstants.SMS_NODES_PATH}/$dateForPath")
            }
            .addOnFailureListener { e ->
                Log.e("TransactionProcessor", "Failed to write transaction to Firebase ID: $deterministicId. Error: ${e.message}", e)
            }
    }
}
