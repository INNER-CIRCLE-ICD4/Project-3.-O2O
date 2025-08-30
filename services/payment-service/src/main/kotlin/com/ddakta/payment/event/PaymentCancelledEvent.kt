package com.ddakta.payment.event

import java.util.UUID

data class PaymentCancelledEvent(
    val matchId: UUID,
    val userId: String,
    val reason: String
)
