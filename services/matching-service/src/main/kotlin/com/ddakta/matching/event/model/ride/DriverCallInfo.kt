package com.ddakta.matching.event.model.ride

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

data class DriverCallInfo(
    val callId: UUID,
    val driverId: UUID,
    val expiresAt: LocalDateTime,
    val estimatedArrival: Int?,
    val estimatedFare: BigDecimal?
)