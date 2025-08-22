package org.fossify.messages.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.content.SharedPreferences
import com.google.firebase.database.*
import org.fossify.messages.R
import org.fossify.messages.databinding.ActivityTransactionsInFbBinding
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale // Added for SimpleDateFormat Locale
import org.fossify.messages.BuildConfig // Replace org.fossify.messages with your actual applicationId
import org.fossify.messages.activities.MainActivity

class TransactionsInFBActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTransactionsInFbBinding
    private lateinit var database: DatabaseReference
    private lateinit var transactionsRecyclerView: RecyclerView
    private lateinit var adapter: TransactionsAdapterFB
    private var transactionsList = mutableListOf<TransactionInfo>()
    // Removed: private lateinit var fab: FloatingActionButton

    // Flag to control whether to use Firebase or sample data
    private val USE_FIREBASE_DATA = true //!BuildConfig.DEBUG

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransactionsInFbBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarFb)
        supportActionBar?.title = "Transactions (FB)"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Removed FAB initialization and OnClickListener
        // fab = binding.fabNewTransactionFb
        // fab.setOnClickListener {
        //     // ...
        // }

        setupRecyclerView()

        if (USE_FIREBASE_DATA) {
            database = FirebaseDatabase.getInstance().getReference("J5/sms_by_date")
            loadTransactionsFromFB()
            setupTodayTransactionsListener()
        } else {
            Log.d("TransactionsInFB", "Using sample data for testing.")
            val sampleData = getSampleTransactionData()
            adapter.updateData(sampleData) // Assuming adapter.updateData handles sorting and headers
            binding.progressBarFb.visibility = View.GONE
            if (sampleData.isEmpty()) {
                binding.tvNoTransactionsFb.visibility = View.VISIBLE
                transactionsRecyclerView.visibility = View.GONE
            } else {
                binding.tvNoTransactionsFb.visibility = View.GONE
                transactionsRecyclerView.visibility = View.VISIBLE
            }
        }
        createNotificationChannel()
    }

    private fun setupRecyclerView() {
        transactionsRecyclerView = binding.recyclerViewTransactionsFb
        transactionsRecyclerView.layoutManager = LinearLayoutManager(this)
        // Initialize adapter with an empty list first
        adapter = TransactionsAdapterFB(emptyList()) // Pass emptyList to constructor
        transactionsRecyclerView.adapter = adapter
    }

    private fun getSampleTransactionData(): List<TransactionInfo> {
        val sampleList = mutableListOf<TransactionInfo>()
        val calendar = Calendar.getInstance()
        val sdf = SimpleDateFormat("dd MMM yy, hh:mm a", Locale.getDefault())

        // Today's transactions
        calendar.set(Calendar.HOUR_OF_DAY, 10)
        calendar.set(Calendar.MINUTE, 0)
        sampleList.add(TransactionInfo(
            id = "sample1", name = "Coffee Shop", amount = "₹150.00", date = calendar.timeInMillis,
            transactionType = "debit", raw = "Debit from XXX123 for Coffee Shop Ref 9876",
            strDateInMessage = sdf.format(calendar.time),
            account = "XX1234", transactionReference = "9876", upi = "coffeeshop@upi", accountBalance = "₹4850.00", isRawExpanded = false
        ))
        calendar.set(Calendar.HOUR_OF_DAY, 14)
        sampleList.add(TransactionInfo(
            id = "sample2", name = "Salary Credit", amount = "₹50000.00", date = calendar.timeInMillis,
            transactionType = "credit", raw = "Credit to XXX123 Salary Ref 1234. Avl Bal 50000.00",
            strDateInMessage = sdf.format(calendar.time),
            account = "XX1234", transactionReference = "1234", upi = "", accountBalance = "₹50000.00", isRawExpanded = false
        ))

        // Yesterday's transactions
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        calendar.set(Calendar.HOUR_OF_DAY, 18)
        sampleList.add(TransactionInfo(
            id = "sample3", name = "Grocery Store", amount = "₹2500.00", date = calendar.timeInMillis,
            transactionType = "debit", raw = "Spent at BigBasket Ref 5555",
            strDateInMessage = sdf.format(calendar.time),
            account = "XX5678", transactionReference = "5555", upi = "bigbasket@scanner", accountBalance = "₹2500.00", isRawExpanded = false
        ))

        // Two days ago
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        calendar.set(Calendar.HOUR_OF_DAY, 9)
        sampleList.add(TransactionInfo(
            id = "sample4", name = "Online Subscription", amount = "₹499.00", date = calendar.timeInMillis,
            transactionType = "debit", raw = "Netflix recurring payment",
            strDateInMessage = sdf.format(calendar.time),
            account = "XX1234", transactionReference = "NFX1122", upi = "", accountBalance = "₹2001.00", isRawExpanded = false
        ))
        calendar.set(Calendar.HOUR_OF_DAY, 11)
        sampleList.add(TransactionInfo(
            id = "sample5", name = "Friend Transfer", amount = "₹1000.00", date = calendar.timeInMillis,
            transactionType = "credit", raw = "Received from John Doe",
            strDateInMessage = sdf.format(calendar.time),
            account = "XX1234", transactionReference = "IMPS7788", upi = "john@upi", accountBalance = "₹3001.00", isRawExpanded = false
        ))

        return sampleList
    }

    private val timestampFormatter = SimpleDateFormat("dd-MMM-yy hh:mm:ss a", Locale.ENGLISH)
    private val shortDateFormat = SimpleDateFormat("dd-MMM-yy", Locale.ENGLISH)
    // Formatter for trying to parse 'dd-MMM-yy'
    private fun parseTransactionNode(transactionNode: DataSnapshot): TransactionInfo? {

        val id = transactionNode.key ?: return null // If key is null, we can't form a valid TransactionInfo

        val name = transactionNode.child("name").getValue(String::class.java) ?: "Unknown Name"
        val amount = transactionNode.child("amount").getValue(String::class.java) ?: "₹0.00"
        val transactionType = transactionNode.child("transactionType").getValue(String::class.java) ?: "unknown"
        val raw = transactionNode.child("raw").getValue(String::class.java) ?: ""
        var account = transactionNode.child("account").getValue(String::class.java)
        var transactionReference = transactionNode.child("transactionReference").getValue(String::class.java)
        val upi = transactionNode.child("upi").getValue(String::class.java)
        val accountBalance = transactionNode.child("accountBalance").getValue(String::class.java)
        val isRawExpanded = transactionNode.child("isRawExpanded").getValue(Boolean::class.java) ?: false

        // --- Logic for date and strDateInMessage ---
        var finalStrDateInMessage = ""
        var finalTimestamp = 0L

        // 1. Check for "strDateInMessage" field first
        val strDateFromNode = transactionNode.child("strDateInMessage").getValue(String::class.java)
        if (strDateFromNode != null && strDateFromNode.isNotBlank()) {
            finalStrDateInMessage = strDateFromNode
            // Attempt to parse this string date to get a timestamp for the 'date' field
            // This part is tricky as strDateFromNode could be in various formats.
            // For simplicity, let's assume if strDateInMessage exists, it's the display date.
            // You might need more robust parsing if you need to derive a timestamp from it.
            // For now, if strDateInMessage is present, we might not have a reliable separate timestamp
            // unless 'date' field also exists.
        }

        // 2. Check for "date" field (could be Long timestamp or String date)
        val dateValue = transactionNode.child("date").value // Get value without specific type first
        if (dateValue != null) {
            when (dateValue) {
                is Long -> { // It's a timestamp
                    finalTimestamp = dateValue
                    if (finalStrDateInMessage.isBlank()) { // Only format if strDateInMessage wasn't already set
                        finalStrDateInMessage = timestampFormatter.format(Date(finalTimestamp))
                    }
                }
                is String -> { // It's a date string
                    // Try to parse it as 'dd-MMM-yy'
                    try {
                        val parsedDate = shortDateFormat.parse(dateValue)
                        if (parsedDate != null) {
                            finalTimestamp = parsedDate.time
                            if (finalStrDateInMessage.isBlank()) {
                                // If it's just 'dd-MMM-yy', format it for consistency or keep as is?
                                // For now, let's reformat with a default time part if only date was found.
                                finalStrDateInMessage = timestampFormatter.format(parsedDate)
                            }
                        } else {
                            // Could not parse the string date, keep it as is if strDateInMessage is still blank
                            if (finalStrDateInMessage.isBlank()) {
                                finalStrDateInMessage = dateValue
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("ParseTransaction", "Could not parse date string: $dateValue", e)
                        if (finalStrDateInMessage.isBlank()) {
                            finalStrDateInMessage = dateValue // Use raw string if parsing fails
                        }
                    }
                }
                else -> {
                    Log.w("ParseTransaction", "Unknown type for 'date' field: ${dateValue::class.java.simpleName}")
                }
            }
        }

        // If after all checks, finalTimestamp is still 0L and strDateInMessage is blank,
        // you might want a more robust default or logging.
        if (finalTimestamp == 0L && finalStrDateInMessage.isBlank()) {
            finalStrDateInMessage = "Date N/A" // Default if no date info found
            // finalTimestamp remains 0L or you could set it to System.currentTimeMillis() or null if date is nullable Long?
        }

        account = account ?: "Unknown Acc"
        transactionReference = transactionReference ?: "Unknown Ref"

        return TransactionInfo(
            id = id,
            name = name,
            amount = amount,
            date = finalTimestamp, // This is our primary timestamp
            transactionType = transactionType,
            raw = raw,
            strDateInMessage = finalStrDateInMessage, // This is the string representation
            account = account,
            transactionReference = transactionReference,
            upi = upi,
            accountBalance = accountBalance,
            isRawExpanded = isRawExpanded
        )
    }

    private fun loadTransactionsFromFB() {
        binding.progressBarFb.visibility = View.VISIBLE
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                transactionsList.clear()
                // Iterate through each date group (e.g., "20-Aug-2025")
                for (dateGroupSnapshot in snapshot.children) {
                    // Now, iterate through the actual transaction nodes within this date group
                    for (transactionNode in dateGroupSnapshot.children) {
                        val transaction = parseTransactionNode(transactionNode) // Your existing parsing function
                        transaction?.let {
                            transactionsList.add(it)
                        }
                    }
                }

                transactionsList.sortByDescending { it.date }

                // Assuming adapter.updateData handles sorting and any header generation
                adapter.updateData(transactionsList)
                binding.progressBarFb.visibility = View.GONE
                if (transactionsList.isEmpty()) {
                    binding.tvNoTransactionsFb.visibility = View.VISIBLE
                    transactionsRecyclerView.visibility = View.GONE
                } else {
                    binding.tvNoTransactionsFb.visibility = View.GONE
                    transactionsRecyclerView.visibility = View.VISIBLE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("TransactionsInFB", "Failed to load transactions", error.toException())
                binding.progressBarFb.visibility = View.GONE
                binding.tvNoTransactionsFb.text = "Failed to load data. Please try again."
                binding.tvNoTransactionsFb.visibility = View.VISIBLE
                transactionsRecyclerView.visibility = View.GONE
            }
        })
    }

    private fun setupTodayTransactionsListener() {
        val todayFormatted: String = SimpleDateFormat("dd-MMM-yy", Locale.ENGLISH).format(Date())
        println(todayFormatted)

        database.child(todayFormatted).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("TransactionsInFB", "Today's transactions updated: ${snapshot.childrenCount} items")

                for (transactionNode in snapshot.children) {
                    val transaction = parseTransactionNode(transactionNode)
                    transaction?.let { newTxn ->
                        // Check if this transaction already exists in the list
                        val alreadyExists = transactionsList.any { existingTxn ->
                            existingTxn.id == newTxn.id // Replace 'id' with your unique key field
                        }

                        if (!alreadyExists) {
                            transactionsList.add(newTxn)
                            Log.d("TransactionsInFB", "Added new transaction: ${newTxn.id}")
                            showNewTransactionNotification(newTxn)
                        }
                    }
                }
                transactionsList.sortByDescending { it.date }
                adapter.updateData(transactionsList)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("TransactionsInFB", "Failed to listen for today's transactions", error.toException())
            }
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name_transactions) // Make sure R.string.channel_name_transactions is defined
            val descriptionText = getString(R.string.channel_description_transactions) // Make sure R.string.channel_description_transactions is defined
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(TRANSACTION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNewTransactionNotification(transaction: TransactionInfo) {
        val intent = Intent(this, TransactionsInFBActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, pendingIntentFlags)
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(this, TRANSACTION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_message) // USER NEEDS TO REPLACE THIS with a valid drawable
            .setContentTitle("From: ${transaction.name}")
            .setContentText("Amount: ${transaction.amount}, Type: ${transaction.transactionType}, Date: ${transaction.strDateInMessage}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setSound(defaultSoundUri)
            .setAutoCancel(true)

        try {
            with(NotificationManagerCompat.from(this)) {
                notify(getNextNotificationId(), builder.build())
            }
        } catch (e: SecurityException) {
            Log.e("TransactionsInFB", "Failed to show notification. Ensure POST_NOTIFICATIONS permission.", e)
            // Consider Toast.makeText(this, "Notification permission needed.", Toast.LENGTH_LONG).show()
        }
    }
    private fun getNextNotificationId(): Int {
        val prefs = getSharedPreferences(NOTIFICATION_ID_PREFS, Context.MODE_PRIVATE)
        val lastId = prefs.getInt(LAST_NOTIFICATION_ID_KEY, 0)
        val nextId = lastId + 1
        prefs.edit().putInt(LAST_NOTIFICATION_ID_KEY, nextId).apply()
        return nextId
    }

    private fun getStartOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
//        menuInflater.inflate(R.menu.menu_transactions_fb, menu)
//        return true

        // Check if the current flavor is fbTransactionsOnly
        if (BuildConfig.FLAVOR == "fbTransactionsOnly") {
            menuInflater.inflate(R.menu.menu_transactions_fb, menu)
            // Don't inflate or show any menu for this flavor
            //menu.clear() // Clear any items that might have been added by a superclass
            return true // Return false to indicate no menu should be displayed
        } else {
            // Existing menu inflation logic for other flavors
            menuInflater.inflate(R.menu.menu_main, menu) // Reuse existing menu or create a new one
            return true
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_sms, R.id.menu_transaction -> { // not applicable for fbOnly flavor
                // For simplicity, let's assume these take you to TransactionActivity
                // which can then decide to load SMS or local transactions.
                // Or, if you have a specific activity for SMS list, use that for menu_sms.
                startActivity(Intent(this, TransactionActivity::class.java))
                finish() // Optional: finish this activity
                true
            }
            R.id.menu_conversations -> { // not applicable for fbOnly flavor
                startActivity(Intent(this, MainActivity::class.java))
                finish() // Optional: finish this activity
                true
            }
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed() // Use this for proper back navigation
                true
            }
            R.id.action_add_dummy_fb -> {
                //TODO add a menu user could use
                // I am thinking could provide site names to display site specific transactions
                /*if (USE_FIREBASE_DATA) {
                    addDummyTransactionToFB()
                } */
                true
            }
            R.id.action_clear_all_fb -> {
                if (USE_FIREBASE_DATA) {
                    clearAllTransactionsFromFB()
                } else {
                    adapter.updateData(emptyList()) // Clear sample data
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun addDummyTransactionToFB() {
        val key = database.push().key ?: return
        val calendar = Calendar.getInstance() // For date consistency if needed
        val sdf = SimpleDateFormat("dd MMM yy, hh:mm a", Locale.getDefault())
        val dummyTransaction = TransactionInfo(
            id = key,
            name = "Dummy Transaction ${System.currentTimeMillis() % 100}",
            amount = "₹${(100..1000).random()}.00",
            transactionType = if (Math.random() < 0.5) "credit" else "debit",
            date = System.currentTimeMillis(),
            strDateInMessage = sdf.format(Date()), // Use current date for strDateInMessage
            account = "FBTestACC",
            transactionReference = "FBRef${(1000..9999).random()}",
            upi = "dummy@fb",
            accountBalance = "₹${(5000..10000).random()}.00",
            raw = "This is a dummy transaction added via app for Firebase testing.",
            isRawExpanded = false
        )
        database.child(key).setValue(dummyTransaction)
            .addOnSuccessListener {
                Log.d("TransactionsInFB", "Dummy transaction added to Firebase.")
            }
            .addOnFailureListener {
                Log.e("TransactionsInFB", "Failed to add dummy transaction to Firebase.", it)
            }
    }


    private fun clearAllTransactionsFromFB() {
        database.removeValue()
            .addOnSuccessListener {
                Log.d("TransactionsInFB", "All transactions cleared from Firebase.")
                transactionsList.clear() // Clear local list
                adapter.updateData(transactionsList) // Update adapter with empty list
            }
            .addOnFailureListener {
                Log.e("TransactionsInFB", "Failed to clear transactions from Firebase.", it)
            }
    }

    companion object {
        private const val TRANSACTION_CHANNEL_ID = "new_transaction_channel"
        private const val NOTIFICATION_ID_PREFS = "NotificationPrefs"
        private const val LAST_NOTIFICATION_ID_KEY = "lastNotificationId"
    }
}
