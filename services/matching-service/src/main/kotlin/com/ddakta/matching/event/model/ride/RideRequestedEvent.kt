package com.ddakta.matching.event.model.ride

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

data class RideRequestedEvent(
    val rideId: UUID,
    val passengerId: UUID,
    val pickupLocation: EventLocationDto,
    val dropoffLocation: EventLocationDto,
    val pickupH3: String,
    val requestedAt: LocalDateTime,
    val estimatedFare: BigDecimal?,
    val surgeMultiplier: Double
)