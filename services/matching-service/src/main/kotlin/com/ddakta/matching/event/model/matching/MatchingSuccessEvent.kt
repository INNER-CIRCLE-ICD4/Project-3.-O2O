package com.ddakta.matching.event.model.matching

import java.time.LocalDateTime
import java.util.*

data class MatchingSuccessEvent(
    val rideId: UUID,
    val passengerId: UUID,
    val matchedDriverId: UUID,
    val matchingScore: Double,
    val estimatedArrivalSeconds: Int,
    val matchedAt: LocalDateTime
)