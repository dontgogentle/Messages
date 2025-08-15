package org.fossify.messages.utils

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.fossify.messages.ui.TransactionInfo
import java.math.BigInteger
import java.security.MessageDigest

object TransactionProcessor {

    private const val TAG = "TransactionProcessor"

    fun cleanAmount(amountStr: String): String {
        return amountStr.replace(Regex("Rs\\.?\\s*|,"), "").replace(Regex("\\.00$"), "").trim()
    }

    private fun generateDeterministicId(rawMessage: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hashBytes = md.digest(rawMessage.toByteArray(Charsets.UTF_8))
        return BigInteger(1, hashBytes).toString(16).padStart(64, '0')
    }

    fun parseMessage(body: String): TransactionInfo? {
        val parseTag = "$TAG-Parse"

        val regex1 = Regex("""^ICICI Bank Account\s+(\w+)\s+credited:Rs\.\s*([\d,]+\.?\d{0,2})\s+on\s+(\d{2}-\w{3}-\d{2})\.\s*Info\s*([^.]+?)\.?\s*Available Balance is Rs\.\s*([\d,]+\.?\d{0,2})""", RegexOption.IGNORE_CASE)
        regex1.find(body)?.let {
            Log.d(parseTag, "Matched Regex 1")
            return TransactionInfo(
                account = it.groupValues[1],
                transactionType = "CREDIT",
                amount = cleanAmount(it.groupValues[2]),
                date = it.groupValues[3],
                transactionReference = (it.groupValues[4].trim()).let { ref -> if (ref.endsWith("-")) ref.dropLast(1) else ref },
                accountBalance = cleanAmount(it.groupValues[5]),
                raw = body
            )
        }

        val regex2 = Regex("""^Dear Customer, Acct\s+(\w+)\s+is credited with Rs\s*([\d,]+\.?\d{0,2})\s+on\s+(\d{2}-\w{3}-\d{2})\s+from\s+(.+?)\.\s*UPI:(\S+)""", RegexOption.IGNORE_CASE)
        regex2.find(body)?.let {
            Log.d(parseTag, "Matched Regex 2")
            val upiRef = it.groupValues[5].split('-').first()
            val receivedFromName = it.groupValues[4].trim()
            return TransactionInfo(
                account = it.groupValues[1],
                transactionType = "CREDIT",
                amount = cleanAmount(it.groupValues[2]),
                date = it.groupValues[3],
                transactionReference = "UPI:$upiRef",
                upi = upiRef,
                receivedFrom = receivedFromName,
                name = receivedFromName,
                raw = body
            )
        }

        val regex3 = Regex("""^ICICI Bank Acct\s+(\w+)\s+debited for Rs\s*([\d,]+\.?\d{0,2})\s+on\s+(\d{2}-\w{3}-\d{2});\s*(.+?)\s+credited\.\s*UPI:(\S+)""", RegexOption.IGNORE_CASE)
        regex3.find(body)?.let {
            Log.d(parseTag, "Matched Regex 3")
            val upiRef = it.groupValues[5].split('-').first()
            val transferredToName = it.groupValues[4].trim()
            return TransactionInfo(
                account = it.groupValues[1],
                transactionType = "DEBIT",
                amount = cleanAmount(it.groupValues[2]),
                date = it.groupValues[3],
                transactionReference = "UPI:$upiRef",
                upi = upiRef,
                transferredTo = transferredToName,
                name = transferredToName,
                raw = body
            )
        }

        val regex4 = Regex("""Acct\s+(\w+)\s+is credited with Rs\.?\s*([\d,]+\.?\d{0,2})\s+on\s+(\d{2}-\w{3}-\d{2})\s+from\s+(.+?)\s+UPI:(\S+)""", RegexOption.IGNORE_CASE)
        regex4.find(body)?.let {
            Log.d(parseTag, "Matched Regex 4 (general credit with UPI)")
            val upiRef = it.groupValues[5].split('-').first()
            val receivedFromName = it.groupValues[4].trim()
            return TransactionInfo(
                account = it.groupValues[1],
                transactionType = "CREDIT",
                amount = cleanAmount(it.groupValues[2]),
                date = it.groupValues[3],
                transactionReference = "UPI:$upiRef",
                upi = upiRef,
                name = receivedFromName,
                receivedFrom = receivedFromName,
                raw = body
            )
        }

        Log.d(parseTag, "No regex matched for body: $body")
        return null
    }

    fun pushToFirebase(transactionInfoToPush: TransactionInfo) { // Renamed parameter
        val pushTag = "$TAG-Push"
        val site = getSiteForAccount(transactionInfoToPush.account)
        // Generate ID from the specific transactionInfoToPush instance for this call
        val deterministicId = generateDeterministicId(transactionInfoToPush.raw)

        // Log details of the transaction being processed in this specific call
        Log.d(pushTag, "Attempting to push. Raw Preview: ${transactionInfoToPush.raw.take(50)}..., Date: ${transactionInfoToPush.date}, Deterministic ID: $deterministicId")

        val firebasePath = "$site/sms_by_date/${transactionInfoToPush.date}/$deterministicId"
        Log.d(pushTag, "Firebase Path for this call: $firebasePath")

        val databaseReference = FirebaseDatabase.getInstance().getReference(firebasePath)

        // It's crucial that this 'transactionInfoToPush' is the one captured by the listener.
        // Kotlin's anonymous objects (like ValueEventListener here) capture the state of
        // variables from their enclosing scope at the time of creation.
        // Since transactionInfoToPush is a parameter, its reference is stable for this function call.

        databaseReference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Log which transaction this onDataChange is for, using its unique ID and raw preview
                Log.d(pushTag, "[onDataChange for ID: $deterministicId, Path: $firebasePath]. Raw preview: ${transactionInfoToPush.raw.take(30)}")
                if (snapshot.exists()) {
                    Log.d(pushTag, "Transaction ALREADY EXISTS in Firebase. Path: $firebasePath. Skipping push for ID: $deterministicId.")
                } else {
                    Log.d(pushTag, "Transaction DOES NOT EXIST. Pushing to Firebase. Path: $firebasePath for ID: $deterministicId.")
                    // Use the 'transactionInfoToPush' captured by this specific listener instance
                    databaseReference.setValue(transactionInfoToPush.copy(isRawExpanded = false))
                        .addOnSuccessListener {
                            Log.d(pushTag, "Firebase push SUCCESSFUL for ID: $deterministicId. Path: $firebasePath")
                        }
                        .addOnFailureListener { e ->
                            Log.e(pushTag, "Firebase push FAILED for ID: $deterministicId. Path: $firebasePath", e)
                        }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(pushTag, "Firebase read CANCELLED for ID: $deterministicId. Path: $firebasePath. Error: ${error.message}", error.toException())
            }
        })
        Log.d(pushTag, "Listener attached for ID: $deterministicId. Path: $firebasePath. Raw preview: ${transactionInfoToPush.raw.take(30)}")
    }


    fun pushToFirebase7(info: TransactionInfo) {
        val pushTag = "$TAG-Push"
        Log.d(pushTag, "--- IKKKSTARTING pushToFirebase (setValue TEST) ---")
        Log.d(pushTag, "TransactionInfo RAW: ${info.raw.take(50)}...")

        val testRef = FirebaseDatabase.getInstance().getReference("testSetValueNode") // New simple path for writing
        val dataToSet = mapOf(
            "message" to "Hello from setValue test!",
            "timestamp" to System.currentTimeMillis(),
            "rawPreview" to info.raw.take(20)
        )

        Log.d(pushTag, "Attempting setValue on 'testSetValueNode' with data: $dataToSet")

        testRef.setValue(dataToSet)
            .addOnSuccessListener {
                Log.d(pushTag, "!!!!!!!! setValue TEST: onSuccessListener CALLED !!!!!!!!")
                Log.d(pushTag, "Successfully wrote data to 'testSetValueNode'")
            }
            .addOnFailureListener { e ->
                Log.e(pushTag, "!!!!!!!! setValue TEST: onFailureListener CALLED !!!!!!!!")
                Log.e(pushTag, "Failed to write data to 'testSetValueNode'", e)
            }

        Log.d(pushTag, "--- FINISHED pushToFirebase (setValue TEST) - setValue call made ---")

        // ... (previous addListenerForSingleValueEvent test and original logic are commented out) ...
    }

    fun pushToFirebase2(info: TransactionInfo) {
        val pushTag = "$TAG-Push"
        Log.d(pushTag, "--- STARTING pushToFirebase (MINIMAL TEST) ---")
        Log.d(pushTag, "TransactionInfo RAW: ${info.raw.take(50)}...")

        // Ensure MessagesApplication.kt is using the IP your React Native app uses (100.120.198.49)
        val testRef = FirebaseDatabase.getInstance().getReference("testNode") // Simplest possible path

        Log.d(pushTag, "Attempting addListenerForSingleValueEvent on 'testNode'")

        testRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(pushTag, "!!!!!!!! MINIMAL TEST: onDataChange CALLED !!!!!!!!")
                Log.d(pushTag, "TestNode Snapshot exists: ${snapshot.exists()}")
                if (snapshot.exists()) {
                    Log.d(pushTag, "TestNode Data: ${snapshot.value}")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(pushTag, "!!!!!!!! MINIMAL TEST: onCancelled CALLED !!!!!!!!")
                Log.e(pushTag, "Error: ${error.message}", error.toException())
                Log.e(pushTag, "Error Code: ${error.code}, Details: ${error.details}")
            }
        })

        Log.d(pushTag, "--- FINISHED pushToFirebase (MINIMAL TEST) - Listener call made ---")
    }

    fun getSiteForAccount(account: String): String {
        return when (account.lowercase().replace("xx","")) {
            "665" -> "J5"
            else -> "UNKNOWN"
        }
    }
}
