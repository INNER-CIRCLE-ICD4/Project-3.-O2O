package com.ddakta.matching.dto.response

import java.time.LocalDateTime
import java.util.*

data class RideStatusUpdateEventDto(
    val rideId: UUID,
    val status: String,
    val driverId: UUID? = null,
    val timestamp: LocalDateTime,
    val message: String? = null,
    val metadata: Map<String, Any>? = null
)