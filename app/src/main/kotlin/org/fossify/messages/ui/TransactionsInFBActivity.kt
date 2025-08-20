package org.fossify.messages.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import org.fossify.messages.BuildConfig
import org.fossify.messages.R
import org.fossify.messages.activities.MainActivity
import java.text.SimpleDateFormat
import java.util.*

// Assuming TransactionInfo is correctly defined in TransactionActivity.kt as per Step 1:
// data class TransactionInfo(
//     var id: String? = null,
//     var strDateInMessage: String? = "", // For "dd-MMM-yy" string from SMS
//     var date: Long? = null,             // For epoch timestamp
//     val account: String = "",
//     val transactionType: String = "",
//     val amount: String = "",
//     val transactionReference: String = "",
//     val raw: String = "",
//     val upi: String? = null,
//     val name: String? = null,
//     val accountBalance: String? = null,
//     val receivedFrom: String? = null,
//     val transferredTo: String? = null,
//     var isRawExpanded: Boolean = false
// )

class TransactionsInFBActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TransactionsAdapterFB
    private var transactions = mutableListOf<TransactionInfo>()
    private lateinit var database: DatabaseReference
    private val todayTransactionsListenerMap = mutableMapOf<String, TransactionInfo>()
    private var todayValueEventListener: ValueEventListener? = null
    private lateinit var todayRef: Query

    companion object {
        private const val TAG = "TransactionsInFB"
        private val FIREBASE_DATE_FORMAT = SimpleDateFormat("dd-MMM-yy", Locale.ENGLISH)
        private const val NOTIFICATION_CHANNEL_ID = "transactions_channel"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transactions_in_fb) // Ensure you have this layout

        val toolbar: Toolbar = findViewById(R.id.toolbar_fb) // Ensure you have this ID
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Transactions from Firebase"

        recyclerView = findViewById(R.id.transactionsRecyclerViewFB) // Ensure you have this ID
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = TransactionsAdapterFB(transactions)
        recyclerView.adapter = adapter

        database = FirebaseDatabase.getInstance().getReference("J5/sms_by_date")
        loadTransactionsFromFB()
        createNotificationChannel()
    }

    private fun parseFirebaseDate(dateString: String?): Long? {
        if (dateString == null) return null
        return try {
            FIREBASE_DATE_FORMAT.parse(dateString)?.time
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse date string: $dateString", e)
            null
        }
    }

    private fun isTransactionForToday(transactionTimestamp: Long?): Boolean {
        if (transactionTimestamp == null) return false
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return transactionTimestamp >= todayStart
    }

    private fun loadTransactionsFromFB() {
        // Listener for initial load of all transactions (excluding today's for the main list, handled by separate listener)
        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val historicalTransactions = mutableListOf<TransactionInfo>()
                for (dateGroupSnapshot in snapshot.children) { // "dd-MMM-yy"
                    for (entrySnapshot in dateGroupSnapshot.children) { // Transaction ID
                        val id = entrySnapshot.key
                        if (id == null) {
                            Log.w(TAG, "Skipping transaction with null ID")
                            continue
                        }

                        val firebaseDateValue = entrySnapshot.child("date").getValue()
                        val firebaseStrDateInMessage = entrySnapshot.child("strDateInMessage").getValue(String::class.java)

                        var finalTimestamp: Long? = null
                        var finalStrDate: String? = null

                        if (firebaseDateValue is Long) { // New format
                            finalTimestamp = firebaseDateValue
                            finalStrDate = firebaseStrDateInMessage
                        } else if (firebaseDateValue is String) { // Old format
                            finalStrDate = firebaseDateValue
                            finalTimestamp = parseFirebaseDate(finalStrDate)
                        } else { // Fallback or unknown
                            if (firebaseStrDateInMessage != null) {
                                finalStrDate = firebaseStrDateInMessage
                                finalTimestamp = parseFirebaseDate(finalStrDate)
                            } else {
                                Log.e(TAG, "Date info missing or unexpected for ID: $id. Path: ${entrySnapshot.ref.path}")
                            }
                        }

                        // Fetch all fields for TransactionInfo
                        val account = entrySnapshot.child("account").getValue(String::class.java) ?: ""
                        val transactionType = entrySnapshot.child("transactionType").getValue(String::class.java) ?: ""
                        val amount = entrySnapshot.child("amount").getValue(String::class.java) ?: ""
                        val transactionReference = entrySnapshot.child("transactionReference").getValue(String::class.java) ?: ""
                        val raw = entrySnapshot.child("raw").getValue(String::class.java) ?: ""
                        val upi = entrySnapshot.child("upi").getValue(String::class.java)
                        val name = entrySnapshot.child("name").getValue(String::class.java)
                        val accountBalance = entrySnapshot.child("accountBalance").getValue(String::class.java)
                        val receivedFrom = entrySnapshot.child("receivedFrom").getValue(String::class.java)
                        val transferredTo = entrySnapshot.child("transferredTo").getValue(String::class.java)
                        val isRawExpanded = entrySnapshot.child("isRawExpanded").getValue(Boolean::class.java) ?: false

                        val transaction = TransactionInfo(
                            id = id,
                            strDateInMessage = finalStrDate,
                            date = finalTimestamp,
                            account = account,
                            transactionType = transactionType,
                            amount = amount,
                            transactionReference = transactionReference,
                            raw = raw,
                            upi = upi,
                            name = name,
                            accountBalance = accountBalance,
                            receivedFrom = receivedFrom,
                            transferredTo = transferredTo,
                            isRawExpanded = isRawExpanded
                        )
                        historicalTransactions.add(transaction)
                    }
                }
                transactions.clear()
                transactions.addAll(historicalTransactions)
                adapter.updateData(transactions) // Adapter sorts by date (timestamp)
                Log.d(TAG, "Loaded ${historicalTransactions.size} initial transactions from Firebase.")
                setupTodayTransactionsListener() // Setup listener for today after initial load
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to load initial transactions", error.toException())
            }
        })
    }

    private fun setupTodayTransactionsListener() {
        val todayDateStr = SimpleDateFormat("dd-MMM-yy", Locale.ENGLISH).format(Date())
        todayRef = database.child(todayDateStr)

        todayValueEventListener?.let { todayRef.removeEventListener(it) } // Remove previous listener if any

        todayValueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var newNotificationOccurred = false
                val currentTodayTransactions = mutableMapOf<String, TransactionInfo>()

                for (entrySnapshot in snapshot.children) { // Transaction ID
                    val id = entrySnapshot.key
                    if (id == null) {
                        Log.w(TAG, "Skipping today's transaction with null ID")
                        continue
                    }

                    val firebaseDateValue = entrySnapshot.child("date").getValue()
                    val firebaseStrDateInMessage = entrySnapshot.child("strDateInMessage").getValue(String::class.java)

                    var finalTimestamp: Long? = null
                    var finalStrDate: String? = null

                    if (firebaseDateValue is Long) { // New format
                        finalTimestamp = firebaseDateValue
                        finalStrDate = firebaseStrDateInMessage
                    } else if (firebaseDateValue is String) { // Old format
                        finalStrDate = firebaseDateValue
                        finalTimestamp = parseFirebaseDate(finalStrDate)
                    } else { // Fallback or unknown
                        if (firebaseStrDateInMessage != null) {
                            finalStrDate = firebaseStrDateInMessage
                            finalTimestamp = parseFirebaseDate(finalStrDate)
                        } else {
                            Log.e(TAG, "Today's Date info missing or unexpected for ID: $id. Path: ${entrySnapshot.ref.path}")
                        }
                    }

                    // Fetch all fields for TransactionInfo
                    val account = entrySnapshot.child("account").getValue(String::class.java) ?: ""
                    val transactionType = entrySnapshot.child("transactionType").getValue(String::class.java) ?: ""
                    val amount = entrySnapshot.child("amount").getValue(String::class.java) ?: ""
                    val transactionReference = entrySnapshot.child("transactionReference").getValue(String::class.java) ?: ""
                    val raw = entrySnapshot.child("raw").getValue(String::class.java) ?: ""
                    val upi = entrySnapshot.child("upi").getValue(String::class.java)
                    val name = entrySnapshot.child("name").getValue(String::class.java)
                    val accountBalance = entrySnapshot.child("accountBalance").getValue(String::class.java)
                    val receivedFrom = entrySnapshot.child("receivedFrom").getValue(String::class.java)
                    val transferredTo = entrySnapshot.child("transferredTo").getValue(String::class.java)
                    val isRawExpanded = entrySnapshot.child("isRawExpanded").getValue(Boolean::class.java) ?: false

                    val transaction = TransactionInfo(
                        id = id,
                        strDateInMessage = finalStrDate,
                        date = finalTimestamp,
                        account = account,
                        transactionType = transactionType,
                        amount = amount,
                        transactionReference = transactionReference,
                        raw = raw,
                        upi = upi,
                        name = name,
                        accountBalance = accountBalance,
                        receivedFrom = receivedFrom,
                        transferredTo = transferredTo,
                        isRawExpanded = isRawExpanded
                    )

                    currentTodayTransactions[id] = transaction

                    if (!todayTransactionsListenerMap.containsKey(id) && isTransactionForToday(transaction.date)) {
                        showTransactionNotification(transaction)
                        newNotificationOccurred = true
                    }
                }

                // Update master list: remove all old today's, add current today's
                transactions.removeAll { tran -> isTransactionForToday(tran.date) && tran.strDateInMessage == todayDateStr }
                transactions.addAll(currentTodayTransactions.values)

                // Update the map for the next comparison
                todayTransactionsListenerMap.clear()
                todayTransactionsListenerMap.putAll(currentTodayTransactions.filter { isTransactionForToday(it.value.date) })

                adapter.updateData(transactions) // Adapter sorts by date (timestamp)
                if (newNotificationOccurred) {
                    Log.d(TAG, "Processed today's transactions updates, new notifications shown.")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to listen for today's transactions", error.toException())
            }
        }
        todayRef.addValueEventListener(todayValueEventListener!!)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name) // Define in strings.xml
            val descriptionText = getString(R.string.channel_description) // Define in strings.xml
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showTransactionNotification(transaction: TransactionInfo) {
        val intent = Intent(this, TransactionsInFBActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notificationTitle = "New Transaction" // Using default as sender is not in TransactionInfo
        val notificationText = transaction.raw.ifEmpty { "Transaction details received." } // Using raw as messageBody is not in TransactionInfo

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_message) // Ensure you have this drawable
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(transaction.id.hashCode(), builder.build()) // Use transaction id hash for unique notification
    }

    override fun onDestroy() {
        super.onDestroy()
        todayValueEventListener?.let { todayRef.removeEventListener(it) }
    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Check if the current flavor is fbTransactionsOnly
        if (BuildConfig.FLAVOR == "fbTransactionsOnly") {
            // Don't inflate or show any menu for this flavor
            menu.clear() // Clear any items that might have been added by a superclass
            return false // Return false to indicate no menu should be displayed
        } else {
            // Existing menu inflation logic for other flavors
            menuInflater.inflate(R.menu.menu_main, menu) // Reuse existing menu or create a new one
            return true
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_sms, R.id.menu_transaction -> {
                // For simplicity, let's assume these take you to TransactionActivity
                // which can then decide to load SMS or local transactions.
                // Or, if you have a specific activity for SMS list, use that for menu_sms.
                startActivity(Intent(this, TransactionActivity::class.java))
                finish() // Optional: finish this activity
                true
            }
            R.id.menu_conversations -> {
                startActivity(Intent(this, MainActivity::class.java))
                finish() // Optional: finish this activity
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
