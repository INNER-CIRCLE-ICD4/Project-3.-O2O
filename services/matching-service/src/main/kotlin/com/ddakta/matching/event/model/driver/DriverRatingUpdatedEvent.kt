package com.ddakta.matching.event.model.driver

import java.time.LocalDateTime
import java.util.*

data class DriverRatingUpdatedEvent(
    val driverId: UUID,
    val rideId: UUID,
    val rating: Int,
    val previousAverage: Double,
    val newAverage: Double,
    val totalRatings: Int,
    val updatedAt: LocalDateTime
)