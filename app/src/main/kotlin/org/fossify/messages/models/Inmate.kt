package org.fossify.messages.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Inmate(
    val id: String = "", // Unique ID from Firebase key
    val name: String = "",
    val doj: String = "", // Date of Joining
    val phone: String = "",
    val rent: Int = 0,    // Changed to Int, default to 0
    val color: String = "", // For status like "red" if overdue
    val advance: Int = 0,
    val balance: Int = 0,
    val collectedAdvance: Int = 0
) : Parcelable, RoomDisplayItem
