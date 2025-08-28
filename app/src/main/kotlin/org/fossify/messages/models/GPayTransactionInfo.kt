package org.fossify.messages.models

// Created from GPayTransactionsInFBActivity.kt

data class GPayTransactionInfo(
    val id: String = "", // Transaction ID
    val name: String = "", // Name
    val paymentSource: String = "", // Payment Source
    val type: String = "UPI", // Type (defaulted to UPI as per CSV)
    val creationTime: String = "", // creation time string from CSV (e.g., "21-07-2025 17:35:44")
    val amount: String = "", // Amount
    val paymentFee: String = "", // Payment Fee
    val netAmount: String = "", // Net Amount
    val status: String = "", // Status
    val updateTime: String = "", // Update time
    val notes: String = "", // Notes
    val creationTimestamp: Int = 0 // Parsed timestamp from creationTime string
)
