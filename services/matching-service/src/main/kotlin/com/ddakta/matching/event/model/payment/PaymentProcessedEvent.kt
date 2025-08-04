package com.ddakta.matching.event.model.payment

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

data class PaymentProcessedEvent(
    val paymentId: UUID,
    val rideId: UUID,
    val passengerId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val paymentMethod: String,
    val processedAt: LocalDateTime
)