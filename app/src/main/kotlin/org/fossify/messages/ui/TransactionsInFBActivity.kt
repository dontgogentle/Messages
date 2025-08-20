package org.fossify.messages.ui

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import org.fossify.messages.R
import org.fossify.messages.databinding.ActivityTransactionsInFbBinding
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale // Added for SimpleDateFormat Locale
import org.fossify.messages.BuildConfig // Replace org.fossify.messages with your actual applicationId

class TransactionsInFBActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTransactionsInFbBinding
    private lateinit var database: DatabaseReference
    private lateinit var transactionsRecyclerView: RecyclerView
    private lateinit var adapter: TransactionsAdapterFB
    private var transactionsList = mutableListOf<TransactionInfo>()
    // Removed: private lateinit var fab: FloatingActionButton

    // Flag to control whether to use Firebase or sample data
    private val USE_FIREBASE_DATA = !BuildConfig.DEBUG

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
            database = FirebaseDatabase.getInstance().getReference("transactions")
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


    private fun loadTransactionsFromFB() {
        binding.progressBarFb.visibility = View.VISIBLE
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                transactionsList.clear()
                for (transactionSnapshot in snapshot.children) {
                    val transaction = transactionSnapshot.getValue(TransactionInfo::class.java)
                    transaction?.let {
                        if (it.id == null || it.id!!.isEmpty()) {
                            it.id = transactionSnapshot.key
                        }
                        transactionsList.add(it)
                    }
                }
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
        val todayStartTimestamp = getStartOfDay(System.currentTimeMillis())
        val query = database.orderByChild("date").startAt(todayStartTimestamp.toDouble())

        query.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("TransactionsInFB", "Today's transactions updated: ${snapshot.childrenCount} items")
                // Here, you might want to specifically update the adapter with today's transactions
                // or merge them intelligently into the existing list if the main listener
                // isn't catching these updates efficiently for some reason.
                // For now, this just logs. The main listener should ideally handle all updates.
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("TransactionsInFB", "Failed to listen for today's transactions", error.toException())
            }
        })
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
}
