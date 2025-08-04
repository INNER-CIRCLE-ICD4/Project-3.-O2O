package com.ddakta.matching.event.model.matching

import java.time.LocalDateTime
import java.util.*

data class MatchingRequestExpiredEvent(
    val requestId: UUID,
    val rideId: UUID,
    val passengerId: UUID,
    val expiredAt: LocalDateTime,
    val retryCount: Int,
    val lastNoDriverAt: LocalDateTime?
)