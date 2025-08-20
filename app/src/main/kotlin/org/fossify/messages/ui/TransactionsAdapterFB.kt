package org.fossify.messages.ui

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.fossify.messages.R
import org.fossify.messages.ui.TransactionInfo // Ensure this is the correct TransactionInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
// import java.util.TimeZone // If your server timestamps are UTC and you want to display in local, consider this

class TransactionsAdapterFB(initialTransactions: List<TransactionInfo>) : // Constructor param changed
    RecyclerView.Adapter<TransactionsAdapterFB.TransactionFBViewHolder>() {

    // Key change: 'var' and 'MutableList', initialized from constructor param
    private var transactions: MutableList<TransactionInfo> = initialTransactions.toMutableList()

    class TransactionFBViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val descriptionOrNameTextView: TextView = itemView.findViewById(R.id.transaction_fb_description_or_name)
        val amountTextView: TextView = itemView.findViewById(R.id.transaction_fb_amount)
        val dateTextView: TextView = itemView.findViewById(R.id.transaction_fb_date)
        val typeLabelTextView: TextView = itemView.findViewById(R.id.transaction_fb_type_label) // New
        val accountDetailsTextView: TextView = itemView.findViewById(R.id.transaction_fb_account_details)
        val referenceDetailsTextView: TextView = itemView.findViewById(R.id.transaction_fb_reference_details)
        val upiDetailsTextView: TextView = itemView.findViewById(R.id.transaction_fb_upi_details)
        val balanceDetailsTextView: TextView = itemView.findViewById(R.id.transaction_fb_balance_details)
        val rawDataTextView: TextView = itemView.findViewById(R.id.transaction_fb_raw_data)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionFBViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction_fb, parent, false)
        return TransactionFBViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionFBViewHolder, position: Int) {
        val transaction = transactions[position]
        val context = holder.itemView.context

        // Row 1: Name and Amount
        var primaryIdentifier = transaction.name
        if (primaryIdentifier.isNullOrEmpty()) {
            holder.descriptionOrNameTextView.text = ""
        } else {
            holder.descriptionOrNameTextView.text = primaryIdentifier
        }
        holder.amountTextView.text = transaction.amount

        // Row 2: Date (Left)
        val displayDate = if (!transaction.strDateInMessage.isNullOrEmpty()) {
            transaction.strDateInMessage
        } else if (transaction.date != null) {
            SimpleDateFormat("dd MMM yy, hh:mm a", Locale.getDefault()).format(Date(transaction.date!!))
        } else {
            "No Date"
        }
        holder.dateTextView.text = displayDate
        holder.dateTextView.setTypeface(null, Typeface.NORMAL)

        // Row 2: Type Label (Right)
        val typeText = when (transaction.transactionType.lowercase(Locale.getDefault())) {
            "credit" -> "CREDIT"
            "debit" -> "DEBIT"
            else -> transaction.transactionType.uppercase(Locale.getDefault()) // Fallback for other types or hide
        }
        if (typeText.isNotEmpty() && (typeText == "CREDIT" || typeText == "DEBIT")) {
            holder.typeLabelTextView.text = typeText
            holder.typeLabelTextView.visibility = View.VISIBLE
            // Text color for credit/debit can be set here if needed, e.g.
            // if (typeText == "CREDIT") {
            // holder.typeLabelTextView.setTextColor(ContextCompat.getColor(context, R.color.your_credit_text_color))
            // } else {
            // holder.typeLabelTextView.setTextColor(ContextCompat.getColor(context, R.color.your_debit_text_color))
            // }
        } else {
            holder.typeLabelTextView.visibility = View.GONE // Hide if not credit/debit or empty
        }
        // Boldness is handled by XML, but can be overridden:
        // holder.typeLabelTextView.setTypeface(null, Typeface.BOLD) // If XML didn't set it

        // Account Details
        if (!transaction.account.isNullOrEmpty()) {
            holder.accountDetailsTextView.text = "Account: ${transaction.account}"
            holder.accountDetailsTextView.visibility = View.VISIBLE
        } else {
            holder.accountDetailsTextView.visibility = View.GONE
        }

        // Reference Details
        if (!transaction.transactionReference.isNullOrEmpty()) {
            holder.referenceDetailsTextView.text = "Ref: ${transaction.transactionReference}"
            holder.referenceDetailsTextView.visibility = View.VISIBLE
        } else {
            holder.referenceDetailsTextView.visibility = View.GONE
        }

        // UPI Details
        if (!transaction.upi.isNullOrEmpty()) {
            holder.upiDetailsTextView.text = "UPI: ${transaction.upi}"
            holder.upiDetailsTextView.visibility = View.VISIBLE
        } else {
            holder.upiDetailsTextView.visibility = View.GONE
        }

        // Balance Details
        if (!transaction.accountBalance.isNullOrEmpty()) {
            holder.balanceDetailsTextView.text = "Balance: ${transaction.accountBalance}"
            holder.balanceDetailsTextView.visibility = View.VISIBLE
        } else {
            holder.balanceDetailsTextView.visibility = View.GONE
        }

        // Background color
        val backgroundColor = when (transaction.transactionType.lowercase(Locale.getDefault())) {
            "credit" -> ContextCompat.getColor(context, R.color.transaction_credit_background)
            "debit" -> ContextCompat.getColor(context, R.color.transaction_debit_background)
            else -> ContextCompat.getColor(context, R.color.default_transaction_background)
        }
        if (holder.itemView is CardView) {
            (holder.itemView as CardView).setCardBackgroundColor(backgroundColor)
        }

        // Raw data display logic
        holder.rawDataTextView.text = transaction.raw
        holder.itemView.setOnClickListener {
            transaction.isRawExpanded = !transaction.isRawExpanded
            holder.rawDataTextView.visibility = if (transaction.isRawExpanded) View.VISIBLE else View.GONE
        }
        holder.rawDataTextView.visibility = if (transaction.isRawExpanded) View.VISIBLE else View.GONE
    }

    override fun getItemCount() = transactions.size

    fun updateData(newTransactions: List<TransactionInfo>) {
        this.transactions.clear() // This will now work
        this.transactions.addAll(newTransactions) // This will also work
        this.transactions.sortByDescending { it.date ?: 0L } // Sort by date, newest first
        notifyDataSetChanged()
    }
}
