package com.ddakta.matching.event.model.payment

import java.time.LocalDateTime
import java.util.*

data class PaymentFailedEvent(
    val paymentId: UUID,
    val rideId: UUID,
    val passengerId: UUID,
    val reason: String,
    val failedAt: LocalDateTime,
    val retryable: Boolean
)