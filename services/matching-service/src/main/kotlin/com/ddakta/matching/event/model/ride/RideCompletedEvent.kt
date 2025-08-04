package com.ddakta.matching.event.model.ride

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

data class RideCompletedEvent(
    val rideId: UUID,
    val passengerId: UUID,
    val driverId: UUID,
    val completedAt: LocalDateTime,
    val distanceMeters: Int?,
    val durationSeconds: Int?,
    val totalFare: BigDecimal?
)