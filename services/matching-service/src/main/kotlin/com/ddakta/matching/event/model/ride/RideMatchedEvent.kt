package com.ddakta.matching.event.model.ride

import java.time.LocalDateTime
import java.util.*

data class RideMatchedEvent(
    val rideId: UUID,
    val passengerId: UUID,
    val driverId: UUID,
    val matchedAt: LocalDateTime,
    val estimatedArrivalSeconds: Int?,
    val driverLocation: EventLocationDto?
)