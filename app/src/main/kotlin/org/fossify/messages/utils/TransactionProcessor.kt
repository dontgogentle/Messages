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

    fun pushToFirebase(allPotentialTransactions: List<TransactionInfo>) {
        val syncTag = "$TAG-SyncLogic"
        if (allPotentialTransactions.isEmpty()) {
            Log.d(syncTag, "No transactions to process.")
            return
        }

        val transactionsBySite = allPotentialTransactions.groupBy { getSiteForAccount(it.account) }

        for ((site, siteTransactions) in transactionsBySite) {
            if (site == "UNKNOWN") {
                Log.w(syncTag, "Skipping ${siteTransactions.size} transactions for UNKNOWN site.")
                continue
            }
            Log.d(syncTag, "Processing site: $site")
            if (siteTransactions.isEmpty()) {
                Log.d(syncTag, "No transactions for site $site after filtering.")
                continue
            }
            val transactionsByDate = siteTransactions.groupBy { it.date }
            val sortedDates = transactionsByDate.keys.sortedDescending()

            processDatesSequentiallyForSite(site, sortedDates, 0, transactionsByDate)
        }
    }

    private fun processDatesSequentiallyForSite(
        site: String,
        dates: List<String>,
        currentIndex: Int,
        transactionsByDate: Map<String, List<TransactionInfo>> // Contains transactions only for the current 'site'
    ) {
        val siteDateTag = "$TAG-SiteDateProc"
        if (currentIndex >= dates.size) {
            Log.d(siteDateTag, "Site $site: Finished processing all dates or processing stopped earlier.")
            return
        }

        val dateStr = dates[currentIndex]
        val deviceMessagesForDateAndSite = transactionsByDate[dateStr] ?: run {
            Log.w(siteDateTag, "Site $site, Date $dateStr: No device messages found in map (should not happen if keys are correct). Skipping to next date.")
            processDatesSequentiallyForSite(site, dates, currentIndex + 1, transactionsByDate)
            return
        }

        // This check might be redundant if the above `run` block handles missing keys,
        // but good for safety if a key exists with an empty list.
        if (deviceMessagesForDateAndSite.isEmpty()) {
            Log.d(siteDateTag, "Site $site, Date $dateStr: Device messages list is empty. Skipping to next date.")
            processDatesSequentiallyForSite(site, dates, currentIndex + 1, transactionsByDate)
            return
        }

        val firebasePathForDate = "$site/sms_by_date/$dateStr"
        val dateMessagesRef = FirebaseDatabase.getInstance().getReference(firebasePathForDate)

        Log.d(siteDateTag, "Site $site, Date: $dateStr. Querying Firebase count. Device messages for this date & site: ${deviceMessagesForDateAndSite.size}")


//        for (transactionToPush in deviceMessagesForDateAndSite) {
//            pushSingleTransactionInternal(transactionToPush, site)
//        }

        dateMessagesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val firebaseMessageCount = snapshot.childrenCount.toInt()
                Log.d(siteDateTag, "Site $site, Date: $dateStr. Firebase count: $firebaseMessageCount. Device messages: ${deviceMessagesForDateAndSite.size}")

                if (firebaseMessageCount < deviceMessagesForDateAndSite.size) {
                    Log.d(siteDateTag, "Site $site, Date: $dateStr. Firebase has fewer messages (${firebaseMessageCount}) than device (${deviceMessagesForDateAndSite.size}). Pushing all ${deviceMessagesForDateAndSite.size} device messages for this date & site.")
                    for (transactionToPush in deviceMessagesForDateAndSite) {
                        pushSingleTransactionInternal(transactionToPush, site)
                    }
                    // After attempting to push for this date, continue to the previous day for this site
                    Log.d(siteDateTag, "Site $site, Date: $dateStr. Finished pushing. Proceeding to next older date.")
                    processDatesSequentiallyForSite(site, dates, currentIndex + 1, transactionsByDate)
                } else {
                    Log.d(siteDateTag, "Site $site, Date: $dateStr. Firebase count ($firebaseMessageCount) is >= device count (${deviceMessagesForDateAndSite.size}). STOPPING further processing for this site.")
                    // Stop condition met for this site.
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(siteDateTag, "Site $site, Date: $dateStr. Firebase read for count CANCELLED. Error: ${error.message}. Continuing to next older date for this site as a precaution.", error.toException())
                processDatesSequentiallyForSite(site, dates, currentIndex + 1, transactionsByDate)
            }
        })
    }

    private fun pushSingleTransactionInternal(transactionInfoToPush: TransactionInfo, site: String) {
        val pushSingleTag = "$TAG-PushSingle"
        val deterministicId = generateDeterministicId(transactionInfoToPush.raw)

        Log.d(pushSingleTag, "Attempting to push. Site: $site, Account: ${transactionInfoToPush.account}, Date: ${transactionInfoToPush.date}, Raw Preview: ${transactionInfoToPush.raw.take(50)}..., Deterministic ID: $deterministicId")

        val firebasePath = "$site/sms_by_date/${transactionInfoToPush.date}/$deterministicId"
        Log.d(pushSingleTag, "Firebase Path for this transaction: $firebasePath")

        val databaseReference = FirebaseDatabase.getInstance().getReference(firebasePath)

        /*databaseReference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    Log.d(pushSingleTag, "Transaction ALREADY EXISTS in Firebase. Path: $firebasePath. Skipping push for ID: $deterministicId.")
                } else  {*/
                    Log.d(pushSingleTag, "Just Pushing Transaction DOES NOT EXIST. Pushing to Firebase. Path: $firebasePath for ID: $deterministicId.")
        try {
            pushToFirebase7(transactionInfoToPush)
            databaseReference.setValue("XXYZIKIK")
                .addOnSuccessListener {
                    Log.d(pushSingleTag, "XXYFirebase push SUCCESSFUL for ID: $deterministicId. Path: $firebasePath")
                }
                .addOnFailureListener { e ->
                    Log.e(pushSingleTag, "XXYFirebase push FAILED for ID: $deterministicId. Path: $firebasePath", e)
                }
        } catch (e: Exception) {
            Log.e(pushSingleTag, "XXYException thrown", e)
        }
                /*}
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(pushSingleTag, "Firebase read CANCELLED for ID: $deterministicId (while checking existence). Path: $firebasePath. Error: ${error.message}", error.toException())
            }
        })*/
        Log.d(pushSingleTag, "Listener attached for existence check for ID: $deterministicId. Path: $firebasePath.")
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

