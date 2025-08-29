package org.fossify.messages.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class UserReceivedBill(
    val billTag: String = "",
    val collectedAmount: Long = 0,
    val collectedDate: String = "",
    val collectionTransactionUid: String = "",
    val description: String = "",
    val dueDate: String = "",
    val notes: String = "",
    val transactionAmount: Long = 0L,
    val uid: String = ""
) : Parcelable
