package org.fossify.messages.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
// import android.net.Uri // Commented out as loadTransactions is disabled
// import android.os.Bundle // Already imported by AppCompatActivity
// import android.os.Parcelable // Moved to TransactionInfo.kt
// import android.provider.Telephony // Commented out as loadTransactions is disabled
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
// import kotlinx.parcelize.Parcelize // Moved to TransactionInfo.kt
import org.fossify.messages.R
import org.fossify.messages.activities.MainActivity
import org.fossify.messages.models.TransactionInfo // Added import
// import org.fossify.messages.utils.TransactionProcessor // Import TransactionProcessor - already imported

// TransactionInfo data class moved to org.fossify.messages.models.TransactionInfo

class TransactionActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TransactionAdapter
    private val transactions = mutableListOf<TransactionInfo>()

    companion object {
        const val NEW_TRANSACTION_ACTION = "org.fossify.messages.NEW_TRANSACTION"
        const val TRANSACTION_DATA_KEY = "org.fossify.messages.TRANSACTION_DATA"
        private const val SMS_PERMISSION_REQUEST_CODE = 101
    }

    private val newTransactionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == NEW_TRANSACTION_ACTION) {
                val newTransaction = intent.getParcelableExtra<TransactionInfo>(TRANSACTION_DATA_KEY)
                if (newTransaction != null) {
                    Log.d("TransactionActivity", "Received new transaction via broadcast: $newTransaction")
                    if (transactions.none { existing -> existing.raw == newTransaction.raw && existing.strDateInMessage == newTransaction.strDateInMessage }) {
                        transactions.add(0, newTransaction)
                        adapter.updateData(transactions.distinctBy { t -> t.raw + t.strDateInMessage })
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
            IntentFilter(NEW_TRANSACTION_ACTION)
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
        return
//        val tag = "TransactionActivityLoad"
//        Log.d(tag, "Not Starting to load SMS transactions...")
//        if (true) return // This line seems to intentionally block loading, keeping it as is.
//
//        Log.d(tag, "Starting to load SMS transactions...")
//        transactions.clear()
//
//        val cursor = contentResolver.query(
//            Uri.parse("content://sms/inbox"),
//            null,
//            null,
//            null,
//            "date DESC"
//        )
//
//        if (cursor == null) {
//            Log.w(tag, "SMS query returned null cursor.")
//            Toast.makeText(this, "Could not query SMS messages.", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        var totalMessages = 0
//        var parsedCount = 0
//        val localTransactionsBatch = mutableListOf<TransactionInfo>()
//
//        cursor.use {
//            while (it.moveToNext()) {
//                totalMessages++
//                val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
//                if (bodyIndex != -1) {
//                    val body = it.getString(bodyIndex)
//                    val parsed = TransactionProcessor.parseMessage(body)
//                    if (parsed != null) {
//                        parsedCount++
//                        localTransactionsBatch.add(parsed)
//                    } else {
//                        // Log.d(tag, "Skipped SMS: No match for body: ${body.take(50)}...")
//                    }
//                } else {
//                    Log.w(tag, "Column Telephony.Sms.BODY not found in cursor.")
//                }
//            }
//        }
//
//        Log.i(tag, "Finished loading SMS from device. Total: $totalMessages, Parsed: $parsedCount")
//
//        // Update the main list and adapter
//        // Ensure to use strDateInMessage for distinctness if that was the old logic
//        transactions.addAll(localTransactionsBatch.distinctBy { it.raw + it.strDateInMessage })
//        // Sort by the new 'date' (timestamp) field for UI consistency
//        adapter.updateData(transactions.distinctBy { it.raw + it.strDateInMessage }.sortedByDescending { it.date })
//
//        if (localTransactionsBatch.isNotEmpty()) {
//            Log.d(tag, "Calling batch pushToFirebase with ${localTransactionsBatch.size} transactions.")
//            TransactionProcessor.pushToFirebase(localTransactionsBatch.toList())
//        } else {
//            Log.d(tag, "No new transactions parsed from SMS to push to Firebase.")
//        }
    }
}
