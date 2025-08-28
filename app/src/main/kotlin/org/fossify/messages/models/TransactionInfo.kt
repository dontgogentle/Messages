package org.fossify.messages.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// Created from TransactionActivity.kt

@Parcelize
data class TransactionInfo(
    var id: String? = null,
    var strDateInMessage: String? = "", // For "dd-MMM-yy" string from SMS, was 'date'
    var date: Int,             // For epoch timestamp
    val account: String = "",
    val transactionType: String = "", // "CREDIT" or "DEBIT"
    val amount: String = "",
    val transactionReference: String = "",
    val raw: String = "",
    val upi: String? = null, // Not all transactions have UPI
    val name: String? = null, // For "Received From" or "Transferred To"
    val accountBalance: String? = null,
    val receivedFrom: String? = null,
    val transferredTo: String? = null,
    var isRawExpanded: Boolean = false // Added for collapsible raw message
) : Parcelable
