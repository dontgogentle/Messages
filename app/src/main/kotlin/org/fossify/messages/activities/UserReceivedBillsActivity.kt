package org.fossify.messages.activities

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.fossify.messages.adapters.UserReceivedBillAdapter
import org.fossify.messages.databinding.ActivityUserReceivedBillsBinding
import org.fossify.messages.models.UserReceivedBill

class UserReceivedBillsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserReceivedBillsBinding
    private lateinit var userReceivedBillAdapter: UserReceivedBillAdapter
    private val receivedBillsList = mutableListOf<UserReceivedBill>()

    private var userId: String? = null
    private var siteName: String? = null
    private var inmateName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserReceivedBillsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getStringExtra("USER_ID")
        siteName = intent.getStringExtra("SITE_NAME")
        inmateName = intent.getStringExtra("INMATE_NAME")

        if (userId == null || siteName == null || inmateName == null) {
            Log.e("UserReceivedBillsActivity", "User ID, Site Name, or Inmate Name not provided.")
            finish()
            return
        }

        setupToolbar()
        setupRecyclerView()
        fetchUserReceivedBills()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarUserReceivedBills)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = inmateName
    }

    private fun setupRecyclerView() {
        userReceivedBillAdapter = UserReceivedBillAdapter()
        binding.recyclerViewUserReceivedBills.apply {
            layoutManager = LinearLayoutManager(this@UserReceivedBillsActivity)
            adapter = userReceivedBillAdapter
        }
    }

    private fun fetchUserReceivedBills() {
        val databasePath = "$siteName/ReceivedBills/$userId"
        Log.d("UserReceivedBillsActivity", "Fetching received bills from: $databasePath")
        val database = FirebaseDatabase.getInstance().getReference(databasePath)

        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                receivedBillsList.clear()
                if (snapshot.exists()) {
                    for (billSnapshot in snapshot.children) {
                        // Manual parsing for each field with defaults
                        val billTag = billSnapshot.child("billTag").getValue(String::class.java) ?: ""
//                        val collectedAmount = billSnapshot.child("collectedAmount").getValue(String::class.java)?.toLong() ?: 0 // Default to 0 if missing, 0 means writeoff, still it should exist as 0 in fb
                        val collectedDate = billSnapshot.child("collectedDate").getValue(String::class.java) ?: ""
                        val collectedAmount = when (val value = billSnapshot.child("collectedAmount").value) {
                            is Long -> value
                            is String -> value.toLongOrNull() ?: 0L
                            is Double -> value.toLong() // Just in case someone wrote it as a floating point
                            else -> 0L // Default to 0 if missing or unrecognized type
                        }
                        val collectionTransactionUid = billSnapshot.child("collectionTransactionUid").getValue(String::class.java) ?: ""
                        val description = billSnapshot.child("description").getValue(String::class.java) ?: ""
                        val dueDate = billSnapshot.child("dueDate").getValue(String::class.java) ?: ""
                        val notes = billSnapshot.child("notes").getValue(String::class.java) ?: ""

                        // For Long, ensure it's handled correctly if it might be missing or a different type
                        val transactionAmountFirebase = billSnapshot.child("transactionAmount").value
                        val transactionAmount = when (transactionAmountFirebase) {
                            is Long -> transactionAmountFirebase
                            is Number -> transactionAmountFirebase.toLong() // Handle other number types like Integer
                            else -> 0L // Default if missing, null, or wrong type
                        }

                        val uid = billSnapshot.child("uid").getValue(String::class.java) ?: ""

                        val receivedBill = UserReceivedBill(
                            billTag = billTag,
                            collectedAmount = collectedAmount,
                            collectedDate = collectedDate,
                            collectionTransactionUid = collectionTransactionUid,
                            description = description,
                            dueDate = dueDate,
                            notes = notes,
                            transactionAmount = transactionAmount,
                            uid = uid
                        )
                        receivedBillsList.add(receivedBill)
                    }
                } else {
                    Log.d("UserReceivedBillsActivity", "No received bills found for user $userId at $databasePath")
                    // Consider clearing the list or showing a "no data" message if appropriate
                    // receivedBillsList.clear() // Already cleared at the start of onDataChange
                }
                userReceivedBillAdapter.submitList(receivedBillsList.toList())
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("UserReceivedBillsActivity", "Firebase error: ${error.message}")
            }
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
