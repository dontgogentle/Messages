package org.fossify.messages.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class UserBill(
    val billTag: String = "",
    val description: String = "",
    val dueDate: String = "",
    val transactionAmount: Long = 0L, // Using Long for currency to be safe
    val uid: String = "" // This is the bill's own unique ID
) : Parcelable
