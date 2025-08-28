package org.fossify.messages.activities

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.fossify.messages.BuildConfig
import org.fossify.messages.R
import org.fossify.messages.databinding.ActivityGpayTransactionsInFbBinding
import org.fossify.messages.models.GPayTransactionInfo // Added import
import org.fossify.messages.ui.GPayTransactionsAdapterFB
import org.fossify.messages.ui.TransactionActivity
import org.fossify.messages.ui.TransactionsInFBActivity
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale

private const val REQUEST_CODE_READ_STORAGE = 1001

// GPayTransactionInfo data class moved to org.fossify.messages.models.GPayTransactionInfo

class GPayTransactionsInFBActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGpayTransactionsInFbBinding
    private lateinit var database: DatabaseReference // Main DB reference, initialized in onCreate
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
        if (result.resultCode == RESULT_OK) {
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
                    Toast.makeText(this, "CSV file not found.", Toast.LENGTH_LONG).show()
                } catch (e: IOException) {
                    Log.e(TAG, "Error reading CSV file for URI: $uri", e)
                    Toast.makeText(this, "Error reading CSV file.", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Log.e(TAG, "An unexpected error occurred during CSV processing for URI: $uri", e)
                    Toast.makeText(this, "An unexpected error occurred.", Toast.LENGTH_LONG).show()
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

        // Initialize database reference here
        database = FirebaseDatabase.getInstance().reference // Base reference

        if (USE_FIREBASE_DATA) {
            // loadTransactionsFromFB will use database.child("gpay") or similar
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
            R.id.action_user_transactions -> { // Added case for user transactions
                startActivity(Intent(this, UserGPayTransactionsActivity::class.java))
                true
            }
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

            R.id.action_show_fb_transactions -> {
                startActivity(Intent(this, TransactionsInFBActivity::class.java))
                finish()
                true
            }


            // R.id.action_user_transactions -> { // Duplicate case for user transactions removed
            //     startActivity(Intent(this, UserGPayTransactionsActivity::class.java))
            //     true
            // }
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
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*" // MIME type for CSV files
            }
            openCsvLauncher.launch(intent)
        } else {
            Log.e(TAG, "Permission denied. Cannot access external storage.")
            Toast.makeText(this, "Permission denied to read storage.", Toast.LENGTH_LONG).show()

        }
    }

    fun launchCsvPickerWithPermissionCheck(intent: Intent) {
        Log.e(TAG, "Permission check 1.")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Permission check request permission.")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_CODE_READ_STORAGE
            )
        } else {
            Log.e(TAG, "Permission check ok launch file picker.")
            openCsvLauncher.launch(intent)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun openCsvFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*" // MIME type for CSV files
        }
//        launchCsvPickerWithPermissionCheck(intent)
        openCsvLauncher.launch(intent)
    }

    private fun setupRecyclerView() {
        transactionsRecyclerView = binding.recyclerViewTransactionsGpayFb
        transactionsRecyclerView.layoutManager = LinearLayoutManager(this)
        adapter = GPayTransactionsAdapterFB(emptyList())
        transactionsRecyclerView.adapter = adapter
    }

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
//            type = transactionNode.child("Type").getValue(String::class.java) ?: "UPI",
            creationTime = transactionNode.child("creationTime").getValue(String::class.java) ?: "N/A",
            amount = transactionNode.child("amount").getValue(String::class.java) ?: "0",
            paymentFee = transactionNode.child("paymentFee").getValue(String::class.java) ?: "0",
            netAmount = transactionNode.child("netAmount").getValue(String::class.java) ?: "0",
            status = transactionNode.child("status").getValue(String::class.java) ?: "N/A",
            updateTime = transactionNode.child("updateTime").getValue(String::class.java) ?: "N/A",
            notes = transactionNode.child("notes").getValue(String::class.java) ?: "",
            creationTimestamp = try {
                val rawDateStr = transactionNode.child("creationTime").getValue(String::class.java) ?: ""
                val parsedDate = inputFormatter.parse(rawDateStr)
                parsedDate?.let { outputFormatter.format(it).toInt() } ?: 0
            } catch (e: Exception) {
                0
            }
        )
    }

    private fun loadTransactionsFromFB() {
        binding.progressBarGpayFb.visibility = View.VISIBLE
        // Assuming database is FirebaseDatabase.getInstance().reference.child("gpay") or similar
        // For structured data like /gpay/date/id, you might need a more complex query or nested listeners.
        // This is a simplified listener for /gpay path where IDs are direct children.
        database.child("gpay").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                transactionsList.clear()
                for (transactionNode in snapshot.children) {
//                    val dateKey = dateGroupSnapshot.key ?: continue
//                    for (transactionNode in dateGroupSnapshot.children) {
                        val transaction = parseGPayTransactionNode(transactionNode)
                        transaction?.let {
                            transactionsList.add(it)
                        }
//                    }
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
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun getNextNotificationId(): Int {
        val prefs = getSharedPreferences(NOTIFICATION_ID_PREFS, MODE_PRIVATE)
        val lastId = prefs.getInt(LAST_NOTIFICATION_ID_KEY, 0)
        val nextId = lastId + 1
        prefs.edit().putInt(LAST_NOTIFICATION_ID_KEY, nextId).apply()
        return nextId
    }

    private fun parseGPayCsv(inputStream: InputStream): List<GPayTransactionInfo> {
        val transactions = mutableListOf<GPayTransactionInfo>()
        BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
            lines.drop(1) // Skip header row
                .forEachIndexed { index, line ->
                    val parts = line.split(",") // Simple CSV split
                    if (parts.size >= 11) { // Expecting at least 11 columns
                        try {
                            val creationTimeString = parts[3].trim()
                            val parsedDate = csvDateTimeFormatter.parse(creationTimeString)
//                            val intTimestamp = parsedDate?.let { compactFormatter.format(it).toInt() } ?: 0
                            val intTimestamp = parsedDate?.time?.div(1000)?.toInt() ?: 0


                            val transaction = GPayTransactionInfo(
                                name = parts[0].trim(),
                                paymentSource = parts[1].trim(),
//                                type = parts[2].trim(),
                                creationTime = creationTimeString,
                                id = parts[4].trim(), // Transaction ID
                                amount = parts[5].trim(),
                                paymentFee = parts[6].trim(),
                                netAmount = parts[7].trim(),
                                status = parts[8].trim(),
                                updateTime = parts[9].trim(),
                                notes = parts[10].trim(),
                                creationTimestamp = intTimestamp
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

    private fun sanitizeFirebaseKey(key: String): String {
        return key.replace(".", "_")
            .replace("#", "_")
            .replace("$", "_")
            .replace("[", "_")
            .replace("]", "_")
            .replace("/", "_")
    }

    private fun uploadGPayCsvData(transactions: List<GPayTransactionInfo>) {
        if (transactions.isEmpty()) {
            Log.i(TAG, "No transactions to upload from CSV.")
            Toast.makeText(this, "No transactions found in CSV to upload.", Toast.LENGTH_SHORT).show()
            return
        }
        Log.i(TAG, "Processing ${transactions.size} GPay transactions for upload...")
        Toast.makeText(this, "Uploading ${transactions.size} transactions...", Toast.LENGTH_SHORT).show()

        val totalTransactions = transactions.size
        val completedOperations = mutableListOf<Boolean>() // true for success, false for failure

        for (transaction in transactions) {
            if (transaction.id.isBlank()) {
                Log.w(TAG, "Skipping transaction with blank ID: $transaction")
                completedOperations.add(false) // Count as a failure for the summary
                if (completedOperations.size == totalTransactions) {
                    handleUploadCompletion(completedOperations)
                }
                continue
            }

            var userName = transaction.name?.trim() ?: ""
            if (userName.isBlank()) { // If name is blank or only whitespace after trim
                userName = "UnknownUser" // Assign a default user name
            }
            val sanitizedUserName = sanitizeFirebaseKey(userName)

            Log.d(TAG, "Attempting to upload CSV Transaction: ID=${transaction.id}, Name=${transaction.name}, Amount=${transaction.amount}, Timestamp=${transaction.creationTimestamp}")

            val gpayPath = "gpay" // Root path for GPay transactions
            val gpayByUserPath = "gpaybyUser" // Root path for user index

            val gpayRef = database.child(gpayPath).child(transaction.id)
            val gpayByUserRef = database.child(gpayByUserPath).child(sanitizedUserName).child(transaction.id)

            gpayRef.setValue(transaction)
                .addOnSuccessListener {
                    Log.i(TAG, "Successfully uploaded GPay transaction ID ${transaction.id} to Firebase path $gpayPath/${transaction.id}")

                    gpayByUserRef.setValue(transaction.creationTimestamp) // Store 'true' or transaction.id as value
                        .addOnSuccessListener {
                            Log.i(TAG, "Successfully indexed transaction ID ${transaction.id} under user '$sanitizedUserName' in $gpayByUserPath")
                            completedOperations.add(true)
                            if (completedOperations.size == totalTransactions) {
                                handleUploadCompletion(completedOperations)
                            }
                        }
                        .addOnFailureListener { eUserIndex ->
                            Log.e(TAG, "Failed to index transaction ID ${transaction.id} under user '$sanitizedUserName' in $gpayByUserPath", eUserIndex)
                            completedOperations.add(false) // Primary upload succeeded, but indexing failed
                            if (completedOperations.size == totalTransactions) {
                                handleUploadCompletion(completedOperations)
                            }
                        }
                }
                .addOnFailureListener { eGpay ->
                    Log.e(TAG, "Failed to upload GPay transaction ID ${transaction.id} to Firebase path $gpayPath/${transaction.id}", eGpay)
                    completedOperations.add(false)
                    if (completedOperations.size == totalTransactions) {
                        handleUploadCompletion(completedOperations)
                    }
                }
        }
    }

    private fun handleUploadCompletion(results: List<Boolean>) {
        val successCount = results.count { it }
        val failureCount = results.size - successCount
        val message = "Upload complete. Successful operations: $successCount, Failed operations: $failureCount"
        Log.i(TAG, message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        // TODO: Optionally, trigger a refresh of the data displayed from Firebase
    }

    companion object {
        private const val GPAY_TRANSACTION_CHANNEL_ID = "gpay_transaction_channel"
        private const val NOTIFICATION_ID_PREFS = "GPayNotificationPrefs"
        private const val LAST_NOTIFICATION_ID_KEY = "lastGPayNotificationId"
    }
}
