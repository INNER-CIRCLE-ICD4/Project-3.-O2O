package com.ddakta.matching.event.model.ride

import java.time.LocalDateTime
import java.util.*

data class RideStatusChangedEvent(
    val rideId: UUID,
    val passengerId: UUID,
    val driverId: UUID?,
    val previousStatus: String,
    val newStatus: String,
    val changedAt: LocalDateTime,
    val metadata: Map<String, Any>?
)