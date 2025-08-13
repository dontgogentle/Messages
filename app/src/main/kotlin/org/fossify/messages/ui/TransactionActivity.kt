package org.fossify.messages.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Telephony
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.FirebaseDatabase
import org.fossify.messages.R
import java.util.UUID

data class TransactionInfo(
    val account: String,
    val transactionType: String, // "CREDIT" or "DEBIT"
    val amount: String,
    val date: String,
    val transactionReference: String,
    val raw: String,
    val upi: String? = null, // Not all transactions have UPI
    val name: String? = null, // For "Received From" or "Transferred To"
    val accountBalance: String? = null,
    val receivedFrom: String? = null,
    val transferredTo: String? = null
)

class TransactionActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TransactionAdapter
    private val transactions = mutableListOf<TransactionInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction)

        recyclerView = findViewById(R.id.transactionRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = TransactionAdapter(transactions)
        recyclerView.adapter = adapter

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_SMS), 101)
        } else {
            loadTransactions()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadTransactions()
        } else {
            Toast.makeText(this, "SMS permission required", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadTransactions() {
        val TAG = "TransactionActivity"

        Log.d(TAG, "Starting to load SMS transactions...")

        val cursor = contentResolver.query(
            Uri.parse("content://sms/inbox"),
            null,
            "address LIKE ?", // Consider making this more flexible or configurable if needed
            arrayOf("%-ICICIT-%"), // This limits to ICICI, adjust if other banks are needed
            "date DESC"
        )

        if (cursor == null) {
            Log.w(TAG, "SMS query returned null cursor.")
            return
        }

        var totalMessages = 0
        var parsedCount = 0

        cursor.use {
            while (it.moveToNext()) {
                totalMessages++
                val body = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY))
                Log.d(TAG, "SMS[$totalMessages]: $body")

                val parsed = parseMessage(body)
                if (parsed != null) {
                    parsedCount++
                    Log.d(TAG, "Parsed[$parsedCount]: $parsed")
                    transactions.add(parsed)
                    pushToFirebase(parsed)
                } else {
                    Log.d(TAG, "Skipped SMS[$totalMessages]: No match for transaction pattern for body: $body")
                }
            }
        }

        Log.i(TAG, "Finished loading SMS. Total: $totalMessages, Parsed: $parsedCount")
        adapter.notifyDataSetChanged()
    }

    // Helper function to clean amount strings
    private fun cleanAmount(amountStr: String): String {
        // Removes "Rs.", "Rs ", commas, and ".00" if it's the decimal part.
        return amountStr.replace(Regex("Rs\\.?\\s*|,"), "").replace(Regex("\\.00$"), "").trim()
    }

    private fun parseMessage(body: String): TransactionInfo? {
        val TAG = "TransactionActivityParse" // For specific logging from this function

        // Regex 1: ICICI Bank Account XX665 credited:Rs. 12,958.00 on 12-Aug-25. Info NEFT-... Available Balance...
        // Example: "ICICI Bank Account XX665 credited:Rs. 12,958.00 on 12-Aug-25. Info NEFT-UTIBN62025081224608046-. Available Balance is Rs. 3,10,350.94."
        val regex1 = Regex("""^ICICI Bank Account\s+(\w+)\s+credited:Rs\.\s*([\d,]+\.?\d{0,2})\s+on\s+(\d{2}-\w{3}-\d{2})\.\s*Info\s*([^.]+?)\.?\s*Available Balance is Rs\.\s*([\d,]+\.?\d{0,2})""", RegexOption.IGNORE_CASE)
        regex1.find(body)?.let { match ->
            Log.d(TAG, "Matched Regex 1")
            val account = match.groupValues[1]
            val amount = cleanAmount(match.groupValues[2])
            val date = match.groupValues[3]
            val rawTransactionRef = match.groupValues[4].trim()
            val transactionReference = if (rawTransactionRef.endsWith("-")) rawTransactionRef.dropLast(1) else rawTransactionRef
            val accountBalance = cleanAmount(match.groupValues[5])
            return TransactionInfo(
                account = account,
                transactionType = "CREDIT",
                amount = amount,
                date = date,
                transactionReference = transactionReference,
                accountBalance = accountBalance,
                raw = body
            )
        }

        // Regex 2: Dear Customer, Acct XX665 is credited with Rs 267.00 on 11-Aug-25 from MOHANA PRIYA S. UPI:109645749329-ICICI Bank.
        val regex2 = Regex("""^Dear Customer, Acct\s+(\w+)\s+is credited with Rs\s*([\d,]+\.?\d{0,2})\s+on\s+(\d{2}-\w{3}-\d{2})\s+from\s+(.+?)\.\s*UPI:(\S+)""", RegexOption.IGNORE_CASE)
        regex2.find(body)?.let { match ->
            Log.d(TAG, "Matched Regex 2")
            val account = match.groupValues[1]
            val amount = cleanAmount(match.groupValues[2])
            val date = match.groupValues[3]
            val receivedFrom = match.groupValues[4].trim()
            val upiDetails = match.groupValues[5]
            val upiRef = upiDetails.split('-').first()

            return TransactionInfo(
                account = account,
                transactionType = "CREDIT",
                amount = amount,
                date = date,
                transactionReference = "UPI:$upiRef",
                upi = upiRef,
                receivedFrom = receivedFrom,
                name = receivedFrom,
                raw = body
            )
        }

        // Regex 3: ICICI Bank Acct XX665 debited for Rs 1380.00 on 12-Aug-25; KALIAMMAL R credited. UPI:522477380420. ...
        val regex3 = Regex("""^ICICI Bank Acct\s+(\w+)\s+debited for Rs\s*([\d,]+\.?\d{0,2})\s+on\s+(\d{2}-\w{3}-\d{2});\s*(.+?)\s+credited\.\s*UPI:(\S+)""", RegexOption.IGNORE_CASE)
        regex3.find(body)?.let { match ->
            Log.d(TAG, "Matched Regex 3")
            val account = match.groupValues[1]
            val amount = cleanAmount(match.groupValues[2])
            val date = match.groupValues[3]
            val transferredTo = match.groupValues[4].trim()
            val upiDetails = match.groupValues[5]
            val upiRef = upiDetails.split('-').first()

            return TransactionInfo(
                account = account,
                transactionType = "DEBIT",
                amount = amount,
                date = date,
                transactionReference = "UPI:$upiRef",
                upi = upiRef,
                transferredTo = transferredTo,
                name = transferredTo,
                raw = body
            )
        }
        
        // Regex 4 (based on original code's regex logic but improved): Acct X is credited with Rs.Y on Z from W UPI:U
        // Example: "Acct XX665 is credited with Rs.100.00 on 10-Jul-25 from SOMEONE UPI:123456789"
        // This is more general and might catch other credited messages.
        val regex4 = Regex("""Acct\s+(\w+)\s+is credited with Rs\.?\s*([\d,]+\.?\d{0,2})\s+on\s+(\d{2}-\w{3}-\d{2})\s+from\s+(.+?)\s+UPI:(\S+)""", RegexOption.IGNORE_CASE)
        regex4.find(body)?.let { match ->
            Log.d(TAG, "Matched Regex 4 (general credit with UPI)")
            val account = match.groupValues[1]
            val amount = cleanAmount(match.groupValues[2])
            val date = match.groupValues[3]
            val receivedFrom = match.groupValues[4].trim()
            val upiDetails = match.groupValues[5]
            val upiRef = upiDetails.split('-').first()

            return TransactionInfo(
                account = account,
                transactionType = "CREDIT",
                amount = amount,
                date = date,
                transactionReference = "UPI:$upiRef", 
                upi = upiRef,
                name = receivedFrom,
                receivedFrom = receivedFrom,
                raw = body
            )
        }

        Log.d(TAG, "No regex matched for body.")
        return null // No regex matched
    }

    private fun pushToFirebase(info: TransactionInfo) {
        val site = getSiteForAccount(info.account)
        val key = UUID.randomUUID().toString()
        val ref = FirebaseDatabase.getInstance()
            .getReference("$site/sms/${info.date}/$key")

        ref.setValue(info)
    }

    private fun getSiteForAccount(account: String): String {
        return when (account.lowercase().replace("xx","")) { // Made comparison case-insensitive and flexible for "xx"
            "665" -> "J5"
            // Add other account mappings here
            else -> "UNKNOWN"
        }
    }
}
