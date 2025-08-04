package com.ddakta.payment.event

data class PaymentSuccessEvent(
    val matchId: Long,
    val userId: String,
    val amount: Int
)
