package org.fossify.messages.ui

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import org.fossify.messages.R
import org.fossify.messages.databinding.ActivityGpayTransactionsInFbBinding
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.fossify.messages.BuildConfig
import org.fossify.messages.activities.MainActivity
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

private const val REQUEST_CODE_READ_STORAGE = 1001

// Define a data class for GPay Transactions based on CSV fields
data class GPayTransactionInfo(
    val id: String = "", // Transaction ID
    val name: String = "", // Name
    val paymentSource: String = "", // Payment Source
    val type: String = "UPI", // Type (defaulted to UPI as per CSV)
    val creationTime: String = "", // creation time string from CSV (e.g., "21-07-2025 17:35:44")
    val amount: String = "", // Amount
    val paymentFee: String = "", // Payment Fee
    val netAmount: String = "", // Net Amount
    val status: String = "", // Status
    val updateTime: String = "", // Update time
    val notes: String = "", // Notes
    val creationTimestamp: Int = 0 // Parsed timestamp from creationTime string
)


class GPayTransactionsInFBActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGpayTransactionsInFbBinding
    private lateinit var database: DatabaseReference
    private lateinit var transactionsRecyclerView: RecyclerView
    private lateinit var adapter: GPayTransactionsAdapterFB
    private var transactionsList = mutableListOf<GPayTransactionInfo>()

    private val USE_FIREBASE_DATA = true
    private val TAG = "GPayTransactionsInFB"

    // Date formatter for "dd-MM-yyyy HH:mm:ss" from CSV
    val csvDateTimeFormatter = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.ENGLISH)
    val compactFormatter = SimpleDateFormat("yyMMddHHmm", Locale.ENGLISH)

    @RequiresApi(Build.VERSION_CODES.O)
    private val openCsvLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.also { uri ->
                Log.d(TAG, "CSV file selected: $uri")
                try {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val parsedTransactions = parseGPayCsv(inputStream)
                        Log.i(TAG, "Successfully parsed ${parsedTransactions.size} transactions from CSV.")
                        uploadGPayCsvData(parsedTransactions)
                    } ?: Log.e(TAG, "Could not open input stream for URI: $uri")
                } catch (e: FileNotFoundException) {
                    Log.e(TAG, "CSV file not found for URI: $uri", e)
                    // TODO: Show user feedback
                } catch (e: IOException) {
                    Log.e(TAG, "Error reading CSV file for URI: $uri", e)
                    // TODO: Show user feedback
                } catch (e: Exception) {
                    Log.e(TAG, "An unexpected error occurred during CSV processing for URI: $uri", e)
                    // TODO: Show user feedback
                }
            }
        } else {
            Log.d(TAG, "CSV file selection cancelled or failed. Result code: ${result.resultCode}")
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGpayTransactionsInFbBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarGpayFb)
        supportActionBar?.title = "GPay Transactions (FB)"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupRecyclerView()

        if (USE_FIREBASE_DATA) {
            database = FirebaseDatabase.getInstance().getReference("gpay")
            loadTransactionsFromFB()
        } else {
            Log.d(TAG, "Using sample data for testing - not implemented for GPay yet.")
            binding.progressBarGpayFb.visibility = View.GONE
            binding.tvNoTransactionsGpayFb.visibility = View.VISIBLE
            transactionsRecyclerView.visibility = View.GONE
        }
        createNotificationChannel()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (BuildConfig.FLAVOR == "fbTransactionsOnly") {
            menuInflater.inflate(R.menu.menu_transactions_fb, menu)
            return true
        } else {
            menuInflater.inflate(R.menu.menu_main, menu)
            return true
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_sms, R.id.menu_transaction -> {
                startActivity(Intent(this, TransactionActivity::class.java))
                finish()
                true
            }
            R.id.menu_conversations -> {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
                true
            }
            R.id.action_open_gpay_transactions_fb -> {
                startActivity(Intent(this, GPayTransactionsInFBActivity::class.java))
                finish()
                true
            }
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_upload_gpay_csv -> {
                openCsvFilePicker()
                 true
             }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_READ_STORAGE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
//            launchCsvPicker()
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*" // MIME type for CSV files
            }
            openCsvLauncher.launch(intent)
        } else {
            Log.e(TAG, "Permission denied. Cannot access external storage.")
            // TODO: Show user feedback
        }
    }

    fun launchCsvPickerWithPermissionCheck(intent: Intent) {

//        val granted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
//        Log.d(TAG, "Permission status: $granted")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_CODE_READ_STORAGE
            )
        } else {
//                launchCsvPicker()
            openCsvLauncher.launch(intent)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun openCsvFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*" // MIME type for CSV files
        }
        launchCsvPickerWithPermissionCheck(intent)
//        openCsvLauncher.launch(intent)
    }

    private fun setupRecyclerView() {
        transactionsRecyclerView = binding.recyclerViewTransactionsGpayFb
        transactionsRecyclerView.layoutManager = LinearLayoutManager(this)
        adapter = GPayTransactionsAdapterFB(emptyList())
        transactionsRecyclerView.adapter = adapter
    }

    private val firebaseDateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    private val displayDateTimeFormatter = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.ENGLISH)


    private fun parseGPayTransactionNode(transactionNode: DataSnapshot): GPayTransactionInfo? {
        val id = transactionNode.key ?: return null
        val inputFormatter = SimpleDateFormat("dd-MM-yyyy  HH:mm", Locale.ENGLISH)
        val outputFormatter = SimpleDateFormat("yyMMddHHmm", Locale.ENGLISH)

        val creationTimestamp: Int = try {
            val rawDateStr = transactionNode.child("creationTime").getValue(String::class.java) ?: ""
            val parsedDate = inputFormatter.parse(rawDateStr)
            parsedDate?.let { outputFormatter.format(it).toInt() } ?: 0
        } catch (e: Exception) {
            0
        }
        return GPayTransactionInfo(
            id = id,
            name = transactionNode.child("name").getValue(String::class.java) ?: "N/A",
            paymentSource = transactionNode.child("paymentSource").getValue(String::class.java) ?: "N/A",
            type = transactionNode.child("Type").getValue(String::class.java) ?: "UPI",
            creationTime = transactionNode.child("creationTime").getValue(String::class.java) ?: "N/A",
            amount = transactionNode.child("amount").getValue(String::class.java) ?: "0",
            paymentFee = transactionNode.child("paymentFee").getValue(String::class.java) ?: "0",
            netAmount = transactionNode.child("netAmount").getValue(String::class.java) ?: "0",
            status = transactionNode.child("status").getValue(String::class.java) ?: "N/A",
            updateTime = transactionNode.child("updateTime").getValue(String::class.java) ?: "N/A",
            notes = transactionNode.child("notes").getValue(String::class.java) ?: "",
            // creationTimestamp would be populated from 'creationTime' if this node was from a CSV
            // For now, if it's directly from FB, this might remain 0 or you could try to parse 'creationTime' here too.

            creationTimestamp = try {
                val rawDateStr = transactionNode.child("creationTime").getValue(String::class.java) ?: ""
                val parsedDate = inputFormatter.parse(rawDateStr)
                parsedDate?.let { outputFormatter.format(it).toInt() } ?: 0
            } catch (e: Exception) {
                0
            }
            //creationTimestamp = try { displayDateTimeFormatter.parse(transactionNode.child("creationTime").getValue(String::class.java) ?: "")?.time ?: 0L } catch (e: Exception) { 0L }
        )
    }

    private fun loadTransactionsFromFB() {
        binding.progressBarGpayFb.visibility = View.VISIBLE
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                transactionsList.clear()
                for (dateGroupSnapshot in snapshot.children) {
                    val dateKey = dateGroupSnapshot.key ?: continue
                    for (transactionNode in dateGroupSnapshot.children) {
                        val transaction = parseGPayTransactionNode(transactionNode)
                        transaction?.let {
                            transactionsList.add(it)
                        }
                    }
                }
                 transactionsList.sortWith(compareByDescending<GPayTransactionInfo> { it.creationTime }/*.thenByDescending { it.creationTime }*/)
                adapter.updateData(transactionsList)
                binding.progressBarGpayFb.visibility = View.GONE
                if (transactionsList.isEmpty()) {
                    binding.tvNoTransactionsGpayFb.visibility = View.VISIBLE
                    transactionsRecyclerView.visibility = View.GONE
                } else {
                    binding.tvNoTransactionsGpayFb.visibility = View.GONE
                    transactionsRecyclerView.visibility = View.VISIBLE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to load GPay transactions", error.toException())
                binding.progressBarGpayFb.visibility = View.GONE
                binding.tvNoTransactionsGpayFb.text = "Failed to load GPay data. Please try again."
                binding.tvNoTransactionsGpayFb.visibility = View.VISIBLE
                transactionsRecyclerView.visibility = View.GONE
            }
        })
    }


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "GPay Transactions"
            val descriptionText = "Notifications for new GPay transactions synced from CSV"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(GPAY_TRANSACTION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun getNextNotificationId(): Int {
        val prefs = getSharedPreferences(NOTIFICATION_ID_PREFS, Context.MODE_PRIVATE)
        val lastId = prefs.getInt(LAST_NOTIFICATION_ID_KEY, 0)
        val nextId = lastId + 1
        prefs.edit().putInt(LAST_NOTIFICATION_ID_KEY, nextId).apply()
        return nextId
    }

    /**
     * Parses the GPay Business CSV file content.
     * Expected CSV columns: name, payment source, Type, creation time, transaction ID, amount, payment fee, net amount, status, Update time, Notes
     */
    private fun parseGPayCsv(inputStream: InputStream): List<GPayTransactionInfo> {
        val transactions = mutableListOf<GPayTransactionInfo>()
        BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
            lines.drop(1) // Skip header row
                .forEachIndexed { index, line ->
                    val parts = line.split(",") // Simple CSV split
                    if (parts.size >= 11) { // Expecting at least 11 columns
                        try {
                            val creationTimeString = parts[3].trim()
//                            val creationTimestamp = csvDateTimeFormatter.parse(creationTimeString)?.time ?: 0
                            val parsedDate = csvDateTimeFormatter.parse(creationTimeString)
                            val intTimestamp = parsedDate?.let { compactFormatter.format(it).toInt() } ?: 0

                            val transaction = GPayTransactionInfo(
                                name = parts[0].trim(),
                                paymentSource = parts[1].trim(),
                                type = parts[2].trim(), // Should be "UPI" generally
                                creationTime = creationTimeString,
                                id = parts[4].trim(), // Transaction ID
                                amount = parts[5].trim(),
                                paymentFee = parts[6].trim(),
                                netAmount = parts[7].trim(),
                                status = parts[8].trim(),
                                updateTime = parts[9].trim(),
                                notes = parts[10].trim(),
                                creationTimestamp = intTimestamp
                                // firebaseDateKey will be set before Firebase push if needed
                            )
                            transactions.add(transaction)
                            Log.d(TAG, "Parsed CSV line ${index + 2}: $transaction")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing CSV line ${index + 2}: '$line'", e)
                        }
                    } else {
                        Log.w(TAG, "Skipping malformed CSV line ${index + 2} (columns: ${parts.size}): '$line'")
                    }
                }
        }
        return transactions
    }

    /**
     * Handles the list of GPayTransactionInfo objects parsed from the CSV.
     * TODO: Implement Firebase upload logic here.
     */
    private fun uploadGPayCsvData(transactions: List<GPayTransactionInfo>) {
        if (transactions.isEmpty()) {
            Log.i(TAG, "No transactions to upload from CSV.")
            Toast.makeText(this, "No transactions found in CSV to upload.", Toast.LENGTH_SHORT).show()
            return
        }
        Log.i(TAG, "Processing ${transactions.size} GPay transactions for upload to /gpay/{id} path...")
        Toast.makeText(this, "Uploading ${transactions.size} transactions...", Toast.LENGTH_SHORT).show()

        var successCount = 0
        var failureCount = 0

        for (transaction in transactions) {
            if (transaction.id.isBlank()) {
                Log.w(TAG, "Skipping transaction with blank ID: $transaction")
                failureCount++
                continue
            }
            Log.d(TAG, "Attempting to upload CSV Transaction: ID=${transaction.id}, Name=${transaction.name}, Amount=${transaction.amount}, Timestamp=${transaction.creationTimestamp}")

            val transactionRef = database.child("gpay").child(transaction.id) // Path: /gpay/{transaction.id}

            transactionRef.setValue(transaction)
                .addOnSuccessListener {
                    successCount++
                    Log.i(TAG, "Successfully uploaded GPay transaction ID ${transaction.id} to Firebase path /gpay/${transaction.id}")
                    if (successCount + failureCount == transactions.size) {
                        handleUploadCompletion(successCount, failureCount)
                    }
                }
                .addOnFailureListener { e ->
                    failureCount++
                    Log.e(TAG, "Failed to upload GPay transaction ID ${transaction.id} to Firebase path /gpay/${transaction.id}", e)
                    if (successCount + failureCount == transactions.size) {
                        handleUploadCompletion(successCount, failureCount)
                    }
                }
        }
    }

    private fun handleUploadCompletion(successCount: Int, failureCount: Int) {
        val message = "Upload complete. Successful: $successCount, Failed: $failureCount"
        Log.i(TAG, message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        // TODO: Optionally, trigger a refresh of the data displayed from Firebase if this activity also shows it
        // For example, if you have a loadTransactionsFromFB() method, you might call it here.
    }

    companion object {
        private const val GPAY_TRANSACTION_CHANNEL_ID = "gpay_transaction_channel"
        private const val NOTIFICATION_ID_PREFS = "GPayNotificationPrefs"
        private const val LAST_NOTIFICATION_ID_KEY = "lastGPayNotificationId"
    }
}
