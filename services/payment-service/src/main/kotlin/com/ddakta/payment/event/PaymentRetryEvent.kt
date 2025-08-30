package com.ddakta.payment.event

data class PaymentRetryEvent(
    val paymentId: String,
    val userId: String,
    val amount: Int,
    val paymentMethodId: String
)
