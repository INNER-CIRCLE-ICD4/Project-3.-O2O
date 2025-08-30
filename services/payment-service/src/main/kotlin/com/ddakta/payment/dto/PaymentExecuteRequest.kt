package com.ddakta.payment.dto

data class PaymentExecuteRequest(
    val matchId: Long,
    val userId: String,
    val amount: Long,
    val payMethod: String
)
