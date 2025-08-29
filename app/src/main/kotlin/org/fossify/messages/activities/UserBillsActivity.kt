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
import org.fossify.messages.adapters.UserBillAdapter
import org.fossify.messages.databinding.ActivityUserBillsBinding
import org.fossify.messages.models.UserBill

class UserBillsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserBillsBinding
    private lateinit var userBillAdapter: UserBillAdapter
    private val billsList = mutableListOf<UserBill>()

    private var userId: String? = null
    private var siteName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserBillsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getStringExtra("USER_ID")
        siteName = intent.getStringExtra("SITE_NAME")
        val inmateName = intent.getStringExtra("INMATE_NAME") // <<< ADD THIS

        if (userId == null || siteName == null || inmateName == null) { // <<< MODIFIED THIS
            Log.e("UserBillsActivity", "User ID, Site Name, or Inmate Name not provided.")
            finish() // Close activity if essential data is missing
            return
        }

        setupToolbar(inmateName)
        setupRecyclerView()
        fetchUserBills()
    }

    private fun setupToolbar(inmateName: String) { // <<< MODIFIED THIS
        setSupportActionBar(binding.toolbarUserBills)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = inmateName+" Bills" // <<< MODIFIED THIS (was "Pending Bills for User")
    }

    private fun setupRecyclerView() {
        userBillAdapter = UserBillAdapter()
        binding.recyclerViewUserBills.apply {
            layoutManager = LinearLayoutManager(this@UserBillsActivity)
            adapter = userBillAdapter
        }
    }

    private fun fetchUserBills() {
        val databasePath = "$siteName/PendingBills/$userId"
        Log.d("UserBillsActivity", "Fetching bills from: $databasePath")
        val database = FirebaseDatabase.getInstance().getReference(databasePath)

        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                billsList.clear()
                if (snapshot.exists()) {
                    for (billSnapshot in snapshot.children) {
                        val bill = billSnapshot.getValue(UserBill::class.java)
                        bill?.let {
                            // The bill's own UID is already in the object if mapped correctly by UserBill.kt
                            // If not, and it's the key of billSnapshot, you might need to do:
                            // val billWithId = it.copy(uid = billSnapshot.key ?: "") 
                            billsList.add(it)
                        }
                    }
                } else {
                    Log.d("UserBillsActivity", "No bills found for user $userId at $databasePath")
                    // Optionally, show a message to the user that no bills are pending
                }
                userBillAdapter.submitList(billsList.toList()) // Use toList() for a new immutable list
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("UserBillsActivity", "Firebase error: ${error.message}")
                // Handle error, e.g., show a toast
            }
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle arrow click here
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed() // or finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
