package org.fossify.messages.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.fossify.messages.R
import org.fossify.messages.activities.MainActivity

class TransactionsInFBActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TransactionAdapter
    private val transactions = mutableListOf<TransactionInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction) // Assuming this layout is suitable or will be adapted

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        recyclerView = findViewById(R.id.transactionRecyclerView) // Ensure this ID matches your layout
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = TransactionAdapter(transactions) // Ensure TransactionAdapter can be reused
        recyclerView.adapter = adapter

        loadTransactionsFromFB()
    }

    private fun loadTransactionsFromFB() {
        val database = FirebaseDatabase.getInstance().getReference("J5/sms_by_date") // Updated Firebase path
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { // snapshot is for "J5/sms_by_date"
                transactions.clear()
                for (dateSnapshot in snapshot.children) { // Iterate over each $dateString
                    for (entrySnapshot in dateSnapshot.children) { // Iterate over each $entryId under $dateString
                        val transaction = entrySnapshot.getValue(TransactionInfo::class.java)
                        transaction?.let { transactions.add(it) }
                    }
                }
                adapter.updateData(transactions.sortedByDescending { it.date }) // Assuming TransactionAdapter has updateData and TransactionInfo has a date field
                Log.d("TransactionsInFB", "Loaded ${transactions.size} transactions from Firebase.")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("TransactionsInFB", "Failed to load transactions from Firebase.", error.toException())
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu) // Reuse existing menu or create a new one
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_conversations -> {
                startActivity(Intent(this, MainActivity::class.java))
                true
            }
            R.id.menu_transaction -> { 
                startActivity(Intent(this, TransactionActivity::class.java))
                true
            }
            // R.id.action_show_fb_transactions is handled in MainActivity if this is the same menu.
            // If you want a specific action here (e.g., refresh), you can add a new item or handle an existing one.
            // For example, if R.id.menu_transaction_fb was a specific item for this screen:
            // R.id.menu_transaction_fb -> { 
            //    loadTransactionsFromFB() // Refresh data
            //    true
            // }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
