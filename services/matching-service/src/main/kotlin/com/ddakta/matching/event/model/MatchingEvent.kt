package com.ddakta.matching.event.model

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

data class MatchingSuccessEvent(
    val rideId: UUID,
    val passengerId: UUID,
    val matchedDriverId: UUID,
    val matchingScore: Double,
    val estimatedArrivalSeconds: Int,
    val matchedAt: LocalDateTime
)

data class MatchingRequestExpiredEvent(
    val requestId: UUID,
    val rideId: UUID,
    val passengerId: UUID,
    val expiredAt: LocalDateTime,
    val retryCount: Int,
    val lastNoDriverAt: LocalDateTime?
)