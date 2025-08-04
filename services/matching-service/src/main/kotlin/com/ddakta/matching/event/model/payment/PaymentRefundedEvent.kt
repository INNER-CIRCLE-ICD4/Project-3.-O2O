package com.ddakta.matching.event.model.payment

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

data class PaymentRefundedEvent(
    val paymentId: UUID,
    val rideId: UUID,
    val passengerId: UUID,
    val amount: BigDecimal,
    val reason: String,
    val refundedAt: LocalDateTime
)