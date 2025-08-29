package org.fossify.messages.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.fossify.messages.databinding.ItemUserReceivedBillBinding
import org.fossify.messages.models.UserReceivedBill

class UserReceivedBillAdapter : ListAdapter<UserReceivedBill, UserReceivedBillAdapter.UserReceivedBillViewHolder>(UserReceivedBillDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserReceivedBillViewHolder {
        val binding = ItemUserReceivedBillBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserReceivedBillViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserReceivedBillViewHolder, position: Int) {
        val bill = getItem(position)
        holder.bind(bill)
    }

    inner class UserReceivedBillViewHolder(private val binding: ItemUserReceivedBillBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(bill: UserReceivedBill) {
            binding.textViewReceivedBillDescription.text = bill.description
            binding.textViewReceivedCollectedAmount.text = "Collected: ${bill.collectedAmount}"
            binding.textViewReceivedCollectedDate.text = "Collected On: ${bill.collectedDate}"
            binding.textViewReceivedOriginalAmount.text = "Original Amount: ${bill.transactionAmount}"
            binding.textViewReceivedDueDate.text = "Original Due Date: ${bill.dueDate}"
            binding.textViewReceivedNotes.text = "Notes: ${bill.notes}"
            binding.textViewReceivedBillTag.text = "Tag: ${bill.billTag}"
        }
    }

    private class UserReceivedBillDiffCallback : DiffUtil.ItemCallback<UserReceivedBill>() {
        override fun areItemsTheSame(oldItem: UserReceivedBill, newItem: UserReceivedBill): Boolean {
            return oldItem.collectionTransactionUid == newItem.collectionTransactionUid
        }

        override fun areContentsTheSame(oldItem: UserReceivedBill, newItem: UserReceivedBill): Boolean {
            return oldItem == newItem
        }
    }
}
