package org.fossify.messages.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.fossify.messages.R
import org.fossify.messages.models.GPayTransactionInfo
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

// Sealed class for different item types in the RecyclerView
sealed class GPayAdapterItemFB {
    data class GPayTransactionItem(val transaction: GPayTransactionInfo) : GPayAdapterItemFB()
    data class GPayDateHeaderItem(val dateString: String) : GPayAdapterItemFB()
}

class GPayTransactionsAdapterFB(initialTransactions: List<GPayTransactionInfo>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var displayItems: MutableList<GPayAdapterItemFB> = mutableListOf()

    companion object {
        private const val TYPE_GPAY_TRANSACTION = 0
        private const val TYPE_GPAY_DATE_HEADER = 1
        // Date format for parsing the firebaseDateKey (YYYY-MM-DD)
        private val firebaseDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        // Date format for displaying the header (e.g., "Monday, 21 Jul 2025")
        private val displayHeaderFormat = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.ENGLISH)
    }

    init {
        processAndUpdateData(initialTransactions)
    }

    // ViewHolder for GPay Transaction Items
    class GPayTransactionFBViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.gpay_transaction_fb_name)
        val netAmountTextView: TextView = itemView.findViewById(R.id.gpay_transaction_fb_net_amount)
        val creationTimeTextView: TextView = itemView.findViewById(R.id.gpay_transaction_fb_creation_time)
        val statusTextView: TextView = itemView.findViewById(R.id.gpay_transaction_fb_status)

        // Detail fields
        val detailsLayout: LinearLayout = itemView.findViewById(R.id.gpay_transaction_fb_details_layout)
        val idTextView: TextView = itemView.findViewById(R.id.gpay_transaction_fb_id)
        val amountTextView: TextView = itemView.findViewById(R.id.gpay_transaction_fb_amount)
        val paymentFeeTextView: TextView = itemView.findViewById(R.id.gpay_transaction_fb_payment_fee)
        val paymentSourceTextView: TextView = itemView.findViewById(R.id.gpay_transaction_fb_payment_source)
        val typeTextView: TextView = itemView.findViewById(R.id.gpay_transaction_fb_type)
        val updateTimeTextView: TextView = itemView.findViewById(R.id.gpay_transaction_fb_update_time)
        val notesTextView: TextView = itemView.findViewById(R.id.gpay_transaction_fb_notes)
    }

    // ViewHolder for Date Header Items
    class GPayDateHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dateHeaderTextView: TextView = itemView.findViewById(R.id.date_header_text_fb) // Assuming same ID from item_date_header_fb.xml
    }

    override fun getItemViewType(position: Int): Int {
        return when (displayItems[position]) {
            is GPayAdapterItemFB.GPayTransactionItem -> TYPE_GPAY_TRANSACTION
            is GPayAdapterItemFB.GPayDateHeaderItem -> TYPE_GPAY_DATE_HEADER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_GPAY_TRANSACTION -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_gpay_transaction_fb, parent, false)
                GPayTransactionFBViewHolder(view)
            }
            TYPE_GPAY_DATE_HEADER -> {
                // Ensure you have a layout file item_date_header_fb.xml or similar for headers
                // If not, you might need to create one or use a simpler TextView programmatically.
                // For now, assuming R.layout.item_date_header_fb exists and has R.id.date_header_text_fb
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_date_header_fb, parent, false)
                GPayDateHeaderViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type: $viewType for GPayAdapter")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val currentItem = displayItems[position]) {
            is GPayAdapterItemFB.GPayTransactionItem -> {
                val transactionHolder = holder as GPayTransactionFBViewHolder
                val gpayTransaction = currentItem.transaction

                transactionHolder.nameTextView.text = gpayTransaction.name
                transactionHolder.netAmountTextView.text = gpayTransaction.netAmount // Display net amount in main view
                transactionHolder.creationTimeTextView.text = gpayTransaction.creationTime
                transactionHolder.statusTextView.text = gpayTransaction.status

                // Bind details
                transactionHolder.idTextView.text = gpayTransaction.id
                transactionHolder.amountTextView.text = gpayTransaction.amount
                transactionHolder.paymentFeeTextView.text = gpayTransaction.paymentFee
                transactionHolder.paymentSourceTextView.text = gpayTransaction.paymentSource
                transactionHolder.typeTextView.text = gpayTransaction.type
                transactionHolder.updateTimeTextView.text = gpayTransaction.updateTime
                transactionHolder.notesTextView.text = if(gpayTransaction.notes.isNullOrEmpty()) "-" else gpayTransaction.notes

                // Handle click to expand/collapse details
                transactionHolder.itemView.setOnClickListener {
                    val isVisible = transactionHolder.detailsLayout.visibility == View.VISIBLE
                    transactionHolder.detailsLayout.visibility = if (isVisible) View.GONE else View.VISIBLE
                }
                // Ensure details are hidden by default when view is recycled
                transactionHolder.detailsLayout.visibility = View.GONE

            }
            is GPayAdapterItemFB.GPayDateHeaderItem -> {
                val dateHeaderHolder = holder as GPayDateHeaderViewHolder
                dateHeaderHolder.dateHeaderTextView.text = currentItem.dateString
            }
        }
    }

    override fun getItemCount() = displayItems.size

    private fun processAndUpdateData(newTransactions: List<GPayTransactionInfo>) {
        val newDisplayItems = mutableListOf<GPayAdapterItemFB>()
        if (newTransactions.isEmpty()) {
            displayItems.clear()
            notifyDataSetChanged()
            return
        }

        // Transactions are expected to be pre-sorted by GPayTransactionsInFBActivity
        // (by firebaseDateKey desc, then creationTime desc)

        var lastHeaderDisplayDate: String? = null

        for (transaction in newTransactions) {
            try {
                val parsedDate: Date? = firebaseDateFormat.parse(transaction.creationTime)
                val currentHeaderDisplayDate = parsedDate?.let { displayHeaderFormat.format(it) } ?: transaction.creationTime

                if (currentHeaderDisplayDate != lastHeaderDisplayDate) {
                    newDisplayItems.add(GPayAdapterItemFB.GPayDateHeaderItem(currentHeaderDisplayDate))
                    lastHeaderDisplayDate = currentHeaderDisplayDate
                }
                newDisplayItems.add(GPayAdapterItemFB.GPayTransactionItem(transaction))
            } catch (e: Exception) {
                // Log error or handle malformed firebaseDateKey
                // For now, add transaction without a header if date parsing fails
                newDisplayItems.add(GPayAdapterItemFB.GPayTransactionItem(transaction))
            }
        }

        displayItems.clear()
        displayItems.addAll(newDisplayItems)
        notifyDataSetChanged()
    }

    fun updateData(newTransactions: List<GPayTransactionInfo>) {
        processAndUpdateData(newTransactions)
    }
}
