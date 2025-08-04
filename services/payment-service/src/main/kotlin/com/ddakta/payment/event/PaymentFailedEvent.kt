package com.ddakta.payment.event

data class PaymentFailedEvent(
    val matchId: Long,
    val userId: String,
    val amount: Int,
    val payMethod: String,
    val reason: String
)
