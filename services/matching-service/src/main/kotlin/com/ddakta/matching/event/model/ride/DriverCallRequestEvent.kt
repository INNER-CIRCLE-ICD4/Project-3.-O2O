package com.ddakta.matching.event.model.ride

import java.time.LocalDateTime
import java.util.*

data class DriverCallRequestEvent(
    val rideId: UUID,
    val drivers: List<DriverCallInfo>,
    val requestedAt: LocalDateTime
)