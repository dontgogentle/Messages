package org.fossify.messages.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.provider.Telephony
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.parcelize.Parcelize
import org.fossify.messages.R
import org.fossify.messages.activities.MainActivity
import org.fossify.messages.utils.TransactionProcessor // Import TransactionProcessor

@Parcelize
data class TransactionInfo(
    val account: String = "",
    val transactionType: String = "", // "CREDIT" or "DEBIT"
    val amount: String = "",
    val date: String = "",
    val transactionReference: String = "",
    val raw: String = "",
    val upi: String? = null, // Not all transactions have UPI
    val name: String? = null, // For "Received From" or "Transferred To"
    val accountBalance: String? = null,
    val receivedFrom: String? = null,
    val transferredTo: String? = null,
    var isRawExpanded: Boolean = false // Added for collapsible raw message
) : Parcelable

class TransactionActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TransactionAdapter
    private val transactions = mutableListOf<TransactionInfo>()

    companion object {
        const val NEW_TRANSACTION_ACTION = "org.fossify.messages.NEW_TRANSACTION" // Corrected constant name
        const val TRANSACTION_DATA_KEY = "org.fossify.messages.TRANSACTION_DATA"   // Key for passing data
        private const val SMS_PERMISSION_REQUEST_CODE = 101
    }

    private val newTransactionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == NEW_TRANSACTION_ACTION) {
                val newTransaction = intent.getParcelableExtra<TransactionInfo>(TRANSACTION_DATA_KEY) // Expecting Parcelable
                if (newTransaction != null) {
                    Log.d("TransactionActivity", "Received new transaction via broadcast: $newTransaction")
                    // Check for duplicates before adding to avoid issues if receiver and loadTransactions overlap
                    if (transactions.none { existing -> existing.raw == newTransaction.raw && existing.date == newTransaction.date }) {
                        transactions.add(0, newTransaction) // Add to the top
                        adapter.updateData(transactions.distinctBy { t -> t.raw + t.date })
                        // Consider if newly received broadcast messages should also trigger a batch push or be handled by the next full loadTransactions
                        // For now, let's assume they'll be covered by the next manual refresh or app launch.
                        // If immediate push is desired for broadcast items, TransactionProcessor.pushToFirebase(listOf(newTransaction)) could be called here.
                    }
                } else {
                    Log.w("TransactionActivity", "Received null transaction from broadcast or wrong data type.")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        recyclerView = findViewById(R.id.transactionRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = TransactionAdapter(transactions)
        recyclerView.adapter = adapter

        checkAndRequestSmsPermissions()

        LocalBroadcastManager.getInstance(this).registerReceiver(
            newTransactionReceiver,
            IntentFilter(NEW_TRANSACTION_ACTION) // Use corrected constant name
        )
    }

    private fun checkAndRequestSmsPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_SMS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECEIVE_SMS)
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), SMS_PERMISSION_REQUEST_CODE)
        } else {
            loadTransactions()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(newTransactionReceiver)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_sms, R.id.menu_transaction -> {
                // Already in TransactionActivity, refresh by re-requesting permissions (which loads if granted)
                checkAndRequestSmsPermissions()
                true
            }
            R.id.menu_conversations -> {
                startActivity(Intent(this, MainActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == SMS_PERMISSION_REQUEST_CODE) {
            var allPermissionsGranted = true
            for (grantResult in grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false
                    break
                }
            }

            if (allPermissionsGranted) {
                loadTransactions()
            } else {
                Toast.makeText(this, "Both READ_SMS and RECEIVE_SMS permissions are required to function fully.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadTransactions() {
        val tag = "TransactionActivityLoad"
        Log.d(tag, "Not Starting to load SMS transactions...")
        if (true) return

        Log.d(tag, "Starting to load SMS transactions...")
        transactions.clear()

        val cursor = contentResolver.query(
            Uri.parse("content://sms/inbox"),
            null, // projection
            null, // selection
            null, // selectionArgs
            "date DESC" // sortOrder
        )

        if (cursor == null) {
            Log.w(tag, "SMS query returned null cursor.")
            Toast.makeText(this, "Could not query SMS messages.", Toast.LENGTH_SHORT).show()
            return
        }

        var totalMessages = 0
        var parsedCount = 0
        val localTransactionsBatch = mutableListOf<TransactionInfo>() // Collect here before adding to main list

        cursor.use {
            while (it.moveToNext()) {
                totalMessages++
                val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                if (bodyIndex != -1) {
                    val body = it.getString(bodyIndex)
                    val parsed = TransactionProcessor.parseMessage(body)
                    if (parsed != null) {
                        parsedCount++
                        localTransactionsBatch.add(parsed) // Add to a temporary batch
                        // TransactionProcessor.pushToFirebase(parsed) // Removed individual push
                    } else {
                        // Log.d(tag, "Skipped SMS: No match for body: ${body.take(50)}...")
                    }
                } else {
                    Log.w(tag, "Column Telephony.Sms.BODY not found in cursor.")
                }
            }
        }
        // cursor.close() is handled by cursor.use

        Log.i(tag, "Finished loading SMS from device. Total: $totalMessages, Parsed: $parsedCount")
        
        // Update the main list and adapter
        transactions.addAll(localTransactionsBatch.distinctBy { it.raw + it.date }) // Add all new, distinct messages
        adapter.updateData(transactions.distinctBy { it.raw + it.date }.sortedByDescending { it.date }) // Ensure sorted by date for UI

        // Now, push the collected transactions to Firebase using the batch method
        if (localTransactionsBatch.isNotEmpty()) {
            Log.d(tag, "Calling batch pushToFirebase with ${localTransactionsBatch.size} transactions.")
            TransactionProcessor.pushToFirebase(localTransactionsBatch.toList()) // Use toList() for an immutable copy
        } else {
            Log.d(tag, "No new transactions parsed from SMS to push to Firebase.")
        }
    }
}
