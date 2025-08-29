package org.fossify.messages.adapters

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.fossify.messages.databinding.ItemInmateBinding
import org.fossify.messages.databinding.ItemRoomHeaderBinding
import org.fossify.messages.models.Inmate
import org.fossify.messages.models.RoomDisplayItem
import org.fossify.messages.models.RoomHeader
import android.graphics.Typeface
import android.util.Log
import android.widget.Toast

class InmateAdapter : ListAdapter<RoomDisplayItem, RecyclerView.ViewHolder>(RoomDisplayItemDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_INMATE = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is RoomHeader -> VIEW_TYPE_HEADER
            is Inmate -> VIEW_TYPE_INMATE
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val binding = ItemRoomHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                RoomHeaderViewHolder(binding)
            }
            VIEW_TYPE_INMATE -> {
                val binding = ItemInmateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                InmateViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is RoomHeaderViewHolder -> {
                val roomHeader = getItem(position) as RoomHeader
                holder.bind(roomHeader)
            }
            is InmateViewHolder -> {
                val inmate = getItem(position) as Inmate
                holder.bind(inmate)
            }
        }
    }

    inner class InmateViewHolder(private val binding: ItemInmateBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(inmate: Inmate) {
            binding.textViewInmateName.text = inmate.name
            binding.textViewInmateDoj.text = "DOJ: ${inmate.doj}"
            binding.textViewInmatePhone.text = inmate.phone // Set raw phone number
            binding.textViewInmateRent.text = "Rent: ${inmate.rent}"

            var C1 = false
            try {
                if (!inmate.color.isNullOrEmpty()) {
                    binding.root.setBackgroundColor(Color.parseColor(inmate.color))
                    // If background color is set, set text to white for contrast
                    binding.textViewInmateName.setTextColor(Color.WHITE)
                    binding.textViewInmateDoj.setTextColor(Color.WHITE)
                    binding.textViewInmatePhone.setTextColor(Color.WHITE)
                    binding.textViewInmateRent.setTextColor(Color.WHITE)
                    C1= true
                } else {
                    binding.root.setBackgroundColor(Color.TRANSPARENT) // Default if color is not present
                    // Reset to default text colors if no background color
                    // Assuming default colors are handled by the theme or XML definitions
                    binding.textViewInmateName.setTextColor(getDefaultTextColor(binding.textViewInmateName.context))
                    binding.textViewInmateDoj.setTextColor(getDefaultTextColor(binding.textViewInmateDoj.context))
                    binding.textViewInmatePhone.setTextColor(getDefaultTextColor(binding.textViewInmatePhone.context))
                    binding.textViewInmateRent.setTextColor(getDefaultTextColor(binding.textViewInmateRent.context))
                }
            } catch (e: IllegalArgumentException) {
                binding.root.setBackgroundColor(Color.TRANSPARENT) // Default on error
                binding.textViewInmateName.setTextColor(getDefaultTextColor(binding.textViewInmateName.context))
                binding.textViewInmateDoj.setTextColor(getDefaultTextColor(binding.textViewInmateDoj.context))
                binding.textViewInmatePhone.setTextColor(getDefaultTextColor(binding.textViewInmatePhone.context))
                binding.textViewInmateRent.setTextColor(getDefaultTextColor(binding.textViewInmateRent.context))
            }

            // Style phone number
            binding.textViewInmatePhone.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f) // Increased font size
            binding.textViewInmatePhone.setTypeface(null, Typeface.BOLD) // Bold text



            // Set OnClickListener for the phone number
            binding.textViewInmatePhone.setOnClickListener {
                val context = it.context
                val phoneNumber = inmate.phone
                Log.d("InmateAdapter", "Phone number clicked: '${inmate.phone}' for inmate: ${inmate.name}")
                if (phoneNumber.isNotEmpty()) {
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:$phoneNumber")
                    }
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                    } else {
                        // Optionally, show a Toast or log if no dialer app is available
                         Toast.makeText(context, "No app to handle this action", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Helper function to get default text color from theme (optional, adjust as needed)
        private fun getDefaultTextColor(context: android.content.Context): Int {
            val typedValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
            return typedValue.data
        }
    }


    inner class RoomHeaderViewHolder(private val binding: ItemRoomHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(roomHeader: RoomHeader) {
            binding.roomHeaderTextView.text = roomHeader.roomNumber
        }
    }

    private class RoomDisplayItemDiffCallback : DiffUtil.ItemCallback<RoomDisplayItem>() {
        override fun areItemsTheSame(oldItem: RoomDisplayItem, newItem: RoomDisplayItem): Boolean {
            return when {
                oldItem is Inmate && newItem is Inmate -> oldItem.id == newItem.id
                oldItem is RoomHeader && newItem is RoomHeader -> oldItem.roomNumber == newItem.roomNumber
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: RoomDisplayItem, newItem: RoomDisplayItem): Boolean {
            return oldItem == newItem
        }
    }
}
