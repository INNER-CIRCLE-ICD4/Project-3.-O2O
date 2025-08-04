package com.ddakta.matching.event.model.matching

import java.time.LocalDateTime
import java.util.*

data class MatchingRequestCreatedEvent(
    val requestId: UUID,
    val rideId: UUID,
    val passengerId: UUID,
    val pickupH3Index: String,
    val dropoffH3Index: String,
    val surgeMultiplier: Double,
    val requestedAt: LocalDateTime,
    val expiresAt: LocalDateTime
)