package org.fossify.messages.ui

import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.fossify.messages.R
import org.fossify.messages.models.TransactionInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionAdapter(private var transactions: MutableList<TransactionInfo>) :
    RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    // Date formatter for sorting and display if needed for consistency
    // Input format is "dd-MMM-yy", e.g., "12-Aug-25"
    private val dateFormat = SimpleDateFormat("dd-MMM-yy", Locale.ENGLISH)

    class TransactionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view.findViewById(R.id.transactionCard)
        val name: TextView = view.findViewById(R.id.nameText)
        val transactionType: TextView = view.findViewById(R.id.transactionType)
        val amount: TextView = view.findViewById(R.id.amountText)
        val date: TextView = view.findViewById(R.id.dateText) // This will show date on card
        val account: TextView = view.findViewById(R.id.accountText)
        val transactionReference: TextView = view.findViewById(R.id.transactionReference)
        val upi: TextView = view.findViewById(R.id.upiText)
        val accountBalance: TextView = view.findViewById(R.id.accountBalance)
        val receivedFrom: TextView = view.findViewById(R.id.receivedFrom)
        val transferredTo: TextView = view.findViewById(R.id.transferredTo)
        val rawMessage: TextView = view.findViewById(R.id.rawMessage)
        val rawMessageToggle: TextView = view.findViewById(R.id.rawMessageToggle)
        val dateSectionHeader: TextView = view.findViewById(R.id.dateSectionHeader)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transactionInfo = transactions[position]
        val context = holder.itemView.context


        val showDateHeader = if (position == 0) {
            true // Always show header for the first item
        } else {
            val currentTransactionStringDate = transactionInfo.strDateInMessage
            val prevTransactionStringDate = transactions[position - 1].strDateInMessage

            if (currentTransactionStringDate != null && prevTransactionStringDate != null) {
                try {
                    val currentDateParsed = dateFormat.parse(currentTransactionStringDate) // Use String date
                    val prevDateParsed = dateFormat.parse(prevTransactionStringDate)     // Use String date
                    currentDateParsed != prevDateParsed
                } catch (e: Exception) {
                    // Fallback to raw string comparison if parsing fails for valid strings
                    currentTransactionStringDate != prevTransactionStringDate
                }
            } else {
                // If either string date is null, or both are, compare them directly.
                // This means if one is null and the other isn't, they are different (show header).
                // If both are null, they are effectively the same for grouping (don't show header).
                currentTransactionStringDate != prevTransactionStringDate
            }
        }

        if (showDateHeader) {
            holder.dateSectionHeader.visibility = View.VISIBLE
            // Display the strDateInMessage in the header
            holder.dateSectionHeader.text = transactionInfo.strDateInMessage ?: "Unknown Date"
        } else {
            holder.dateSectionHeader.visibility = View.GONE
        }

        // Background color
        if (transactionInfo.transactionType.equals("CREDIT", ignoreCase = true)) {
            holder.card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.transaction_credit_background))
            holder.amount.setTextColor(ContextCompat.getColor(context, R.color.text_color_primary_on_light_bg))
        } else if (transactionInfo.transactionType.equals("DEBIT", ignoreCase = true)) {
            holder.card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.transaction_debit_background))
            holder.amount.setTextColor(ContextCompat.getColor(context, R.color.text_color_primary_on_light_bg))
        } else {
            holder.card.setCardBackgroundColor(Color.WHITE) // Default
            holder.amount.setTextColor(ContextCompat.getColor(context, R.color.text_color_primary_on_light_bg))
        }

        holder.name.text = transactionInfo.name ?: "N/A"
        holder.name.visibility = if (transactionInfo.name.isNullOrEmpty()) View.GONE else View.VISIBLE
        
        holder.transactionType.text = transactionInfo.transactionType
        holder.amount.text = "Rs. ${transactionInfo.amount}"
        holder.date.text = transactionInfo.strDateInMessage // Date on the card itself

        fun setupField(textView: TextView, label: String, value: String?) {
            if (!value.isNullOrEmpty()) {
                textView.text = "$label: $value"
                textView.visibility = View.VISIBLE
            } else {
                textView.visibility = View.GONE
            }
        }

        setupField(holder.account, "Account", transactionInfo.account)
        setupField(holder.transactionReference, "Ref", transactionInfo.transactionReference)
        setupField(holder.upi, "UPI", transactionInfo.upi)
        setupField(holder.accountBalance, "Balance", transactionInfo.accountBalance?.let { "Rs. $it" })
        setupField(holder.receivedFrom, "From", transactionInfo.receivedFrom)
        setupField(holder.transferredTo, "To", transactionInfo.transferredTo)

        // Raw Message Toggle
        holder.rawMessage.text = transactionInfo.raw
        if (transactionInfo.isRawExpanded) {
            holder.rawMessage.visibility = View.VISIBLE
            holder.rawMessageToggle.text = "Hide Raw Message"
        } else {
            holder.rawMessage.visibility = View.GONE
            holder.rawMessageToggle.text = "Show Raw Message"
        }

        holder.rawMessageToggle.setOnClickListener {
            transactionInfo.isRawExpanded = !transactionInfo.isRawExpanded
            notifyItemChanged(position) // Refresh this item
        }
    }

    override fun getItemCount(): Int = transactions.size

    fun updateData(newTransactions: List<TransactionInfo>) {
        transactions.clear()
        val sortedTransactions = newTransactions.sortedWith(compareByDescending { transaction ->
            transaction.strDateInMessage?.let { dateStr -> // Use strDateInMessage
                try {
                    dateFormat.parse(dateStr)
                } catch (e: Exception) {
                    Log.w("TransactionAdapter", "Failed to parse date string for sorting: $dateStr", e)
                    null // Treat as oldest if parsing fails
                }
            } ?: Date(0) // Treat null strDateInMessage as oldest (or Date(Long.MIN_VALUE))
        })
        transactions.addAll(sortedTransactions)
        notifyDataSetChanged()
    }
}
