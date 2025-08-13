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

class TransactionAdapter(private val items: List<TransactionInfo>) :
    RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    class TransactionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view.findViewById(R.id.transactionCard)
        val name: TextView = view.findViewById(R.id.nameText)
        val transactionType: TextView = view.findViewById(R.id.transactionType)
        val amount: TextView = view.findViewById(R.id.amountText)
        val date: TextView = view.findViewById(R.id.dateText)
        val account: TextView = view.findViewById(R.id.accountText)
        val transactionReference: TextView = view.findViewById(R.id.transactionReference)
        val upi: TextView = view.findViewById(R.id.upiText)
        val accountBalance: TextView = view.findViewById(R.id.accountBalance)
        val receivedFrom: TextView = view.findViewById(R.id.receivedFrom)
        val transferredTo: TextView = view.findViewById(R.id.transferredTo)
        val rawMessage: TextView = view.findViewById(R.id.rawMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        // Set background color based on transaction type
        if (item.transactionType.equals("CREDIT", ignoreCase = true)) {
            holder.card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.transaction_credit_background))
            holder.amount.setTextColor(Color.parseColor("#1B5E20")) // Dark Green for credit amount
        } else if (item.transactionType.equals("DEBIT", ignoreCase = true)) {
            holder.card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.transaction_debit_background))
            holder.amount.setTextColor(Color.parseColor("#B71C1C")) // Dark Red for debit amount
        } else {
            holder.card.setCardBackgroundColor(Color.WHITE) // Default
            holder.amount.setTextColor(Color.BLACK)
        }

        // Name (Primary contact for the transaction)
        holder.name.text = item.name
        holder.name.visibility = if (item.name.isNullOrEmpty()) View.GONE else View.VISIBLE

        // Transaction Type
        holder.transactionType.text = "Type: ${item.transactionType}"
        holder.transactionType.visibility = View.VISIBLE

        // Amount
        holder.amount.text = "Amount: Rs. ${item.amount}"
        holder.amount.visibility = View.VISIBLE

        // Date
        holder.date.text = "Date: ${item.date}"
        holder.date.visibility = View.VISIBLE

        // Account
        holder.account.text = "Account: ${item.account}"
        holder.account.visibility = View.VISIBLE

        // Transaction Reference
        if (!item.transactionReference.isNullOrEmpty()) {
            holder.transactionReference.text = "Ref: ${item.transactionReference}"
            holder.transactionReference.visibility = View.VISIBLE
        } else {
            holder.transactionReference.visibility = View.GONE
        }

        // UPI
        if (!item.upi.isNullOrEmpty()) {
            holder.upi.text = "UPI: ${item.upi}"
            holder.upi.visibility = View.VISIBLE
        } else {
            holder.upi.visibility = View.GONE
        }

        // Account Balance
        if (!item.accountBalance.isNullOrEmpty()) {
            holder.accountBalance.text = "Balance: Rs. ${item.accountBalance}"
            holder.accountBalance.visibility = View.VISIBLE
        } else {
            holder.accountBalance.visibility = View.GONE
        }

        // Received From
        if (!item.receivedFrom.isNullOrEmpty()) {
            holder.receivedFrom.text = "From: ${item.receivedFrom}"
            holder.receivedFrom.visibility = View.VISIBLE
        } else {
            holder.receivedFrom.visibility = View.GONE
        }

        // Transferred To
        if (!item.transferredTo.isNullOrEmpty()) {
            holder.transferredTo.text = "To: ${item.transferredTo}"
            holder.transferredTo.visibility = View.VISIBLE
        } else {
            holder.transferredTo.visibility = View.GONE
        }

        // Raw Message (always show for debugging, or conditionally)
        holder.rawMessage.text = "Raw: ${item.raw}"
        holder.rawMessage.visibility = View.VISIBLE // Or make conditional
    }

    override fun getItemCount(): Int = items.size
}
