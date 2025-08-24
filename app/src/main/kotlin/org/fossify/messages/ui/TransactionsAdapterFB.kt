package org.fossify.messages.ui

import android.graphics.Typeface
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.fossify.messages.R
import org.fossify.messages.ui.TransactionInfo // Ensure this is the correct import
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Sealed class for different item types in the RecyclerView
sealed class AdapterItemFB {
    data class TransactionItem(val transaction: TransactionInfo) : AdapterItemFB()
    data class DateHeaderItem(val dateString: String) : AdapterItemFB()
}

class TransactionsAdapterFB(initialTransactions: List<TransactionInfo>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var displayItems: MutableList<AdapterItemFB> = mutableListOf()

    companion object {
        private const val TYPE_TRANSACTION = 0
        private const val TYPE_DATE_HEADER = 1
    }

    init {
        processAndUpdateData(initialTransactions)
    }

    // ViewHolder for Transaction Items
    class TransactionFBViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val descriptionOrNameTextView: TextView = itemView.findViewById(R.id.transaction_fb_description_or_name)
        val amountTextView: TextView = itemView.findViewById(R.id.transaction_fb_amount)
        val dateTextView: TextView = itemView.findViewById(R.id.transaction_fb_date)
        val typeLabelTextView: TextView = itemView.findViewById(R.id.transaction_fb_type_label)
        val accountDetailsTextView: TextView = itemView.findViewById(R.id.transaction_fb_account_details)
        val referenceDetailsTextView: TextView = itemView.findViewById(R.id.transaction_fb_reference_details)
        val upiDetailsTextView: TextView = itemView.findViewById(R.id.transaction_fb_upi_details)
        val balanceDetailsTextView: TextView = itemView.findViewById(R.id.transaction_fb_balance_details)
        val rawDataTextView: TextView = itemView.findViewById(R.id.transaction_fb_raw_data)
    }

    // ViewHolder for Date Header Items
    class DateHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dateHeaderTextView: TextView = itemView.findViewById(R.id.date_header_text_fb)
    }

    override fun getItemViewType(position: Int): Int {
        return when (displayItems[position]) {
            is AdapterItemFB.TransactionItem -> TYPE_TRANSACTION
            is AdapterItemFB.DateHeaderItem -> TYPE_DATE_HEADER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_TRANSACTION -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_transaction_fb, parent, false)
                TransactionFBViewHolder(view)
            }
            TYPE_DATE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_date_header_fb, parent, false)
                DateHeaderViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val currentItem = displayItems[position]) {
            is AdapterItemFB.TransactionItem -> {
                val transactionHolder = holder as TransactionFBViewHolder
                val transaction = currentItem.transaction
                val context = transactionHolder.itemView.context

                var primaryIdentifier = transaction.name
                if (primaryIdentifier.isNullOrEmpty()) {
                    transactionHolder.descriptionOrNameTextView.text = ""
                } else {
                    transactionHolder.descriptionOrNameTextView.text = primaryIdentifier
                }
                transactionHolder.amountTextView.text = transaction.amount

                val displayDateText = if (transaction.date != null) {
                    SimpleDateFormat("dd MMM yy, hh:mm a", Locale.getDefault()).format(Date(transaction.date!!))
                } else if (!transaction.strDateInMessage.isNullOrEmpty()) {
                    transaction.strDateInMessage
                } else {
                    "No Date"
                }
                transactionHolder.dateTextView.text = displayDateText
                transactionHolder.dateTextView.setTypeface(null, Typeface.NORMAL)

                val typeText = when (transaction.transactionType.lowercase(Locale.getDefault())) {
                    "credit" -> "CREDIT"
                    "debit" -> "DEBIT"
                    else -> transaction.transactionType.uppercase(Locale.getDefault())
                }
                if (typeText.isNotEmpty() && (typeText == "CREDIT" || typeText == "DEBIT")) {
                    transactionHolder.typeLabelTextView.text = typeText
                    transactionHolder.typeLabelTextView.visibility = View.VISIBLE
                } else {
                    transactionHolder.typeLabelTextView.visibility = View.GONE
                }

                if (!transaction.account.isNullOrEmpty()) {
                    transactionHolder.accountDetailsTextView.text = "Account: ${transaction.account}"
                    transactionHolder.accountDetailsTextView.visibility = View.VISIBLE
                } else {
                    transactionHolder.accountDetailsTextView.visibility = View.GONE
                }

                if (!transaction.transactionReference.isNullOrEmpty()) {
                    transactionHolder.referenceDetailsTextView.text = "Ref: ${transaction.transactionReference}"
                    transactionHolder.referenceDetailsTextView.visibility = View.VISIBLE
                } else {
                    transactionHolder.referenceDetailsTextView.visibility = View.GONE
                }

                if (!transaction.upi.isNullOrEmpty()) {
                    transactionHolder.upiDetailsTextView.text = "UPI: ${transaction.upi}"
                    transactionHolder.upiDetailsTextView.visibility = View.VISIBLE
                } else {
                    transactionHolder.upiDetailsTextView.visibility = View.GONE
                }

                if (!transaction.accountBalance.isNullOrEmpty()) {
                    transactionHolder.balanceDetailsTextView.text = "Balance: ${transaction.accountBalance}"
                    transactionHolder.balanceDetailsTextView.visibility = View.VISIBLE
                } else {
                    transactionHolder.balanceDetailsTextView.visibility = View.GONE
                }

                val backgroundColor = when (transaction.transactionType.lowercase(Locale.getDefault())) {
                    "credit" -> ContextCompat.getColor(context, R.color.transaction_credit_background)
                    "debit" -> ContextCompat.getColor(context, R.color.transaction_debit_background)
                    else -> ContextCompat.getColor(context, R.color.default_transaction_background)
                }
                if (transactionHolder.itemView is CardView) {
                    (transactionHolder.itemView as CardView).setCardBackgroundColor(backgroundColor)
                }

                transactionHolder.rawDataTextView.text = transaction.raw
                transactionHolder.itemView.setOnClickListener {
                    transaction.isRawExpanded = !transaction.isRawExpanded
                    transactionHolder.rawDataTextView.visibility = if (transaction.isRawExpanded) View.VISIBLE else View.GONE
                }
                transactionHolder.rawDataTextView.visibility = if (transaction.isRawExpanded) View.VISIBLE else View.GONE
            }
            is AdapterItemFB.DateHeaderItem -> {
                val dateHeaderHolder = holder as DateHeaderViewHolder
                dateHeaderHolder.dateHeaderTextView.text = currentItem.dateString
            }
        }
    }

    override fun getItemCount() = displayItems.size

    private fun getHeaderDateFormatter(): SimpleDateFormat {
        return SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault())
    }

    private fun processAndUpdateData(newTransactions: List<TransactionInfo>) {
        val TAG = "processAndUpdateData"
        Log.d(TAG, "processAndUpdateData called with ${newTransactions.size} new transactions.")
        val newDisplayItems = mutableListOf<AdapterItemFB>()
        if (newTransactions.isEmpty()) {
            Log.d(TAG, "No new transactions, clearing display items.")
            displayItems.clear()
            notifyDataSetChanged()
            return
        }

        val sortedTransactions = newTransactions.sortedByDescending { it.date ?: 0L }
        Log.d(TAG, "Transactions sorted. Total: ${sortedTransactions.size}")

        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))

        val headerFormatter = getHeaderDateFormatter()

        var lastHeaderDay = -1
        var lastHeaderMonth = -1
        var lastHeaderYear = -1
        Log.d(TAG, "Initial lastHeaderDate: Y=$lastHeaderYear, M=$lastHeaderMonth, D=$lastHeaderDay")

        for ((index, transaction) in sortedTransactions.withIndex()) {
            val transactionDateMillis = transaction.date ?: continue // Skip if date is null
            calendar.timeInMillis = transactionDateMillis

            val currentTransactionDay = calendar.get(Calendar.DAY_OF_MONTH)
            val currentTransactionMonth = calendar.get(Calendar.MONTH) // 0-indexed
            val currentTransactionYear = calendar.get(Calendar.YEAR)

            Log.d(TAG, "Loop $index: TxDateMillis=$transactionDateMillis -> Current Y=$currentTransactionYear, M=$currentTransactionMonth, D=$currentTransactionDay")
            Log.d(TAG, "Comparing with LastHeader: Y=$lastHeaderYear, M=$lastHeaderMonth, D=$lastHeaderDay")

            if (currentTransactionYear != lastHeaderYear ||
                currentTransactionMonth != lastHeaderMonth ||
                currentTransactionDay != lastHeaderDay
            ) {
                val currentDateHeaderString = headerFormatter.format(calendar.time)
                Log.i(TAG, "Date change detected! Adding header: '$currentDateHeaderString'")
                newDisplayItems.add(AdapterItemFB.DateHeaderItem(currentDateHeaderString))
                lastHeaderYear = currentTransactionYear
                lastHeaderMonth = currentTransactionMonth
                lastHeaderDay = currentTransactionDay
                Log.d(TAG, "Updated lastHeaderDate: Y=$lastHeaderYear, M=$lastHeaderMonth, D=$lastHeaderDay")
            } else {
                Log.d(TAG, "No date change for this transaction.")
            }
            newDisplayItems.add(AdapterItemFB.TransactionItem(transaction))
        }

        Log.d(TAG, "Finished processing transactions. New display items count: ${newDisplayItems.size}")
        displayItems.clear()
        displayItems.addAll(newDisplayItems)
        notifyDataSetChanged()
        Log.d(TAG, "Data set changed notified.")
    }

    fun updateData(newTransactions: List<TransactionInfo>) {
        processAndUpdateData(newTransactions)
    }
}
