package org.fossify.messages.models

data class GPayTransactionInfo(
    val id: String = "",
    val name: String = "",
    val paymentSource: String = "",
    val creationTime: String = "",
    val amount: String = "",
    val paymentFee: String = "",
    val netAmount: String = "",
    val status: String = "",
    val updateTime: String = "",
    val notes: String = "",
    val creationTimestamp: Int = 0 // Keeping as Int as per original extraction
)
