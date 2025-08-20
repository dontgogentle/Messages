package org.fossify.messages.utils

import android.content.Context
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import org.fossify.messages.ui.TransactionInfo
import java.util.UUID
import java.security.MessageDigest

object TransactionProcessor {

    private val regexes = listOf(
        // Regex 1: Credit by transfer with account balance (Coop)
        // Acc XXXXXX is Credited by Rs.XXXXX on dd-MMM-yy from XXXXXX. Avl Bal Rs.XXXXX.XX
        Regex("""Acc\s+(\S+)\s+is\s+Credited\s+by\s+Rs\.([\d,]+\.?\d*)\s+on\s+(\d{2}-[A-Za-z]{3}-\d{2})\s+from\s+(\S+)\.\s+Avl\s+Bal\s+Rs\.([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE),
        // Regex 2: Debit by transfer with account balance (Coop)
        // Acc XXXXXX is Debited by Rs.XXXXX on dd-MMM-yy to XXXXXX. Avl Bal Rs.XXXXX.XX
        Regex("""Acc\s+(\S+)\s+is\s+Debited\s+by\s+Rs\.([\d,]+\.?\d*)\s+on\s+(\d{2}-[A-Za-z]{3}-\d{2})\s+to\s+(\S+)\.\s+Avl\s+Bal\s+Rs\.([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE),
        // Regex 3: Credit UPI (HDFC)
        // Rs XXXX credited to A/c XXXXXX by UPI Ref No XXXXXXXXXXXX (UPI ID: XXXXX@okhdfcbank) Name: XXXX on Date dd-MMM-yy. Linked to A/c No XXXXXX
        Regex("""Rs\s+([\d,]+\.?\d*)\s+credited\s+to\s+A/c\s+\S+\s+by\s+UPI\s+Ref\s+No\s+(\S+)\s+\(UPI\s+ID:\s+(\S+)\)\s+Name:\s+(.+?)\s+on\s+Date\s+(\d{2}-[A-Za-z]{3}-\d{2})\.\s*Linked\s+to\s+A/c\s+No\s+(\S+)""", RegexOption.IGNORE_CASE),
        // Regex 4: Debit UPI (HDFC)
        // Rs XXXX debited from A/c XXXXXX to XXXXX (UPI Ref No XXXXXXXXXXXX). on Date dd-MMM-yy. Linked to A/c No XXXXXX
        Regex("""Rs\s+([\d,]+\.?\d*)\s+debited\s+from\s+A/c\s+\S+\s+to\s+(.+?)\s+\(UPI\s+Ref\s+No\s+(\S+)\)\s*on\s+Date\s+(\d{2}-[A-Za-z]{3}-\d{2})\.\s*Linked\s+to\s+A/c\s+No\s+(\S+)""", RegexOption.IGNORE_CASE)
    )

    private fun cleanAmount(amount: String): String {
        return amount.replace(",", "")
    }
    
    fun parseMessage(body: String): TransactionInfo? {
        for (regex in regexes) {
            val matchResult = regex.find(body)
            if (matchResult != null) {
                val it = matchResult
                return when (regex) {
                    regexes[0] -> TransactionInfo( // Regex 1 (Coop Credit)
                        account = it.groupValues[1],
                        transactionType = "CREDIT",
                        amount = cleanAmount(it.groupValues[2]),
                        strDateInMessage = it.groupValues[3], 
                        date = null,                         
                        transactionReference = (it.groupValues[4].trim()).let { ref -> if (ref.endsWith("-")) ref.dropLast(1) else ref },
                        accountBalance = cleanAmount(it.groupValues[5]),
                        raw = body
                    )
                    regexes[1] -> TransactionInfo( // Regex 2 (Coop Debit)
                        account = it.groupValues[1],
                        transactionType = "DEBIT",
                        amount = cleanAmount(it.groupValues[2]),
                        strDateInMessage = it.groupValues[3], 
                        date = null,                         
                        transactionReference = (it.groupValues[4].trim()).let { ref -> if (ref.endsWith("-")) ref.dropLast(1) else ref },
                        accountBalance = cleanAmount(it.groupValues[5]),
                        raw = body
                    )
                    regexes[2] -> TransactionInfo( // Regex 3 (HDFC Credit UPI)
                        transactionType = "CREDIT",
                        amount = cleanAmount(it.groupValues[1]),
                        transactionReference = it.groupValues[2].trim(),
                        upi = it.groupValues[3].trim(), 
                        name = it.groupValues[4].trim(),
                        receivedFrom = it.groupValues[4].trim(),
                        strDateInMessage = it.groupValues[5], 
                        date = null,
                        account = it.groupValues[6], 
                        raw = body
                    )
                    regexes[3] -> TransactionInfo( // Regex 4 (HDFC Debit UPI)
                        transactionType = "DEBIT",
                        amount = cleanAmount(it.groupValues[1]), 
                        name = it.groupValues[2].trim(), 
                        transferredTo = it.groupValues[2].trim(),
                        transactionReference = it.groupValues[3].trim(),
                        strDateInMessage = it.groupValues[4], 
                        date = null,
                        account = it.groupValues[5], 
                        raw = body
                    )
                    else -> null 
                }
            }
        }
        return null
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
        val transactionInfoToPush = transactionInfoToPushOriginal.copy()
        val deterministicId = generateDeterministicId(transactionInfoToPush.raw)

        transactionInfoToPush.id = deterministicId
        transactionInfoToPush.date = System.currentTimeMillis() // Added for Step 3

        val dateForPath = transactionInfoToPush.strDateInMessage ?: "unknown-date" // Uses strDateInMessage
        val databaseReference = FirebaseDatabase.getInstance().getReference("$site/sms_by_date/$dateForPath/$deterministicId")

        databaseReference.setValue(transactionInfoToPush)
            .addOnSuccessListener {
                // Log.d("TransactionProcessor", "Transaction successfully written to Firebase with ID: $deterministicId at $site/sms_by_date/$dateForPath")
            }
            .addOnFailureListener { e ->
                Log.e("TransactionProcessor", "Failed to write transaction to Firebase ID: $deterministicId. Error: ${e.message}", e)
            }
    }

    private fun pushSingleTransactionInternal(context: Context?, transactionInfoToPushOriginal: TransactionInfo) {
        val transactionInfoToPush = transactionInfoToPushOriginal.copy()
        val site = "J5" 

        val deterministicId = generateDeterministicId(transactionInfoToPush.raw)
        transactionInfoToPush.id = deterministicId
        transactionInfoToPush.date = System.currentTimeMillis() // Added for Step 3

        val dateForPath = transactionInfoToPush.strDateInMessage ?: "unknown-date"
        val databaseReference = FirebaseDatabase.getInstance().getReference("$site/sms_by_date/$dateForPath/$deterministicId")

        databaseReference.setValue(transactionInfoToPush)
            .addOnSuccessListener {
                // Success
            }
            .addOnFailureListener {
                // Failure
            }
    }
}
