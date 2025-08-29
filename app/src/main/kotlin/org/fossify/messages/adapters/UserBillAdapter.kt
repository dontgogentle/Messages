package org.fossify.messages.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.fossify.messages.databinding.ItemUserBillBinding
import org.fossify.messages.models.UserBill

class UserBillAdapter : ListAdapter<UserBill, UserBillAdapter.UserBillViewHolder>(UserBillDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserBillViewHolder {
        val binding = ItemUserBillBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserBillViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserBillViewHolder, position: Int) {
        val bill = getItem(position)
        holder.bind(bill)
    }

    inner class UserBillViewHolder(private val binding: ItemUserBillBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(bill: UserBill) {
            binding.textViewBillDescription.text = bill.description
            binding.textViewBillAmount.text = "Amount: ${bill.transactionAmount}"
            binding.textViewBillDueDate.text = "Due: ${bill.dueDate}"
            binding.textViewBillTag.text = "Tag: ${bill.billTag}"
        }
    }

    private class UserBillDiffCallback : DiffUtil.ItemCallback<UserBill>() {
        override fun areItemsTheSame(oldItem: UserBill, newItem: UserBill): Boolean {
            return oldItem.uid == newItem.uid // Assuming uid is unique for each bill
        }

        override fun areContentsTheSame(oldItem: UserBill, newItem: UserBill): Boolean {
            return oldItem == newItem
        }
    }
}
