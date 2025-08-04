package com.ddakta.matching.event.model.user

import java.time.LocalDateTime
import java.util.*

data class UserPaymentMethodUpdatedEvent(
    val userId: UUID,
    val paymentMethodId: String,
    val action: String, // ADDED, UPDATED, REMOVED, SET_DEFAULT
    val updatedAt: LocalDateTime
)