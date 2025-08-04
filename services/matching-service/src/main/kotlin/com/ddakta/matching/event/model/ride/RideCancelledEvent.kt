package com.ddakta.matching.event.model.ride

import java.time.LocalDateTime
import java.util.*

data class RideCancelledEvent(
    val rideId: UUID,
    val passengerId: UUID,
    val driverId: UUID?,
    val cancellationReason: String?,
    val cancelledBy: String?,
    val cancelledAt: LocalDateTime
)