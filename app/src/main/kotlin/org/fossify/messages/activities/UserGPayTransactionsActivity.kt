package org.fossify.messages.activities

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar // Keep this, it's used by the activity
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat // Added for color
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import org.fossify.messages.R
import org.fossify.messages.databinding.ActivityUserGpayTransactionsBinding
import org.fossify.messages.models.GPayTransactionInfo // IMPORTING THE CORRECT MODEL

// GPayTransactionFB data class REMOVED

data class ExpandableUserItem(
    val userName: String,
    var isExpanded: Boolean = false,
    var transactions: List<GPayTransactionInfo>? = null, // CHANGED TO GPayTransactionInfo
    var isLoadingTransactions: Boolean = false
)

sealed class DisplayListItem {
    data class UserHeaderItem(val userItem: ExpandableUserItem) : DisplayListItem()
    data class TransactionRowItem(val transaction: GPayTransactionInfo) : DisplayListItem() // CHANGED TO GPayTransactionInfo
    data class LoadingTransactionsItem(val userName: String) : DisplayListItem()
    data class NoTransactionsItem(val userName: String) : DisplayListItem()

    open val id: String get() = when (this) {
        is UserHeaderItem -> "user_${userItem.userName}"
        is TransactionRowItem -> "txn_${transaction.id}" // Assuming GPayTransactionInfo has an 'id' field
        is LoadingTransactionsItem -> "loading_$userName"
        is NoTransactionsItem -> "no_txn_$userName"
    }
}


class UserGPayTransactionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserGpayTransactionsBinding
    private lateinit var database: FirebaseDatabase
    private lateinit var expandableAdapter: ExpandableUserTransactionsAdapter
    private val usersDataList = mutableListOf<ExpandableUserItem>()

    companion object {
        private const val TAG = "UserGPayActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserGpayTransactionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarUserGPayTransactions)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.user_gpay_transactions_title)

        database = FirebaseDatabase.getInstance()

        setupRecyclerView()
        fetchUsers()
    }

    private fun setupRecyclerView() {
        expandableAdapter = ExpandableUserTransactionsAdapter { clickedUserItem ->
            handleUserHeaderClick(clickedUserItem.userItem.userName)
        }
        binding.usersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@UserGPayTransactionsActivity)
            adapter = expandableAdapter
        }
    }

    private fun handleUserHeaderClick(userName: String) {
        val userIndex = usersDataList.indexOfFirst { it.userName == userName }
        if (userIndex == -1) return

        val userItem = usersDataList[userIndex]
        userItem.isExpanded = !userItem.isExpanded

        if (userItem.isExpanded && userItem.transactions == null && !userItem.isLoadingTransactions) {
            userItem.isLoadingTransactions = true
            buildAndSubmitDisplayList()
            fetchTransactionsForUser(userItem)
        } else {
            buildAndSubmitDisplayList()
        }
    }

    private fun fetchUsers() {
        binding.progressBarUserGPay.visibility = View.VISIBLE
        binding.emptyStateTextViewUserGPay.visibility = View.GONE
        binding.usersRecyclerView.visibility = View.GONE

        val usersRef = database.getReference("gpaybyUser")

        usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                binding.progressBarUserGPay.visibility = View.GONE
                usersDataList.clear()
                snapshot.children.forEach { userSnapshot ->
                    userSnapshot.key?.let { userName ->
                        usersDataList.add(ExpandableUserItem(userName = userName))
                    }
                }
                usersDataList.sortBy { it.userName }

                if (usersDataList.isEmpty()) {
                    binding.emptyStateTextViewUserGPay.text = getString(R.string.no_users_found)
                    binding.emptyStateTextViewUserGPay.visibility = View.VISIBLE
                    binding.usersRecyclerView.visibility = View.GONE
                } else {
                    binding.emptyStateTextViewUserGPay.visibility = View.GONE
                    binding.usersRecyclerView.visibility = View.VISIBLE
                }
                buildAndSubmitDisplayList()
            }

            override fun onCancelled(error: DatabaseError) {
                binding.progressBarUserGPay.visibility = View.GONE
                Log.e(TAG, "Error fetching users: ${error.message}")
                binding.emptyStateTextViewUserGPay.text = getString(R.string.failed_to_load_users)
                binding.emptyStateTextViewUserGPay.visibility = View.VISIBLE
                Toast.makeText(this@UserGPayTransactionsActivity, getString(R.string.failed_to_load_users), Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun fetchTransactionsForUser(userItem: ExpandableUserItem) {
        val userTransactionIdsRef = database.getReference("gpaybyUser").child(userItem.userName)
        val gpayRef = database.getReference("gpay")
        val fetchedTransactions = mutableListOf<GPayTransactionInfo>() // CHANGED TO GPayTransactionInfo

        userTransactionIdsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val transactionIds = snapshot.children.mapNotNull { it.key }
                if (transactionIds.isEmpty()) {
                    userItem.transactions = emptyList()
                    userItem.isLoadingTransactions = false
                    buildAndSubmitDisplayList()
                    return
                }

                var transactionsToFetch = transactionIds.size
                transactionIds.forEach { transId ->
                    gpayRef.child(transId).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(transSnapshot: DataSnapshot) {
                            transSnapshot.getValue(GPayTransactionInfo::class.java)?.let { // CHANGED TO GPayTransactionInfo
                                // Assuming GPayTransactionInfo has an 'id' field, if not, this needs adjustment or it comes from transSnapshot.key
                                // For now, let's assume the 'id' field is correctly populated in GPayTransactionInfo
                                fetchedTransactions.add(it)
                            }
                            transactionsToFetch--
                            if (transactionsToFetch == 0) {
                                // Assuming GPayTransactionInfo has 'creationTime' or similar for sorting
                                userItem.transactions = fetchedTransactions.sortedByDescending { it.creationTime }
                                userItem.isLoadingTransactions = false
                                buildAndSubmitDisplayList()
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            transactionsToFetch--
                            Log.e(TAG, "Error fetching transaction $transId for ${userItem.userName}: ${error.message}")
                            if (transactionsToFetch == 0) {
                                userItem.transactions = fetchedTransactions.sortedByDescending { it.creationTime }
                                userItem.isLoadingTransactions = false
                                buildAndSubmitDisplayList()
                            }
                        }
                    })
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error fetching transaction IDs for ${userItem.userName}: ${error.message}")
                userItem.isLoadingTransactions = false
                userItem.transactions = emptyList()
                buildAndSubmitDisplayList()
            }
        })
    }


    private fun buildAndSubmitDisplayList() {
        val displayList = mutableListOf<DisplayListItem>()
        if (usersDataList.isEmpty() && binding.progressBarUserGPay.visibility == View.GONE) {
            // empty state handled by visibility changes in fetchUsers
        } else if (usersDataList.isNotEmpty()) {
            binding.usersRecyclerView.visibility = View.VISIBLE
            binding.emptyStateTextViewUserGPay.visibility = View.GONE
        }

        usersDataList.forEach { user ->
            displayList.add(DisplayListItem.UserHeaderItem(user))
            if (user.isExpanded) {
                if (user.isLoadingTransactions) {
                    displayList.add(DisplayListItem.LoadingTransactionsItem(user.userName))
                } else {
                    user.transactions?.let { transactions ->
                        if (transactions.isEmpty()) {
                            displayList.add(DisplayListItem.NoTransactionsItem(user.userName))
                        } else {
                            transactions.forEach { transaction ->
                                displayList.add(DisplayListItem.TransactionRowItem(transaction))
                            }
                        }
                    }
                }
            }
        }
        expandableAdapter.submitList(displayList)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

class ExpandableUserTransactionsAdapter(
    private val onUserHeaderClick: (DisplayListItem.UserHeaderItem) -> Unit
) : ListAdapter<DisplayListItem, RecyclerView.ViewHolder>(DisplayListItemDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_USER_HEADER = 1
        private const val VIEW_TYPE_TRANSACTION_ROW = 2
        private const val VIEW_TYPE_LOADING = 3
        private const val VIEW_TYPE_NO_TRANSACTIONS = 4
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is DisplayListItem.UserHeaderItem -> VIEW_TYPE_USER_HEADER
            is DisplayListItem.TransactionRowItem -> VIEW_TYPE_TRANSACTION_ROW
            is DisplayListItem.LoadingTransactionsItem -> VIEW_TYPE_LOADING
            is DisplayListItem.NoTransactionsItem -> VIEW_TYPE_NO_TRANSACTIONS
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_USER_HEADER -> UserHeaderViewHolder(
                inflater.inflate(R.layout.item_user_header, parent, false)
            )
            VIEW_TYPE_TRANSACTION_ROW -> TransactionViewHolder(
                inflater.inflate(R.layout.item_transaction_fb, parent, false)
            )
            VIEW_TYPE_LOADING -> LoadingViewHolder(
                inflater.inflate(R.layout.item_loading_transactions, parent, false)
            )
            VIEW_TYPE_NO_TRANSACTIONS -> NoTransactionsViewHolder(
                inflater.inflate(R.layout.item_no_transactions, parent, false)
            )
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is DisplayListItem.UserHeaderItem -> {
                (holder as UserHeaderViewHolder).bind(item)
                holder.itemView.setOnClickListener { onUserHeaderClick(item) }
            }
            is DisplayListItem.TransactionRowItem -> (holder as TransactionViewHolder).bind(item.transaction)
            is DisplayListItem.LoadingTransactionsItem -> (holder as LoadingViewHolder).bind(item)
            is DisplayListItem.NoTransactionsItem -> (holder as NoTransactionsViewHolder).bind(item)
        }
    }

    class UserHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userNameTextView: TextView = itemView.findViewById(R.id.userNameTextView)
        private val expandIcon: ImageView = itemView.findViewById(R.id.expandIcon)
        fun bind(item: DisplayListItem.UserHeaderItem) {
            userNameTextView.text = item.userItem.userName
            expandIcon.setImageResource(
                if (item.userItem.isExpanded) R.drawable.ic_keyboard_arrow_up else R.drawable.ic_keyboard_arrow_down
            )
        }
    }

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val descriptionTextView: TextView = itemView.findViewById(R.id.transaction_fb_description_or_name)
        private val amountTextView: TextView = itemView.findViewById(R.id.transaction_fb_amount)
        private val dateTextView: TextView = itemView.findViewById(R.id.transaction_fb_date)
        private val typeIndicatorView: TextView = itemView.findViewById(R.id.transaction_fb_type_label) // This is a TextView

        fun bind(transaction: GPayTransactionInfo) { // CHANGED TO GPayTransactionInfo
            descriptionTextView.text = transaction.name // Use 'name' from GPayTransactionInfo
            amountTextView.text = transaction.amount // Use 'amount' from GPayTransactionInfo
            dateTextView.text = transaction.creationTime // Use 'creationTime' from GPayTransactionInfo

            typeIndicatorView.text = "CREDIT" // Always show CREDIT
            // REMOVED: typeIndicatorView.setBackgroundColor(...)
            amountTextView.setTextColor(ContextCompat.getColor(itemView.context, R.color.green_dark)) // Always green
        }
    }

    class LoadingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(item: DisplayListItem.LoadingTransactionsItem) {
            // Optional
        }
    }

    class NoTransactionsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageTextView: TextView = itemView.findViewById(R.id.noTransactionsMessageTextView)
        fun bind(item: DisplayListItem.NoTransactionsItem) {
            messageTextView.text = itemView.context.getString(R.string.no_transactions_for_user, item.userName)
        }
    }
}

class DisplayListItemDiffCallback : DiffUtil.ItemCallback<DisplayListItem>() {
    override fun areItemsTheSame(oldItem: DisplayListItem, newItem: DisplayListItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: DisplayListItem, newItem: DisplayListItem): Boolean {
        return oldItem == newItem
    }
}
