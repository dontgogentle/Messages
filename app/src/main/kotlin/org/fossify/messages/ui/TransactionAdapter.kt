package org.fossify.messages.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.fossify.messages.R
import java.text.SimpleDateFormat
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

        // Date Header Logic
        val showDateHeader = if (position == 0) {
            true // Always show header for the first item
        } else {
            try {
                val currentDate = dateFormat.parse(transactionInfo.date)
                val prevDate = dateFormat.parse(transactions[position - 1].date)
                currentDate != prevDate
            } catch (e: Exception) {
                // Fallback if date parsing fails, compare raw strings
                transactionInfo.date != transactions[position - 1].date
            }
        }

        if (showDateHeader) {
            holder.dateSectionHeader.visibility = View.VISIBLE
            holder.dateSectionHeader.text = transactionInfo.date // Or a more formatted version
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
        holder.date.text = transactionInfo.date // Date on the card itself

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
        // Sort by date - descending for newest first overall, then headers will naturally group
        // It's crucial that dates like "12-Aug-25" can be parsed and compared correctly.
        val sortedTransactions = newTransactions.sortedWith(compareByDescending {
            try {
                dateFormat.parse(it.date)
            } catch (e: Exception) {
                null // Handle parsing error, maybe log or treat as oldest
            }
        })
        transactions.addAll(sortedTransactions)
        notifyDataSetChanged()
    }
}
